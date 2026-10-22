package io.github.alfayohannis.eclipse.mcp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A tool as the plug-in describes it: name, title, description, input schema and read-only hint. */
public final class ToolInfo {

    private final JsonObject json;

    /** Wraps one entry of GET /api/tools. */
    public ToolInfo(JsonObject json) {
        this.json = json;
    }

    /** The name to call it by. */
    public String name() {
        return json.get("name").getAsString();
    }

    /** The human-readable title, or the name. */
    public String title() {
        JsonElement title = json.get("title");
        return title == null ? name() : title.getAsString();
    }

    /** What the tool does. */
    public String description() {
        JsonElement description = json.get("description");
        return description == null ? "" : description.getAsString();
    }

    /** The JSON Schema of the arguments. */
    public JsonObject inputSchema() {
        JsonElement schema = json.get("inputSchema");
        return schema == null ? new JsonObject() : schema.getAsJsonObject().deepCopy();
    }

    /** Whether the tool only reads state. */
    public boolean readOnly() {
        JsonElement annotations = json.get("annotations");
        JsonElement hint = annotations == null ? null : annotations.getAsJsonObject().get("readOnlyHint");
        return hint != null && hint.getAsBoolean();
    }

    /** The description as MCP tools/list returns it. */
    public JsonObject json() {
        return json.deepCopy();
    }
}
