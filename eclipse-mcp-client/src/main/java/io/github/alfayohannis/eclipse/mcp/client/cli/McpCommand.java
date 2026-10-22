package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.client.mcp.StdioServer;

/** The mcp subcommand, which turns the CLI into an MCP server on standard input and output. */
final class McpCommand {

    /** Not instantiable: the class only has static members. */
    private McpCommand() {
    }

    /** The mcp subcommand, for the command catalog. */
    static List<CommandSpec> all() {
        return List.of(new CommandSpec("mcp", "", "MCP server over stdio that forwards to the plug-in, for AI assistants",
                McpCommand::serve));
    }

    /** Serves MCP until standard input ends; nothing else may write to standard output meanwhile. */
    private static int serve(CommandLine line, CliContext context) throws java.io.IOException {
        new StdioServer(context.in(), context.out(), context.settings()).run();
        return 0;
    }
}
