package io.github.alfayohannis.eclipse.mcp.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for the Eclipse plug-in's HTTP server, so client tests run
 * without Eclipse. It answers the REST API and /mcp for a few tools, checks
 * the token, and announces itself with an instance file in a test directory.
 */
public final class FakePlugin implements AutoCloseable {

    /** The token the fake accepts. */
    public static final String TOKEN = "test-token";

    private final HttpServer server;
    private final Path configDirectory;
    private String lastTool;
    private JsonObject lastArguments;

    /** Starts the fake on a free port and writes its instance and token files into the directory. */
    public FakePlugin(Path configDirectory) throws IOException {
        this.configDirectory = configDirectory;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        writeFiles();
    }

    /** The port the fake listens on. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** The name of the last tool called through REST or MCP. */
    public String lastTool() {
        return lastTool;
    }

    /** The arguments of the last tool call. */
    public JsonObject lastArguments() {
        return lastArguments;
    }

    /** Stops the server and removes its instance file, as the plug-in does when it stops. */
    @Override
    public void close() throws IOException {
        server.stop(0);
        Files.deleteIfExists(configDirectory.resolve("instances").resolve(port() + ".json"));
    }

    /** Writes the token file and an instance file naming this process, as the plug-in would. */
    private void writeFiles() throws IOException {
        Files.createDirectories(configDirectory.resolve("instances"));
        Files.writeString(configDirectory.resolve("token"), TOKEN + "\n");
        JsonObject instance = new JsonObject();
        instance.addProperty("url", "http://127.0.0.1:" + port());
        instance.addProperty("port", port());
        instance.addProperty("pid", ProcessHandle.current().pid());
        instance.addProperty("workspace", "/tmp/fake-workspace");
        instance.addProperty("started", Instant.now().toString());
        Files.writeString(configDirectory.resolve("instances").resolve(port() + ".json"), instance.toString());
    }

