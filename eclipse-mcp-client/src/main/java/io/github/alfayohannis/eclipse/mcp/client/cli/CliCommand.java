package io.github.alfayohannis.eclipse.mcp.client.cli;

/** One subcommand of the CLI. */
@FunctionalInterface
public interface CliCommand {

    /**
     * Runs the command.
     *
     * @return the exit code: 0 for success, 1 for a failure
     */
    int run(CommandLine line, CliContext context) throws Exception;
}
