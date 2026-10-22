package io.github.alfayohannis.eclipse.mcp.client.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.alfayohannis.eclipse.mcp.client.ClientSettings;
import io.github.alfayohannis.eclipse.mcp.client.FakePlugin;

/** The stdio MCP server, with a fake plug-in and without any Eclipse. */
class StdioServerTest {

    private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
            + "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}";
    private static final String INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final String LIST = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
    private static final String CALL = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":"
            + "{\"name\":\"list_projects\",\"arguments\":{}}}";

    @TempDir
    Path config;

    /** With Eclipse running, the server forwards and keeps the client's protocol version. */
    @Test
    void forwardsToThePlugin() throws IOException {
        try (FakePlugin plugin = new FakePlugin(config)) {
            List<JsonObject> replies = serve(INITIALIZE, INITIALIZED, LIST, CALL);

            assertEquals(3, replies.size());
            JsonObject initialize = replies.get(0).getAsJsonObject("result");
            assertEquals("2025-03-26", initialize.get("protocolVersion").getAsString());
            assertEquals("Fake instructions", initialize.get("instructions").getAsString());
            assertEquals(5, replies.get(1).getAsJsonObject("result").getAsJsonArray("tools").size());
            assertEquals("list_projects", plugin.lastTool());
            assertTrue(replies.get(2).getAsJsonObject("result").has("structuredContent"));
        }
    }

    /** Without Eclipse, initialize still works, tools/list offers a placeholder, and calls explain the problem. */
    @Test
    void answersWithoutEclipse() {
        List<JsonObject> replies = serve(INITIALIZE, LIST, CALL);

        assertEquals(3, replies.size());
        assertTrue(replies.get(0).getAsJsonObject("result").get("instructions").getAsString().contains("not reachable"));
        assertEquals("eclipse_status", replies.get(1).getAsJsonObject("result").getAsJsonArray("tools").get(0)
                .getAsJsonObject().get("name").getAsString());
        JsonObject call = replies.get(2).getAsJsonObject("result");
        assertTrue(call.get("isError").getAsBoolean());
        assertTrue(call.toString().contains("not running"));
    }

    /** A tool list seen once is offered again when Eclipse is gone. */
    @Test
    void remembersTheToolList() throws IOException {
        try (FakePlugin plugin = new FakePlugin(config)) {
            serve(LIST);
        }
        List<JsonObject> replies = serve(LIST);

        assertEquals(5, replies.get(0).getAsJsonObject("result").getAsJsonArray("tools").size());
    }

    /** Feeds the lines to a server and returns the reply lines as JSON. */
    private List<JsonObject> serve(String... lines) {
        String input = String.join("\n", lines) + "\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ClientSettings settings = ClientSettings.builder().configDirectory(config).token(FakePlugin.TOKEN).build();
        StdioServer server = new StdioServer(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8), settings);
        try {
            server.run();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        List<JsonObject> replies = new ArrayList<>();
        for (String line : output.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                replies.add(JsonParser.parseString(line).getAsJsonObject());
            }
        }
        return replies;
    }
}
