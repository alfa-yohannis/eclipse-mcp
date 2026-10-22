package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.ResourcesPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.google.gson.JsonObject;

/**
 * A small HTTP/1.1 server on the loopback interface. It owns the listening
 * socket and the connections; {@link RequestRouter} decides what each request
 * gets. Only local programs can connect, and the router requires the token.
 */
final class McpHttpServer {

    /** Idle keep-alive connections close after this long. */
    private static final int IDLE_TIMEOUT_MS = 120_000;

    private final RequestRouter router;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    /** Connections whose request is being answered, which a stop lets finish. */
    private final Set<Socket> busy = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadCount = new AtomicInteger();
    private final Instant started = Instant.now();
    private volatile boolean stopping;
    private ServerSocket serverSocket;
    private ExecutorService workers;
    private InstanceFile instanceFile;
    private int port;

    /** A server that hands requests to the router; it listens after {@link #start(int, boolean)}. */
    McpHttpServer(RequestRouter router) {
        this.router = router;
        router.setServerInfo(this::describe);
    }

    /** Binds the port, or the first free one above it unless the port is fixed, and starts serving. */
    void start(int firstPort, boolean portFixed) throws IOException {
        serverSocket = bind(firstPort, portFixed ? 1 : Config.PORT_TRIES);
        port = serverSocket.getLocalPort();
        workers = Executors.newCachedThreadPool(this::newWorkerThread);
        Thread acceptor = new Thread(this::acceptConnections, "Eclipse MCP acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        instanceFile = new InstanceFile(Config.instancesDirectory(), port);
        instanceFile.write(describe());
        Log.info("Eclipse MCP server listening on " + baseUrl() + "/mcp and " + baseUrl() + "/api");
    }

    /** Stops accepting connections and closes the idle ones; requests in progress still get their answer. */
    void stop() {
        stopping = true;
        closeQuietly(serverSocket);
        for (Socket socket : connections) {
            if (!busy.contains(socket)) {
                closeQuietly(socket);
            }
        }
        workers.shutdown();
        instanceFile.delete();
    }

    /** The port the server listens on. */
    int port() {
        return port;
    }

    /** The address clients use, without a path. */
    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** What the instance file and GET /api/status say about this server. */
    JsonObject describe() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "eclipse-mcp");
        json.addProperty("version", version());
        json.addProperty("url", baseUrl());
        json.addProperty("mcp", baseUrl() + "/mcp");
        json.addProperty("api", baseUrl() + "/api");
        json.addProperty("port", port);
        json.addProperty("pid", ProcessHandle.current().pid());
        json.addProperty("workspace", ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
        json.addProperty("started", started.toString());
        return json;
    }

    /** The version of this bundle, which clients see in serverInfo. */
    static String version() {
        Bundle bundle = FrameworkUtil.getBundle(McpHttpServer.class);
        return bundle == null ? "0.1.0" : bundle.getVersion().toString();
    }

    /** Binds the first free port of the range on 127.0.0.1. */
    private static ServerSocket bind(int firstPort, int tries) throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        BindException lastFailure = null;
        for (int candidatePort = firstPort; candidatePort < firstPort + tries; candidatePort++) {
            ServerSocket candidate = new ServerSocket();
            try {
                candidate.bind(new InetSocketAddress(loopback, candidatePort));
                return candidate;
            } catch (BindException e) {
                candidate.close();
                lastFailure = e;
            }
        }
        throw new IOException("No free port in " + firstPort + "-" + (firstPort + tries - 1), lastFailure);
    }

    /** A daemon thread for one connection, so open connections never keep Eclipse from exiting. */
    private Thread newWorkerThread(Runnable task) {
        Thread thread = new Thread(task, "Eclipse MCP " + threadCount.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    /** Accepts connections until the server socket closes. */
    private void acceptConnections() {
        while (!serverSocket.isClosed()) {
            try {
                dispatch(serverSocket.accept());
            } catch (IOException e) {
                logUnlessStopping(e);
            }
        }
    }

    /** Hands a new connection to a worker thread. */
    private void dispatch(Socket socket) throws IOException {
        connections.add(socket);
        try {
            workers.execute(() -> serve(socket));
        } catch (RejectedExecutionException e) {
            connections.remove(socket);
            socket.close();
        }
    }

    /** Logs an accept failure, which is expected while the socket closes. */
    private void logUnlessStopping(IOException failure) {
        if (!serverSocket.isClosed()) {
            Log.error("The MCP server could not accept a connection", failure);
        }
    }

    /** Answers the requests of one connection until it closes, idles out or the server stops. */
    private void serve(Socket socket) {
        try (socket) {
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            boolean keepAlive = true;
            while (keepAlive) {
                keepAlive = answerNext(socket, in, out);
            }
        } catch (IOException e) {
            // the client went away or stayed idle too long
        } finally {
            connections.remove(socket);
        }
    }

    /** Reads and answers one request; returns whether the connection stays open for another. */
    private boolean answerNext(Socket socket, InputStream in, OutputStream out) throws IOException {
        HttpRequest request;
        try {
            request = HttpRequest.read(in, out);
        } catch (BadRequestException e) {
            HttpResponse.json(e.status(), RestApi.errorJson(e.getMessage())).write(out, false);
            return false;
        }
        if (request == null) {
            return false;
        }
        busy.add(socket);
        try {
            HttpResponse response = router.route(request);
            boolean keepAlive = request.keepAlive() && !stopping;
            response.write(out, keepAlive);
            return keepAlive;
        } finally {
            busy.remove(socket);
        }
    }

    /** Closes a socket, ignoring that it may already be closed. */
    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            // already closed
        }
    }
}
