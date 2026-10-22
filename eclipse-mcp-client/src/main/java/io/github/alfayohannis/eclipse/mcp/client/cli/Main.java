package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.github.alfayohannis.eclipse.mcp.client.ClientSettings;
import io.github.alfayohannis.eclipse.mcp.client.EclipseMcpException;
import io.github.alfayohannis.eclipse.mcp.client.EclipseNotRunningException;
import io.github.alfayohannis.eclipse.mcp.client.ToolFailedException;

/**
 * The eclipse-mcp command line: global options, then a subcommand. Exit codes:
 * 0 success, 1 failure, 2 wrong usage, 3 Eclipse not running.
 */
public final class Main {

    /** Exit code when the command line is wrong. */
    static final int USAGE = 2;
    /** Exit code when no Eclipse could be reached. */
    static final int NOT_RUNNING = 3;

    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;

    /** A CLI over the given streams, which tests replace. */
    public Main(InputStream in, PrintStream out, PrintStream err) {
        this.in = in;
        this.out = out;
        this.err = err;
    }

    /** Runs the CLI on the process's streams and exits with its code. */
    public static void main(String[] args) {
        int code = new Main(System.in, System.out, System.err).run(Arrays.asList(args));
        System.exit(code);
    }

    /** Runs one command line and returns the exit code. */
    public int run(List<String> args) {
        CommandCatalog catalog = new CommandCatalog();
        try {
            return runCommand(catalog, args);
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.println("Run eclipse-mcp help for the commands.");
            return USAGE;
        } catch (EclipseNotRunningException e) {
            err.println(e.getMessage());
            return NOT_RUNNING;
        } catch (ToolFailedException e) {
            err.println(e.result().text());
            return 1;
        } catch (EclipseMcpException e) {
            err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("Failed: " + e);
            return 1;
        }
    }

    /** Splits global options from the command, then runs it. */
    private int runCommand(CommandCatalog catalog, List<String> args) throws Exception {
        List<String> remaining = new ArrayList<>(args);
        ClientSettings.Builder settings = ClientSettings.builder();
        boolean json = consumeGlobalOptions(remaining, settings);
        if (remaining.isEmpty() || List.of("help", "-h", "--help").contains(remaining.get(0))) {
            out.print(catalog.help());
            return remaining.isEmpty() ? USAGE : 0;
        }
        Optional<CommandSpec> command = catalog.find(remaining.get(0));
        if (command.isEmpty()) {
            throw new UsageException("Unknown command: " + remaining.get(0));
        }
        CliContext context = new CliContext(settings.build(), new Output(out, json), in, out);
        return command.get().command().run(new CommandLine(remaining.subList(1, remaining.size())), context);
    }

    /** Removes the global options from the front of the list into the settings; returns whether --json was given. */
    private static boolean consumeGlobalOptions(List<String> args, ClientSettings.Builder settings) {
        boolean json = false;
        while (!args.isEmpty() && args.get(0).startsWith("--") && !args.get(0).equals("--help")) {
            String option = args.remove(0);
            if ("--json".equals(option)) {
                json = true;
            } else {
                applyOption(option, takeValue(args, option), settings);
            }
        }
        return json;
    }

    /** Sets one global option that takes a value. */
    private static void applyOption(String option, String value, ClientSettings.Builder settings) {
        switch (option) {
        case "--url":
            settings.url(value);
            break;
        case "--port":
            settings.port(parsePort(value));
            break;
        case "--workspace":
            settings.workspace(value);
            break;
        case "--config":
            settings.configDirectory(java.nio.file.Paths.get(value));
            break;
        case "--timeout":
            settings.timeout(Duration.ofSeconds(parsePort(value)));
            break;
        default:
            throw new UsageException("Unknown option " + option);
        }
    }

    /** The value that follows an option. */
    private static String takeValue(List<String> args, String option) {
        if (args.isEmpty()) {
            throw new UsageException(option + " needs a value");
        }
        return args.remove(0);
    }

    /** A whole number for --port or --timeout. */
    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new UsageException("Expected a whole number, not \"" + value + "\"");
        }
    }
}
