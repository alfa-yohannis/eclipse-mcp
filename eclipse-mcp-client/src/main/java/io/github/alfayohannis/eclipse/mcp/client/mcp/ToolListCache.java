package io.github.alfayohannis.eclipse.mcp.client.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The last tool list the plug-in returned, kept on disk. AI clients read the
 * tool list once at start-up, so when Eclipse is not running yet the proxy
 * still offers the tools, and their calls explain that Eclipse must run.
 */
final class ToolListCache {

    private final Path file;

    /** A cache in the configuration directory. */
    ToolListCache(Path configDirectory) {
        this.file = configDirectory.resolve("tools-cache.json");
    }

    /** Saves the tools; a failure only means no cache next time. */
    void save(JsonArray tools) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, tools.toString());
        } catch (IOException e) {
            // the cache is a convenience
        }
    }

    /** The cached tools, or a single placeholder tool when there is no cache. */
    JsonArray load() {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return placeholder();
        }
    }

    /** One tool whose description says that Eclipse is not running; calling it says the same. */
    private static JsonArray placeholder() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        JsonObject tool = new JsonObject();
        tool.addProperty("name", "eclipse_status");
        tool.addProperty("description", "Describes the running Eclipse. Eclipse with the MCP plug-in was not running"
                + " when this session started; once it runs, the full tool list appears.");
        tool.add("inputSchema", schema);
        JsonArray tools = new JsonArray();
        tools.add(tool);
        return tools;
    }
}
