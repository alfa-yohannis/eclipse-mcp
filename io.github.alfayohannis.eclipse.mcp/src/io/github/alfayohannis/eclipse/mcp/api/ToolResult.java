package io.github.alfayohannis.eclipse.mcp.api;

import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What a tool call returns: text and image content as MCP defines it, an
 * optional structured JSON object, and an error flag. MCP clients and the REST
 * API receive the same JSON from {@link #toJson()}.
 */
public final class ToolResult {

    /** Pretty printing keeps the text copy of structured results readable for models. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private final JsonArray content = new JsonArray();
    private JsonObject structured;
    private boolean error;
    private boolean internalError;

    /** Use the factory methods. */
    private ToolResult() {
    }

    /** A plain text result. */
    public static ToolResult text(String text) {
        return new ToolResult().addText(text);
    }

    /**
     * A structured result. Its pretty-printed JSON is also the text content,
     * since many MCP clients only read text.
     */
    public static ToolResult json(JsonObject value) {
        ToolResult result = new ToolResult();
        result.structured = value.deepCopy();
        return result.addText(PRETTY.toJson(value));
    }

    /** An expected failure, such as a wrong argument or a dialog that is not open. */
    public static ToolResult error(String message) {
        ToolResult result = text(message);
        result.error = true;
        return result;
    }

    /** An unexpected failure inside a tool; the REST API answers it with HTTP 500 instead of 422. */
    public static ToolResult internalError(String message) {
        ToolResult result = error(message);
        result.internalError = true;
        return result;
    }

    /** Appends a text item. */
    public ToolResult addText(String text) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);
        content.add(item);
        return this;
    }

    /** Appends an image item, base64-encoded as MCP requires. */
    public ToolResult addImage(byte[] data, String mimeType) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "image");
        item.addProperty("data", Base64.getEncoder().encodeToString(data));
        item.addProperty("mimeType", mimeType);
        content.add(item);
        return this;
    }

    /** Whether the call failed. */
    public boolean isError() {
        return error;
    }

    /** Whether the call failed because of a bug or an unexpected condition rather than the caller's input. */
    public boolean isInternalError() {
        return internalError;
    }

    /** A copy of the structured result, or null when the tool returned only content. */
    public JsonObject structuredContent() {
        return structured == null ? null : structured.deepCopy();
    }

    /** A copy of the content items. */
    public JsonArray content() {
        return content.deepCopy();
    }

    /** The text items joined by blank lines, for logs and error messages. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (JsonElement item : content) {
            appendTextItem(text, item.getAsJsonObject());
        }
        return text.toString();
    }

    /** The MCP CallToolResult: content, structuredContent when present, and isError. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("content", content.deepCopy());
        if (structured != null) {
            json.add("structuredContent", structured.deepCopy());
        }
        json.addProperty("isError", error);
        return json;
    }

    /** Appends one content item to the text when it is a text item. */
    private static void appendTextItem(StringBuilder text, JsonObject item) {
        if (!"text".equals(item.get("type").getAsString())) {
            return;
        }
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append(item.get("text").getAsString());
    }
}
