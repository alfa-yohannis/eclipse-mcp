package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.client.Arguments;

/** Subcommands for launch configurations. */
final class LaunchCommands {

    /** Not instantiable: the class only has static members. */
    private LaunchCommands() {
    }

    /** The subcommands this class defines, for the command catalog. */
    static List<CommandSpec> all() {
        return List.of(
                new CommandSpec("launches", "", "launch configurations and running launches", LaunchCommands::launches),
                new CommandSpec("launch", "<name> [--mode run|debug]", "launches a configuration", LaunchCommands::launch),
                new CommandSpec("terminate", "<name>", "terminates a configuration's launches", LaunchCommands::terminate));
    }

    /** Prints the configurations and what runs. */
    private static int launches(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_launch_configurations", Arguments.create());
    }

    /** Launches the named configuration. */
    private static int launch(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "launch", Arguments.create().with("name", line.required(0, "the configuration name"))
                .with("mode", line.option("mode")));
    }

    /** Terminates the named configuration's launches. */
    private static int terminate(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "terminate_launch",
                Arguments.create().with("name", line.required(0, "the configuration name")));
    }
}
