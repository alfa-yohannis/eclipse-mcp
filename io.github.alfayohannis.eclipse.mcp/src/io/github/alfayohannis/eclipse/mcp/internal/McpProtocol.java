package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;
import java.util.NoSuchElementException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolRegistry;

/**
 * The JSON-RPC side of MCP: initialize, ping, tools/list and tools/call. It
 * keeps no session state, so every request stands on its own and a restarted
 * Eclipse needs no new handshake.
 */
final class McpProtocol {

    /** Supported protocol versions, newest first; initialize answers with the client's if it is listed. */
    static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    /** Advice for the model, sent with initialize. */
    static final String INSTRUCTIONS = "These tools control the running Eclipse IDE: workspace projects, problems,"
            + " builds, commands, menus, dialogs, editors and launches. Start with eclipse_status."
            + " Tools that can open a modal dialog (run_command, click_menu, press_button, open_file, launch,"
            + " select_tree_item with open, mouse_click, press_keys) return after wait_ms while the dialog stays"
            + " open and list the open shells; read the dialog with list_shells (widgets=true), fill it with"
            + " set_text or select_tree_item and close it with press_button. When shell is omitted, tools act on"
            + " the innermost modal dialog, else the active window. Mouse coordinates are relative to the top-left"
            + " corner of the shell or widget, the same origin as a screenshot of it at scale 1. Widget indexes"
            + " come from list_shells with widgets=true and stay valid until the dialog changes.";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final ToolRegistry registry;

    /** A protocol handler over the tools of this registry. */
    McpProtocol(ToolRegistry registry) {
        this.registry = registry;
    }

    /** Answers one HTTP body, a message or a batch; returns null when nothing needs an answer. */
    String handle(String body) {
        JsonElement message;
        try {
            message = JsonParser.parseString(body);
        } catch (JsonParseException e) {
            return GSON.toJson(error(JsonNull.INSTANCE, -32700, "Parse error: " + e.getMessage()));
        }
        JsonElement reply = message.isJsonArray() ? handleBatch(message.getAsJsonArray()) : handleMessage(message);
        return reply == null ? null : GSON.toJson(reply);
    }

    /** Answers each message of a batch; notifications add nothing, so an all-notification batch needs no reply. */
    private JsonElement handleBatch(JsonArray batch) {
        if (batch.size() == 0) {
            return error(JsonNull.INSTANCE, -32600, "Invalid Request: empty batch");
        }
        JsonArray replies = new JsonArray();
        for (JsonElement item : batch) {
            JsonObject reply = handleMessage(item);
            if (reply != null) {
                replies.add(reply);
            }
        }
        return replies.size() == 0 ? null : replies;
    }

    /** Answers a request; notifications and responses from the client get no answer. */
    private JsonObject handleMessage(JsonElement element) {
        if (!element.isJsonObject()) {
            return error(JsonNull.INSTANCE, -32600, "Invalid Request: not an object");
        }
        JsonObject message = element.getAsJsonObject();
        JsonElement method = message.get("method");
        if (method == null || !method.isJsonPrimitive()) {
            boolean isResponse = message.has("result") || message.has("error");
            return isResponse ? null : error(idOf(message), -32600, "Invalid Request: no method");
        }
        if (!message.has("id")) {
            return null;
        }
        return answer(idOf(message), method.getAsString(), paramsOf(message));
    }

    /** Runs a request and wraps its result or error with the request's id. */
    private JsonObject answer(JsonElement id, String method, JsonObject params) {
        try {
            return success(id, call(method, params));
        } catch (RpcException e) {
            return error(id, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            Log.error("The MCP request " + method + " failed", e);
            return error(id, -32603, "Internal error: " + e);
        }
    }

    /** Dispatches a method to its handler. */
    private JsonElement call(String method, JsonObject params) {
        switch (method) {
        case "initialize":
            return initialize(params);
        case "ping":
            return new JsonObject();
        case "tools/list":
            return listTools();
        case "tools/call":
            return callTool(params);
        default:
            throw new RpcException(-32601, "Method not found: " + method);
        }
    }

    /** Agrees on a protocol version and announces the tools capability and the instructions. */
    private static JsonObject initialize(JsonObject params) {
        JsonElement requested = params.get("protocolVersion");
        boolean supported = requested != null && requested.isJsonPrimitive()
                && PROTOCOL_VERSIONS.contains(requested.getAsString());
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", supported ? requested.getAsString() : PROTOCOL_VERSIONS.get(0));
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", tools);
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "eclipse-mcp");
        serverInfo.addProperty("title", "Eclipse MCP Server");
        serverInfo.addProperty("version", McpHttpServer.version());
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions", INSTRUCTIONS);
        return result;
    }

    /** The descriptions of all tools, read from the registry on each request so they never drift. */
    private JsonObject listTools() {
        JsonArray tools = new JsonArray();
        for (Tool tool : registry.tools()) {
            tools.add(tool.describe());
        }
        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return result;
    }

    /** Calls a tool; an unknown name is a protocol error, a failing tool an error result. */
    private JsonObject callTool(JsonObject params) {
        JsonElement name = params.get("name");
        if (name == null || !name.isJsonPrimitive()) {
            throw new RpcException(-32602, "Invalid params: tools/call needs a tool name");
        }
        JsonElement arguments = params.get("arguments");
        if (arguments != null && !arguments.isJsonNull() && !arguments.isJsonObject()) {
            throw new RpcException(-32602, "Invalid params: arguments must be an object");
        }
        JsonObject argumentObject = arguments != null && arguments.isJsonObject() ? arguments.getAsJsonObject()
                : new JsonObject();
        try {
            return registry.call(name.getAsString(), argumentObject).toJson();
        } catch (NoSuchElementException e) {
            throw new RpcException(-32602, e.getMessage());
        }
    }

    /** The message's params object, or an empty one. */
    private static JsonObject paramsOf(JsonObject message) {
        JsonElement params = message.get("params");
        return params != null && params.isJsonObject() ? params.getAsJsonObject() : new JsonObject();
    }

    /** The message's id, or JSON null when there is none. */
    private static JsonElement idOf(JsonObject message) {
        JsonElement id = message.get("id");
        return id == null ? JsonNull.INSTANCE : id;
    }

    /** A JSON-RPC success response. */
    private static JsonObject success(JsonElement id, JsonElement result) {
        JsonObject reply = new JsonObject();
        reply.addProperty("jsonrpc", "2.0");
        reply.add("id", id);
        reply.add("result", result);
        return reply;
    }

    /** A JSON-RPC error response. */
    private static JsonObject error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject reply = new JsonObject();
        reply.addProperty("jsonrpc", "2.0");
        reply.add("id", id);
        reply.add("error", error);
        return reply;
    }
}
