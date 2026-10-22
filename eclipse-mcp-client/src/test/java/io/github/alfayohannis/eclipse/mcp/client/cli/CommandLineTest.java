package io.github.alfayohannis.eclipse.mcp.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Parsing of subcommand arguments. */
class CommandLineTest {

    /** Positional values, options with values and flags are told apart. */
    @Test
    void separatesPositionalOptionsAndFlags() {
        CommandLine line = new CommandLine(List.of("Task", "10", "--name", "Check order", "20", "--open"));

        assertEquals("Task", line.positional(0));
        assertEquals(10, line.requiredInt(1, "x"));
        assertEquals(20, line.requiredInt(2, "y"));
        assertEquals("Check order", line.option("name"));
        assertTrue(line.flag("open"));
        assertNull(line.positional(3));
    }

    /** A missing positional value is a usage error naming it. */
    @Test
    void missingValueIsUsageError() {
        UsageException failure = assertThrows(UsageException.class, () -> new CommandLine(List.of()).required(0, "the path"));

        assertEquals("Missing the path", failure.getMessage());
    }

    /** A number option that is not a number is a usage error. */
    @Test
    void badNumberIsUsageError() {
        CommandLine line = new CommandLine(List.of("--limit", "many"));

        assertThrows(UsageException.class, () -> line.intOption("limit"));
    }

    /** key=value words keep JSON types; other values stay strings. */
    @Test
    void parsesKeyValueArguments() {
        var arguments = ToolCalls.parseArguments(List.of("severity=error", "limit=5", "open=true", "path=/a b"));

        assertEquals("error", arguments.get("severity").getAsString());
        assertEquals(5, arguments.get("limit").getAsInt());
        assertTrue(arguments.get("open").getAsBoolean());
        assertEquals("/a b", arguments.get("path").getAsString());
    }
}
