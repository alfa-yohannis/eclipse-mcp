package io.github.alfayohannis.eclipse.mcp.client.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.alfayohannis.eclipse.mcp.client.ClientSettings;
import io.github.alfayohannis.eclipse.mcp.client.EclipseMcpException;
import io.github.alfayohannis.eclipse.mcp.client.EclipseNotRunningException;

/**
 * An MCP server on standard input and output that forwards to the plug-in.
 * Clients that only start commands (stdio) can use the plug-in this way, and
 * the token stays out of their configuration. The tool list always comes from
 * the plug-in at request time; when Eclipse is not running, the server still
 * answers, with the cached tools and calls that explain how to start Eclipse.
 */
public final class StdioServer {

    /** Supported protocol versions, newest first, as the plug-in supports them. */
    static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private static final String OFFLINE_INSTRUCTIONS = "These tools control a running Eclipse IDE through its MCP"
            + " plug-in. Eclipse was not reachable when this session started; start Eclipse with the plug-in (or"
            + " MCP > Start Server in Eclipse) and the tools will work. Start with eclipse_status.";

    private final BufferedReader input;
    private final MessageWriter writer;
    private final PluginLink plugin;
    private final ToolListCache cache;
    private final EclipseWatcher watcher;

    /** A server between the streams and the Eclipse that the settings find. */
    public StdioServer(InputStream in, PrintStream out, ClientSettings settings) {
        this.input = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new MessageWriter(out);
        this.plugin = new PluginLink(settings);
        this.cache = new ToolListCache(settings.configDirectory());
        this.watcher = new EclipseWatcher(plugin, writer, cache);
    }

    /** Serves until standard input ends. */
    public void run() throws IOException {
        try {
            for (String line = input.readLine(); line != null; line = input.readLine()) {
                handleLine(line);
            }
        } finally {
            watcher.stop();
        }
    }

    /** Answers one line: a message or a batch. */
    void handleLine(String line) {
        if (line.isBlank()) {
            return;
        }
        JsonElement message;
        try {
            message = JsonParser.parseString(line);
        } catch (JsonParseException e) {
            writer.write(JsonRpc.error(null, -32700, "Parse error: " + e.getMessage()));
            return;
        }
        JsonElement reply = message.isJsonArray() ? handleBatch(message.getAsJsonArray()) : handle(message);
        if (reply != null) {
            writer.write(reply);
        }
    }

    /** Answers each message of a batch; notifications add nothing. */
    private JsonElement handleBatch(JsonArray batch) {
        JsonArray replies = new JsonArray();
        for (JsonElement message : batch) {
            JsonObject reply = handle(message);
            if (reply != null) {
                replies.add(reply);
            }
        }
        return replies.size() == 0 ? null : replies;
    }

    /** Answers one message; notifications and the client's responses get no answer. */
    private JsonObject handle(JsonElement element) {
        if (!element.isJsonObject() || !element.getAsJsonObject().has("method")) {
            return element.isJsonObject() ? null : JsonRpc.error(null, -32600, "Invalid Request");
        }
        JsonObject message = element.getAsJsonObject();
        if (!message.has("id")) {
            return null;
        }
        JsonElement id = message.get("id");
        try {
            return dispatch(id, message.get("method").getAsString(), message);
        } catch (EclipseMcpException e) {
            return JsonRpc.error(id, -32603, e.getMessage());
        }
    }

    /** Answers initialize and ping here; tools/list and tools/call with fallbacks; the rest through the plug-in. */
    private JsonObject dispatch(JsonElement id, String method, JsonObject message) {
        switch (method) {
        case "initialize":
            return JsonRpc.success(id, initialize(message));
        case "ping":
            return JsonRpc.success(id, new JsonObject());
        case "tools/list":
            return listTools(id, message);
        case "tools/call":
            return callTool(id, message);
        default:
            return asObject(plugin.forward(message), id);
        }
    }

    /**
     * Agrees on a protocol version and announces the tools, with list_changed
     * so the tool list can follow Eclipse. The instructions come from the
     * plug-in when it is reachable.
     */
    private JsonObject initialize(JsonObject message) {
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
                ? message.getAsJsonObject("params") : new JsonObject();
        JsonElement requested = params.get("protocolVersion");
        boolean supported = requested != null && requested.isJsonPrimitive()
                && PROTOCOL_VERSIONS.contains(requested.getAsString());
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", supported ? requested.getAsString() : PROTOCOL_VERSIONS.get(0));
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", true);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "eclipse-mcp");
        serverInfo.addProperty("title", "Eclipse MCP Server (stdio)");
        serverInfo.addProperty("version", "0.1.0");
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions", pluginInstructions(message));
        return result;
    }

    /** The plug-in's instructions, or the offline ones when Eclipse is not reachable. */
    private String pluginInstructions(JsonObject message) {
        try {
            JsonElement reply = plugin.forward(message);
            JsonObject result = reply.getAsJsonObject().getAsJsonObject("result");
            return result.get("instructions").getAsString();
        } catch (EclipseMcpException | IllegalStateException | ClassCastException e) {
            return OFFLINE_INSTRUCTIONS;
        }
    }

    /** The plug-in's tools, remembered on disk; without Eclipse the remembered ones, and a watch for Eclipse. */
    private JsonObject listTools(JsonElement id, JsonObject message) {
        try {
            JsonElement reply = plugin.forward(message);
            JsonArray tools = EclipseWatcher.toolsOf(reply);
            if (tools != null) {
                cache.save(tools);
            }
            return asObject(reply, id);
        } catch (EclipseNotRunningException e) {
            JsonArray tools = cache.load();
            watcher.watch(tools);
            JsonObject result = new JsonObject();
            result.add("tools", tools);
            return JsonRpc.success(id, result);
        }
    }

    /** Forwards the call; without Eclipse the result is an error that says how to start it. */
    private JsonObject callTool(JsonElement id, JsonObject message) {
        try {
            return asObject(plugin.forward(message), id);
        } catch (EclipseNotRunningException e) {
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", "Eclipse is not running with the MCP plug-in, so the tool cannot run. "
                    + e.getMessage());
            JsonArray content = new JsonArray();
            content.add(text);
            JsonObject result = new JsonObject();
            result.add("content", content);
            result.addProperty("isError", true);
            return JsonRpc.success(id, result);
        }
    }

    /** The plug-in's reply as an object; an empty or odd reply becomes an error for the request. */
    private static JsonObject asObject(JsonElement reply, JsonElement id) {
        if (reply != null && reply.isJsonObject()) {
            return reply.getAsJsonObject();
        }
        return JsonRpc.error(id, -32603, "Eclipse gave no answer to the request");
    }
}
