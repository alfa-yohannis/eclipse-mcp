package io.github.alfayohannis.eclipse.mcp.client;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The result of a tool call as the plug-in returns it: text and image content,
 * an optional structured object, and an error flag. The same JSON that MCP
 * clients receive.
 */
public final class ToolResult {

    private final JsonObject json;

    /** Wraps the result JSON. */
    public ToolResult(JsonObject json) {
        this.json = json;
    }

    /** Whether the tool reported an error. */
    public boolean isError() {
        return json.has("isError") && json.get("isError").getAsBoolean();
    }

    /** The structured result, or null when the tool returned only content. */
    public JsonObject structured() {
        JsonElement structured = json.get("structuredContent");
        return structured != null && structured.isJsonObject() ? structured.getAsJsonObject() : null;
    }

    /** The text items joined by blank lines. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (JsonObject item : items("text")) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(item.get("text").getAsString());
        }
        return text.toString();
    }

    /** The decoded bytes of each image item, such as the PNG of a screenshot. */
    public List<byte[]> images() {
        List<byte[]> images = new ArrayList<>();
        for (JsonObject item : items("image")) {
            images.add(Base64.getDecoder().decode(item.get("data").getAsString()));
        }
        return images;
    }

    /** The whole result as JSON. */
    public JsonObject json() {
        return json.deepCopy();
    }

    /** The content items of one type. */
    private List<JsonObject> items(String type) {
        List<JsonObject> items = new ArrayList<>();
        JsonElement content = json.get("content");
        if (content == null || !content.isJsonArray()) {
            return items;
        }
        for (JsonElement item : (JsonArray) content) {
            if (item.isJsonObject() && type.equals(item.getAsJsonObject().get("type").getAsString())) {
                items.add(item.getAsJsonObject());
            }
        }
        return items;
    }
}