    /** Routes a request after checking the token. */
    private void handle(HttpExchange exchange) throws IOException {
        if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            reply(exchange, 401, error("Missing or wrong bearer token"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if ("/api/status".equals(path)) {
            reply(exchange, 200, status());
        } else if ("/api/tools".equals(path)) {
            JsonObject tools = new JsonObject();
            tools.add("tools", toolList());
            reply(exchange, 200, tools);
        } else if (path.startsWith("/api/tools/")) {
            callRest(exchange, path.substring("/api/tools/".length()), body);
        } else if ("/mcp".equals(path)) {
            answerMcp(exchange, JsonParser.parseString(body).getAsJsonObject());
        } else {
            reply(exchange, 404, error("Not found: " + path));
        }
    }

    /** POST /api/tools/{name}: 404 for unknown tools, 422 for tool errors, else 200. */
    private void callRest(HttpExchange exchange, String tool, String body) throws IOException {
        JsonObject arguments = body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        JsonObject result = run(tool, arguments);
        if (result == null) {
            reply(exchange, 404, error("Unknown tool: " + tool));
            return;
        }
        boolean failed = result.get("isError").getAsBoolean();
        if (failed) {
            result.addProperty("error", result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
        }
        reply(exchange, failed ? 422 : 200, result);
    }

    /** The /mcp endpoint for initialize, tools/list and tools/call; notifications get 202. */
    private void answerMcp(HttpExchange exchange, JsonObject message) throws IOException {
        if (!message.has("id")) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        String method = message.get("method").getAsString();
        JsonObject result = new JsonObject();
        if ("initialize".equals(method)) {
            result.addProperty("instructions", "Fake instructions");
        } else if ("tools/list".equals(method)) {
            result.add("tools", toolList());
        } else if ("tools/call".equals(method)) {
            JsonObject params = message.getAsJsonObject("params");
            result = run(params.get("name").getAsString(), params.getAsJsonObject("arguments"));
        }
        JsonObject reply = new JsonObject();
        reply.addProperty("jsonrpc", "2.0");
        reply.add("id", message.get("id"));
        reply.add("result", result);
        reply(exchange, 200, reply);
    }

    /** The canned result of a tool, recording the call; null for unknown tools. */
    private JsonObject run(String tool, JsonObject arguments) {
        lastTool = tool;
        lastArguments = arguments;
        switch (tool) {
        case "list_projects":
            return projectsResult();
        case "get_problems":
            return result("{}", problems(), false);
        case "refresh":
            return result("Refreshed /", null, false);
        case "screenshot":
            return screenshotResult();
        case "diagram_info":
            return diagramResult(arguments);
        case "fail_tool":
            return result("Something went wrong", null, true);
        default:
            return null;
        }
    }

    /** One open project with two warnings. */
    private static JsonObject projectsResult() {
        JsonObject project = new JsonObject();
        project.addProperty("name", "demo");
        project.addProperty("open", true);
        project.addProperty("errors", 0);
        project.addProperty("warnings", 2);
        JsonArray projects = new JsonArray();
        projects.add(project);
        JsonObject structured = new JsonObject();
        structured.add("projects", projects);
        return result(structured.toString(), structured, false);
    }

    /** A result with a four-byte "PNG" image item, enough to test decoding. */
    private static JsonObject screenshotResult() {
        JsonObject screenshot = result("{}", new JsonObject(), false);
        JsonObject image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("data", Base64.getEncoder().encodeToString(new byte[] { (byte) 0x89, 'P', 'N', 'G' }));
        image.addProperty("mimeType", "image/png");
        screenshot.getAsJsonArray("content").add(image);
        screenshot.getAsJsonObject("structuredContent").addProperty("captured", "the shell");
        return screenshot;
    }

    /**
     * An open diagram with one task and one sequence flow. Like the plug-in, it adds the palette unless
     * the call sets palette to false.
     */
    private static JsonObject diagramResult(JsonObject arguments) {
        JsonObject structured = new JsonObject();
        structured.addProperty("editor", "order");
        structured.addProperty("file", "/demo/order.bpmn");
        structured.addProperty("dirty", false);
        structured.add("shapes", single(task()));
        structured.add("connections", single(sequenceFlow()));
        boolean withPalette = !arguments.has("palette") || arguments.get("palette").getAsBoolean();
        if (withPalette) {
            structured.add("palette", single(taskTool()));
        }
        return result(structured.toString(), structured, false);
    }

    /** The task Task_1 "Check order" at 100,80, 110 by 50. */
    private static JsonObject task() {
        JsonObject bounds = new JsonObject();
        bounds.addProperty("x", 100);
        bounds.addProperty("y", 80);
        bounds.addProperty("width", 110);
        bounds.addProperty("height", 50);
        JsonObject task = new JsonObject();
        task.addProperty("id", "Task_1");
        task.addProperty("type", "Task");
        task.addProperty("name", "Check order");
        task.add("bounds", bounds);
        return task;
    }

    /** The sequence flow SequenceFlow_1 from StartEvent_1 to Task_1. */
    private static JsonObject sequenceFlow() {
        JsonObject flow = new JsonObject();
        flow.addProperty("id", "SequenceFlow_1");
        flow.addProperty("type", "SequenceFlow");
        flow.addProperty("source", "StartEvent_1");
        flow.addProperty("target", "Task_1");
        return flow;
    }

    /** The palette tool Task in the drawer Tasks. */
    private static JsonObject taskTool() {
        JsonObject tool = new JsonObject();
        tool.addProperty("label", "Task");
        tool.addProperty("drawer", "Tasks");
        tool.addProperty("kind", "shape");
        return tool;
    }

    /** A JSON array holding one object. */
    private static JsonArray single(JsonObject element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    /** An empty problem list with counts. */
    private static JsonObject problems() {
        JsonObject problems = new JsonObject();
        problems.addProperty("errors", 0);
        problems.addProperty("warnings", 0);
        problems.add("problems", new JsonArray());
        return problems;
    }

    /** A tool result with one text item and optional structured content. */
    private static JsonObject result(String text, JsonObject structured, boolean isError) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);
        JsonArray content = new JsonArray();
        content.add(item);
        JsonObject result = new JsonObject();
        result.add("content", content);
        if (structured != null) {
            result.add("structuredContent", structured);
        }
        result.addProperty("isError", isError);
        return result;
    }

    /** The descriptions the fake offers. */
    private static JsonArray toolList() {
        JsonArray tools = new JsonArray();
        for (String name : new String[] { "list_projects", "get_problems", "refresh", "screenshot", "fail_tool" }) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", name);
            tool.addProperty("description", "Fake " + name);
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            tool.add("inputSchema", schema);
            tools.add(tool);
        }
        return tools;
    }

    /** The status document. */
    private JsonObject status() {
        JsonObject status = new JsonObject();
        status.addProperty("name", "eclipse-mcp");
        status.addProperty("version", "0.1.0.test");
        status.addProperty("url", "http://127.0.0.1:" + port());
        status.addProperty("tools", toolList().size());
        return status;
    }

    /** {"error": message}. */
    private static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return json;
    }

    /** Sends a JSON reply. */
    private static void reply(HttpExchange exchange, int status, JsonElement body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
