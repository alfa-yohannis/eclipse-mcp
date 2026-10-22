package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All subcommands, in the order the help lists them. */
final class CommandCatalog {

    private final List<CommandSpec> commands = new ArrayList<>();

    /** The catalog of every command group. */
    CommandCatalog() {
        commands.addAll(WorkbenchCommands.all());
        commands.addAll(UiCommands.all());
        commands.addAll(LaunchCommands.all());
        commands.addAll(DiagramCommands.all());
        commands.addAll(McpCommand.all());
    }

    /** The command with that name. */
    Optional<CommandSpec> find(String name) {
        for (CommandSpec command : commands) {
            if (command.name().equals(name)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }

    /** The help text: global options, then one block per command. */
    String help() {
        StringBuilder help = new StringBuilder();
        help.append("Usage: eclipse-mcp [--url URL | --port N] [--workspace TEXT] [--json] <command> [arguments]\n\n");
        help.append("Without --url or --port, the client uses the newest running Eclipse that announces itself in\n");
        help.append("~/.config/eclipse-mcp/instances. --json prints the plug-in's JSON as is.\n\nCommands:\n");
        for (CommandSpec command : commands) {
            help.append("  ").append(command.name());
            if (!command.usage().isEmpty()) {
                help.append(' ').append(command.usage());
            }
            help.append("\n      ").append(command.summary()).append('\n');
        }
        return help.toString();
    }
}
