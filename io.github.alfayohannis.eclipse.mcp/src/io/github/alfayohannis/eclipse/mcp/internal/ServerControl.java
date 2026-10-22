package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.ui.PlatformUI;

import io.github.alfayohannis.eclipse.mcp.api.ToolRegistry;

/**
 * Owns the one MCP server of this Eclipse instance and tells listeners, such as
 * the MCP menu handlers, when it starts or stops.
 */
public final class ServerControl {

    private static final List<Runnable> STATE_LISTENERS = new CopyOnWriteArrayList<>();
    private static McpHttpServer server;

    /** Static only: there is one server per Eclipse instance. */
    private ServerControl() {
    }

    /** Starts the server unless it is running, on the configured port or the next free one. */
    static synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        ToolRegistry registry = ToolRegistry.getDefault();
        McpHttpServer started = new McpHttpServer(new RequestRouter(new McpProtocol(registry), new RestApi(registry),
                new AccessPolicy(TokenFile.token())));
        started.start(Config.port(), Config.portFixed());
        server = started;
        announceStateChange();
    }

    /** Starts the server and logs a failure instead of throwing it, for start-up without a user to tell. */
    static void startQuietly() {
        try {
            start();
        } catch (IOException | RuntimeException e) {
            Log.error("The MCP server could not start", e);
        }
    }

    /** Stops the server; requests in progress still get their answer. */
    static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
        Log.info("Eclipse MCP server stopped");
        announceStateChange();
    }

    /** Whether the server is running. */
    static synchronized boolean isRunning() {
        return server != null;
    }

    /** The running server, or null when it is stopped. */
    static synchronized McpHttpServer server() {
        return server;
    }

    /** Registers a listener that runs on the UI thread after each start and stop. */
    static void addStateListener(Runnable listener) {
        STATE_LISTENERS.add(listener);
    }

    /** Removes a listener added with {@link #addStateListener(Runnable)}. */
    static void removeStateListener(Runnable listener) {
        STATE_LISTENERS.remove(listener);
    }

    /** Schedules the listeners on the UI thread, where handlers may update their enablement. */
    private static void announceStateChange() {
        if (PlatformUI.isWorkbenchRunning()) {
            PlatformUI.getWorkbench().getDisplay().asyncExec(ServerControl::runStateListeners);
        }
    }

    /** Runs every state listener; called on the UI thread. */
    private static void runStateListeners() {
        for (Runnable listener : STATE_LISTENERS) {
            listener.run();
        }
    }
}
