package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.List;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.client.Arguments;
import io.github.alfayohannis.eclipse.mcp.client.Instance;
import io.github.alfayohannis.eclipse.mcp.client.InstanceDirectory;
import io.github.alfayohannis.eclipse.mcp.client.ToolInfo;

/** Subcommands about the server, the tools, the workspace and editors. */
final class WorkbenchCommands {

    /** Not instantiable: the class only has static members. */
    private WorkbenchCommands() {
    }

    /** The subcommands this class defines, for the command catalog. */
    static List<CommandSpec> all() {
        return List.of(
                new CommandSpec("status", "", "server address, Eclipse version, workspace, editors and dialogs",
                        WorkbenchCommands::status),
                new CommandSpec("instances", "", "the running Eclipse instances that announce an MCP server",
                        WorkbenchCommands::instances),
                new CommandSpec("tools", "[name]", "the tools, or one tool's description and arguments",
                        WorkbenchCommands::tools),
                new CommandSpec("call", "<tool> [key=value ... | json]", "calls any tool", WorkbenchCommands::call),
                new CommandSpec("projects", "", "workspace projects with problem counts", WorkbenchCommands::projects),
                new CommandSpec("problems", "[--severity error|warning|info] [--project P] [--limit N]",
                        "problem markers, most severe first", WorkbenchCommands::problems),
                new CommandSpec("refresh", "[path]", "refreshes a workspace path from disk", WorkbenchCommands::refresh),
                new CommandSpec("build", "[--project P] [--kind incremental|full|clean] [--timeout S]",
                        "builds and waits for the build", WorkbenchCommands::build),
                new CommandSpec("open", "<path> [--line N] [--editor ID]", "opens a file in an editor",
                        WorkbenchCommands::open),
                new CommandSpec("select", "<path>", "selects a resource in the Project Explorer",
                        WorkbenchCommands::select));
    }

    /** Prints the server status: a summary, or everything with --json. */
    private static int status(CommandLine line, CliContext context) {
        JsonObject status = context.client().serverStatus();
        Output output = context.output();
        if (output.isJson() || !status.has("eclipse")) {
            output.json(status);
            return 0;
        }
        JsonObject eclipse = status.getAsJsonObject("eclipse");
        output.line("Eclipse MCP " + status.get("version").getAsString() + " at " + status.get("url").getAsString()
                + " (" + status.get("tools").getAsInt() + " tools)");
        output.line("Eclipse " + eclipse.get("platformVersion").getAsString() + ", Java " + eclipse.get("java").getAsString());
        output.line("Workspace " + eclipse.get("workspace").getAsString() + " with " + eclipse.get("projects").getAsInt()
                + " projects");
        output.line("Window: " + eclipse.get("windowTitle").getAsString());
        Formats.shells(eclipse, output);
        return 0;
    }

    /** Lists the instance files of running servers. */
    private static int instances(CommandLine line, CliContext context) {
        InstanceDirectory directory = new InstanceDirectory(context.settings().configDirectory());
        List<Instance> running = directory.running();
        if (running.isEmpty()) {
            context.output().line("No running Eclipse announces an MCP server in " + directory.path());
            return 1;
        }
        for (Instance instance : running) {
            context.output().line(instance.baseUrl() + "  pid " + instance.pid() + "  " + instance.workspace()
                    + "  since " + instance.started());
        }
        return 0;
    }

    /** Lists the tools, or prints one tool's description. */
    private static int tools(CommandLine line, CliContext context) {
        String name = line.positional(0);
        for (ToolInfo tool : context.client().tools()) {
            if (name == null) {
                context.output().line(tool.name() + (tool.readOnly() ? "  (read-only)  " : "  ") + tool.title());
            } else if (tool.name().equals(name)) {
                context.output().json(tool.json());
                return 0;
            }
        }
        if (name != null) {
            throw new UsageException("No tool named " + name);
        }
        return 0;
    }

    /** Calls any tool with key=value arguments or one JSON object. */
    private static int call(CommandLine line, CliContext context) {
        String tool = line.required(0, "the tool name");
        return ToolCalls.run(context, tool, ToolCalls.parseArguments(line.rest(1)), Formats::json);
    }

    /** Prints the projects one per line, through list_projects. */
    private static int projects(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_projects", new JsonObject(), Formats::projects);
    }

    /** Prints the problem counts and markers, through get_problems. */
    private static int problems(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "get_problems", Arguments.create().with("severity", line.option("severity"))
                .with("project", line.option("project")).with("limit", line.intOption("limit")).toJson(), Formats::problems);
    }

    /** Refreshes a path (the workspace when none is given) after changes made outside Eclipse. */
    private static int refresh(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "refresh", Arguments.create().with("path", line.positional(0)));
    }

    /** Builds a project or the workspace and prints the resulting problem counts. */
    private static int build(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "build", Arguments.create().with("project", line.option("project"))
                .with("kind", line.option("kind")).with("timeout_s", line.intOption("timeout")));
    }

    /** Opens the file in its editor, or in the editor the --editor option names. */
    private static int open(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "open_file", Arguments.create().with("path", line.required(0, "the path"))
                .with("line", line.intOption("line")).with("editor_id", line.option("editor")));
    }

    /** Selects the resource in the Project Explorer, so commands act on it. */
    private static int select(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "select_resource", Arguments.create().with("path", line.required(0, "the path")));
    }
}
