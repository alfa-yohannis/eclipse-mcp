package io.github.alfayohannis.eclipse.mcp.client.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Builds JSON-RPC 2.0 messages. */
final class JsonRpc {

    /** Not instantiable: the class only has static members. */
    private JsonRpc() {
    }

    /** A success response. */
    static JsonObject success(JsonElement id, JsonElement result) {
        JsonObject reply = envelope(id);
        reply.add("result", result);
        return reply;
    }

    /** An error response. */
    static JsonObject error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject reply = envelope(id);
        reply.add("error", error);
        return reply;
    }

    /** A notification, which has a method but no id. */
    static JsonObject notification(String method) {
        JsonObject message = new JsonObject();
        message.addProperty("jsonrpc", "2.0");
        message.addProperty("method", method);
        return message;
    }

    /** A request with an id and parameters. */
    static JsonObject request(String id, String method, JsonObject params) {
        JsonObject message = notification(method);
        message.addProperty("id", id);
        message.add("params", params);
        return message;
    }

    /** The jsonrpc and id members shared by responses. */
    private static JsonObject envelope(JsonElement id) {
        JsonObject reply = new JsonObject();
        reply.addProperty("jsonrpc", "2.0");
        reply.add("id", id == null ? JsonNull.INSTANCE : id);
        return reply;
    }
}
