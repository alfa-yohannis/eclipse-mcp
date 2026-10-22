package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.client.Arguments;
import io.github.alfayohannis.eclipse.mcp.client.Screenshot;

/** Subcommands that drive the IDE: commands, menus, dialogs, trees, screenshots, mouse and keyboard. */
final class UiCommands {

    /** Not instantiable: the class only has static members. */
    private UiCommands() {
    }

    /** The subcommands this class defines, for the command catalog. */
    static List<CommandSpec> all() {
        return List.of(
                new CommandSpec("commands", "[filter] [--limit N]", "Eclipse commands whose id or name contains the filter",
                        UiCommands::commands),
                new CommandSpec("run", "<command-id> [key=value ...] [--wait MS]", "runs a command by id", UiCommands::run),
                new CommandSpec("menu", "[\"File > New\"] [--depth N]", "the menu bar or a submenu", UiCommands::menu),
                new CommandSpec("click-menu", "<\"File > New > Project...\"> [--wait MS]", "clicks a menu item",
                        UiCommands::clickMenu),
                new CommandSpec("shells", "[--shell S] [--widgets]", "open windows and dialogs, and their widgets",
                        UiCommands::shells),
                new CommandSpec("press", "<label> [--shell S] [--widget N]", "presses a button", UiCommands::press),
                new CommandSpec("set-text", "<value> [--shell S] [--index N] [--widget N]", "fills an input field",
                        UiCommands::setText),
                new CommandSpec("tree", "<\"a > b > c\"> [--shell S] [--view V] [--widget N] [--open]",
                        "selects a tree or table item", UiCommands::tree),
                new CommandSpec("tree-items", "[path] [--shell S] [--view V] [--depth N]", "lists tree or table items",
                        UiCommands::treeItems),
                new CommandSpec("screenshot", "[file.png] [--target shell|window|widget|screen] [--shell S] [--widget N]"
                        + " [--scale X]", "saves a PNG screenshot (default screenshot.png)", UiCommands::screenshot),
                new CommandSpec("click", "<x> <y> [--shell S] [--widget N] [--button left|middle|right] [--count N]",
                        "clicks with the real mouse", UiCommands::click),
                new CommandSpec("drag", "<x1> <y1> <x2> <y2> [--shell S] [--widget N]", "drags with the real mouse",
                        UiCommands::drag),
                new CommandSpec("type", "<text> [--shell S] [--widget N] [--method auto|insert|keys]",
                        "types into the focused widget", UiCommands::type),
                new CommandSpec("keys", "<combo> [combo ...] [--shell S] [--widget N]", "presses keys such as ctrl+s",
                        UiCommands::keys));
    }

    /** Prints matching command ids and names, the ids that run takes. */
    private static int commands(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_commands", Arguments.create().with("filter", line.positional(0))
                .with("limit", line.intOption("limit")).toJson(), Formats::commands);
    }

    /** Runs a command by id; key=value words become its parameters. */
    private static int run(CommandLine line, CliContext context) {
        JsonObject arguments = Arguments.create().with("id", line.required(0, "the command id"))
                .with("wait_ms", line.intOption("wait")).toJson();
        List<String> parameters = line.rest(1);
        if (!parameters.isEmpty()) {
            arguments.add("parameters", ToolCalls.parseArguments(parameters));
        }
        return ToolCalls.run(context, "run_command", arguments, Formats::json);
    }

    /** Prints the menu bar, or the submenu at the path, as an indented tree. */
    private static int menu(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_menu", Arguments.create().with("path", line.positional(0))
                .with("depth", line.intOption("depth")).toJson(), Formats::menu);
    }

    /** Clicks a menu item and reports the dialogs it opened. */
    private static int clickMenu(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "click_menu", Arguments.create().with("path", line.required(0, "the menu path"))
                .with("wait_ms", line.intOption("wait")));
    }

    /** Prints the shells, and with --widgets the widgets of the default or given shell. */
    private static int shells(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_shells", Arguments.create().with("shell", line.option("shell"))
                .with("widgets", line.flag("widgets") ? Boolean.TRUE : null).toJson(), Formats::shells);
    }

    /** Presses a button by label, or by widget index for image buttons. */
    private static int press(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "press_button", Arguments.create().with("label", line.positional(0))
                .with("shell", line.option("shell")).with("widget", line.intOption("widget")));
    }

    /** Sets an input field of a dialog. */
    private static int setText(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "set_text", Arguments.create().with("value", line.required(0, "the value"))
                .with("shell", line.option("shell")).with("index", line.intOption("index"))
                .with("widget", line.intOption("widget")));
    }

    /** Selects a tree item by label path, opening it with --open. */
    private static int tree(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "select_tree_item", Arguments.create().with("path", line.required(0, "the item path"))
                .with("shell", line.option("shell")).with("view", line.option("view"))
                .with("widget", line.intOption("widget")).with("open", line.flag("open") ? Boolean.TRUE : null));
    }

    /** Lists tree items under a path. */
    private static int treeItems(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "list_tree_items", Arguments.create().with("path", line.positional(0))
                .with("shell", line.option("shell")).with("view", line.option("view"))
                .with("widget", line.intOption("widget")).with("depth", line.intOption("depth")));
    }

    /** Saves a screenshot to the file and prints what it shows. */
    private static int screenshot(CommandLine line, CliContext context) throws java.io.IOException {
        Screenshot screenshot = context.client().screenshot(line.option("target"), line.option("shell"),
                line.intOption("widget"), line.doubleOption("scale"));
        Path file = screenshot.save(Paths.get(line.positional(0) == null ? "screenshot.png" : line.positional(0)));
        context.output().line("Saved " + file + ": " + screenshot.details().get("captured").getAsString());
        return 0;
    }

    /** Clicks at a point of a shell or widget with the real mouse. */
    private static int click(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "mouse_click", Arguments.create().with("x", line.requiredInt(0, "x"))
                .with("y", line.requiredInt(1, "y")).with("shell", line.option("shell"))
                .with("widget", line.intOption("widget")).with("button", line.option("button"))
                .with("count", line.intOption("count")));
    }

    /** Drags between two points with the real mouse. */
    private static int drag(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "mouse_drag", Arguments.create().with("from_x", line.requiredInt(0, "x1"))
                .with("from_y", line.requiredInt(1, "y1")).with("to_x", line.requiredInt(2, "x2"))
                .with("to_y", line.requiredInt(3, "y2")).with("shell", line.option("shell"))
                .with("widget", line.intOption("widget")));
    }

    /** Types text into the focused widget. */
    private static int type(CommandLine line, CliContext context) {
        return ToolCalls.run(context, "type_text", Arguments.create().with("text", line.required(0, "the text"))
                .with("shell", line.option("shell")).with("widget", line.intOption("widget"))
                .with("method", line.option("method")));
    }

    /** Presses the key combinations in order. */
    private static int keys(CommandLine line, CliContext context) {
        List<String> combos = line.rest(0);
        if (combos.isEmpty()) {
            throw new UsageException("Missing the keys, such as ctrl+s");
        }
        return ToolCalls.run(context, "press_keys", Arguments.create().with("keys", String.join(" ", combos))
                .with("shell", line.option("shell")).with("widget", line.intOption("widget")));
    }
}
