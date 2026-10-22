package io.github.alfayohannis.eclipse.mcp.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/** The library against a fake plug-in: discovery, typed methods, generic calls and failures. */
class EclipseClientTest {

    @TempDir
    Path config;

    private FakePlugin plugin;

    /** Starts a fake plug-in that announces itself in the test's configuration directory. */
    @BeforeEach
    void startPlugin() throws IOException {
        plugin = new FakePlugin(config);
    }

    /** Stops the fake plug-in. */
    @AfterEach
    void stopPlugin() throws IOException {
        plugin.close();
    }

    /** The client finds the fake through its instance file and token file. */
    @Test
    void discoversTheRunningInstance() {
        EclipseClient client = client();

        assertEquals(plugin.port(), client.baseUrl().getPort());
        assertEquals("eclipse-mcp", client.serverStatus().get("name").getAsString());
    }

    /** GET /api/tools becomes tool descriptions. */
    @Test
    void listsTools() {
        List<ToolInfo> tools = client().tools();

        assertEquals(5, tools.size());
        assertEquals("list_projects", tools.get(0).name());
    }

    /** A typed method returns the structured result. */
    @Test
    void typedMethodReturnsStructuredResult() {
        JsonObject projects = client().listProjects();

        assertEquals("demo", projects.getAsJsonArray("projects").get(0).getAsJsonObject().get("name").getAsString());
    }

    /** Typed methods send only the arguments that are given, so the tools apply their defaults. */
    @Test
    void typedMethodSendsOnlyGivenArguments() {
        client().getProblems("error", null, 5);

        assertEquals("get_problems", plugin.lastTool());
        assertEquals("error", plugin.lastArguments().get("severity").getAsString());
        assertEquals(5, plugin.lastArguments().get("limit").getAsInt());
        assertFalse(plugin.lastArguments().has("project"));
    }

    /** Text-only tools come back as their text. */
    @Test
    void textToolReturnsText() {
        assertEquals("Refreshed /", client().refresh(null));
    }

    /** The screenshot's image content is decoded. */
    @Test
    void screenshotDecodesTheImage() {
        Screenshot screenshot = client().screenshot("shell", null, null, null);

        assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G' }, screenshot.png());
        assertEquals("the shell", screenshot.details().get("captured").getAsString());
    }

    /** A tool error (HTTP 422) becomes a ToolFailedException with the tool's message. */
    @Test
    void toolErrorThrowsWithTheResult() {
        ToolFailedException failure = assertThrows(ToolFailedException.class,
                () -> client().call("fail_tool", new JsonObject()));

        assertEquals(422, failure.status());
        assertEquals("Something went wrong", failure.result().text());
    }

    /** An unknown tool is a client error with the server's message. */
    @Test
    void unknownToolFails() {
        EclipseMcpException failure = assertThrows(EclipseMcpException.class, () -> client().call("nope", null));

        assertTrue(failure.getMessage().contains("Unknown tool"));
    }

    /** A wrong token gives a message about the token. */
    @Test
    void wrongTokenFails() {
        EclipseClient client = EclipseClient
                .connect(ClientSettings.builder().configDirectory(config).token("wrong").build());

        EclipseMcpException failure = assertThrows(EclipseMcpException.class, client::serverStatus);
        assertTrue(failure.getMessage().contains("token"));
    }

    /** A port where nothing listens means Eclipse is not running. */
    @Test
    void refusedConnectionMeansNotRunning() throws IOException {
        int port = plugin.port();
        plugin.close();
        EclipseClient client = EclipseClient.connect(ClientSettings.builder().configDirectory(config).port(port).build());

        assertThrows(EclipseNotRunningException.class, client::serverStatus);
    }

    /** Without instance files the client says that no Eclipse runs. */
    @Test
    void noInstanceMeansNotRunning() throws IOException {
        plugin.close();

        assertThrows(EclipseNotRunningException.class, this::client);
    }

    /** A client that discovers the fake through the test directory. */
    private EclipseClient client() {
        return EclipseClient.connect(ClientSettings.builder().configDirectory(config).build());
    }
}
