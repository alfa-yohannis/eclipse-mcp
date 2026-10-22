package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.client.Arguments;

/** Subcommands for Graphiti diagrams such as BPMN2 models; they need the plug-in's Graphiti bundle. */
final class DiagramCommands {

    private static final String MODE = "[--mode feature|mouse]";

    /** Not instantiable: the class only has static members. */
    private DiagramCommands() {
    }

    /** The subcommands this class defines, for the command catalog. */
    static List<CommandSpec> all() {
        return List.of(
                new CommandSpec("diagram", "[--editor E] [--palette]", "the open diagram's shapes and connections",
                        DiagramCommands::info),
                new CommandSpec("diagram-create", "<tool> <x> <y> [--name N] [--container ID] " + MODE,
                        "creates a shape with a palette tool", DiagramCommands::create),
                new CommandSpec("diagram-connect", "<source-id> <target-id> [--tool T] " + MODE,
                        "connects two elements (Sequence Flow by default)", DiagramCommands::connect),
                new CommandSpec("diagram-move", "<id> <x> <y> [--container ID] " + MODE, "moves a shape",
                        DiagramCommands::move),
                new CommandSpec("diagram-resize", "<id> <width> <height>", "resizes a shape", DiagramCommands::resize),
                new CommandSpec("diagram-delete", "<id>", "deletes an element", DiagramCommands::delete),
                new CommandSpec("diagram-rename", "<id> <name>", "renames an element", DiagramCommands::rename),
                new CommandSpec("diagram-set", "<id> <attribute> <value>", "sets an attribute of an element",
                        DiagramCommands::set),
                new CommandSpec("diagram-select", "[id ...]", "selects elements (none clears the selection)",
                        DiagramCommands::select));
    }

    /** Prints the shapes and connections; --palette adds the palette tools. */
    private static int info(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_info", Arguments.create().with("editor", line.option("editor"))
                .with("palette", line.flag("palette")).toJson(), Formats::diagram);
    }

    /** Creates a shape and prints the new element. */
    private static int create(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_create", Arguments.create().with("tool", line.required(0, "the palette tool"))
                .with("x", line.requiredInt(1, "x")).with("y", line.requiredInt(2, "y")).with("name", line.option("name"))
                .with("container", line.option("container")).with("mode", line.option("mode"))
                .with("editor", line.option("editor")));
    }

    /** Connects two elements and prints the new connection. */
    private static int connect(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_connect", Arguments.create().with("source", line.required(0, "the source id"))
                .with("target", line.required(1, "the target id")).with("tool", line.option("tool"))
                .with("mode", line.option("mode")).with("editor", line.option("editor")));
    }

    /** Moves a shape and prints its new bounds. */
    private static int move(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_move", Arguments.create().with("id", line.required(0, "the element id"))
                .with("x", line.requiredInt(1, "x")).with("y", line.requiredInt(2, "y"))
                .with("container", line.option("container")).with("mode", line.option("mode"))
                .with("editor", line.option("editor")));
    }

    /** Resizes a shape. */
    private static int resize(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_resize", Arguments.create().with("id", line.required(0, "the element id"))
                .with("width", line.requiredInt(1, "width")).with("height", line.requiredInt(2, "height"))
                .with("editor", line.option("editor")));
    }

    /** Deletes an element. */
    private static int delete(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_delete", Arguments.create().with("id", line.required(0, "the element id"))
                .with("editor", line.option("editor")));
    }

    /** Renames an element. */
    private static int rename(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_rename", Arguments.create().with("id", line.required(0, "the element id"))
                .with("name", line.required(1, "the name")).with("editor", line.option("editor")));
    }

    /** Sets an attribute of an element's business object. */
    private static int set(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_set_property", Arguments.create()
                .with("id", line.required(0, "the element id")).with("attribute", line.required(1, "the attribute"))
                .with("value", line.required(2, "the value")).with("editor", line.option("editor")));
    }

    /** Selects the elements. */
    private static int select(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "diagram_select", Arguments.create().with("ids", line.rest(0))
                .with("editor", line.option("editor")));
    }
}
