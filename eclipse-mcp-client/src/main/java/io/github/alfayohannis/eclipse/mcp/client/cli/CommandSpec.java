package io.github.alfayohannis.eclipse.mcp.client.cli;

/** A subcommand with its usage line and summary, for dispatching and for help. */
public final class CommandSpec {

    private final String name;
    private final String usage;
    private final String summary;
    private final CliCommand command;

    /** A command named as typed, with its argument syntax and a one-line summary. */
    public CommandSpec(String name, String usage, String summary, CliCommand command) {
        this.name = name;
        this.usage = usage;
        this.summary = summary;
        this.command = command;
    }

    /** The name typed on the command line. */
    public String name() {
        return name;
    }

    /** The arguments, such as "&lt;path&gt; [--line N]". */
    public String usage() {
        return usage;
    }

    /** What the command does. */
    public String summary() {
        return summary;
    }

    /** The code that runs it. */
    public CliCommand command() {
        return command;
    }
}
