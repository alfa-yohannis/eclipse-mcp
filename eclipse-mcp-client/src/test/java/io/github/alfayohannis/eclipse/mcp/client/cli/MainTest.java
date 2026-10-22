package io.github.alfayohannis.eclipse.mcp.client.cli;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.alfayohannis.eclipse.mcp.client.FakePlugin;

/** The command line against a fake plug-in: output and exit codes. */
class MainTest {

    @TempDir
    Path config;

    private FakePlugin plugin;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    /** Starts a fake plug-in in the test's configuration directory. */
    @BeforeEach
    void startPlugin() throws IOException {
        plugin = new FakePlugin(config);
    }

    /** Stops the fake plug-in. */
    @AfterEach
    void stopPlugin() throws IOException {
        plugin.close();
    }

    /** projects prints one readable line per project. */
    @Test
    void projectsPrintsReadableLines() {
        assertEquals(0, run("projects"));
        assertTrue(out().startsWith("demo  (0 errors, 2 warnings)"), out());
    }

    /** --json prints the plug-in's result JSON. */
    @Test
    void jsonOptionPrintsResultJson() {
        assertEquals(0, run("--json", "call", "list_projects"));
        assertTrue(out().contains("\"structuredContent\""), out());
    }

    /** call passes key=value arguments with their JSON types. */
    @Test
    void callPassesTypedArguments() {
        assertEquals(0, run("call", "get_problems", "severity=error", "limit=3"));
        assertEquals(3, plugin.lastArguments().get("limit").getAsInt());
    }

    /** A tool error prints the tool's message and exits with 1. */
    @Test
    void toolErrorExitsWithOne() {
        assertEquals(1, run("call", "fail_tool"));
        assertTrue(err().contains("Something went wrong"), err());
    }

    /** An unknown command exits with 2. */
    @Test
    void unknownCommandExitsWithTwo() {
        assertEquals(Main.USAGE, run("frobnicate"));
    }

    /** No running Eclipse exits with 3. */
    @Test
    void notRunningExitsWithThree() throws IOException {
        plugin.close();
        assertEquals(Main.NOT_RUNNING, run("projects"));
    }

    /** help lists the commands and exits with 0. */
    @Test
    void helpListsCommands() {
        assertEquals(0, run("help"));
        assertTrue(out().contains("diagram-create"), out());
    }

    /** Runs the CLI with the test's configuration directory. */
    private int run(String... args) {
        List<String> arguments = new ArrayList<>(List.of("--config", config.toString()));
        arguments.addAll(List.of(args));
        PrintStream stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        return new Main(new ByteArrayInputStream(new byte[0]), stdout, stderr).run(arguments);
    }

    /** What the command printed. */
    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    /** What the command printed as errors. */
    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
