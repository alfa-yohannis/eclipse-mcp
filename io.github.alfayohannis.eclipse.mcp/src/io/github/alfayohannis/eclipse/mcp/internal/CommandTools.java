package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.IParameter;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.CommandException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools for Eclipse commands, the actions behind menu items and key bindings.
 * Running a command by id is the steadiest way to act, since ids do not change
 * with the language or the menu layout.
 */
final class CommandTools {

    /** Not instantiable: the class only has static members. */
    private CommandTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(listCommandsTool(), runCommandTool());
    }

    /** Defines list_commands, which finds the ids that run_command needs. */
    private static Tool listCommandsTool() {
        return Tool.builder("list_commands").title("List commands")
                .description("Lists Eclipse commands whose id or name contains the filter, with their category,"
                        + " parameters and whether a handler is active right now.")
                .input(Schema.object().string("filter", "Text to look for in the id or name")
                        .integer("limit", "At most this many commands (default 50)"))
                .readOnly(true).handler(CommandTools::listCommands).build();
    }

    /** Defines run_command, which runs a command in the context of the active window and part. */
    private static Tool runCommandTool() {
        return Tool.builder("run_command").title("Run command")
                .description("Runs an Eclipse command by id, as its menu item or key binding would, in the context"
                        + " of the active window and part. A command that opens a modal dialog leaves it open; the"
                        + " result lists the open shells.")
                .input(Schema.object().string("id", "Command id, e.g. org.eclipse.ui.file.save")
                        .stringMap("parameters", "Command parameters by id")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 1000)").required("id"))
                .handler(CommandTools::runCommand).build();
    }

    /** Lists the matching commands. */
    private static ToolResult listCommands(Args args) throws Exception {
        String filter = args.string("filter", "").toLowerCase(Locale.ROOT);
        int limit = args.integer("limit", 50);
        return ToolResult.json(Ui.call(() -> describeCommands(filter, limit)));
    }

    /** The defined commands that match, sorted by id, with the total number of matches. UI thread only. */
    private static JsonObject describeCommands(String filter, int limit) throws NotDefinedException {
        ICommandService service = Ui.window().getService(ICommandService.class);
        List<Command> commands = new ArrayList<>(Arrays.asList(service.getDefinedCommands()));
        commands.sort(Comparator.comparing(Command::getId));
        JsonArray list = new JsonArray();
        int matches = 0;
        for (Command command : commands) {
            if (!matches(command, filter)) {
                continue;
            }
            matches++;
            if (list.size() < limit) {
                list.add(describe(command));
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("matches", matches);
        json.add("commands", list);
        return json;
    }

    /** Whether the command's id or name contains the filter. */
    private static boolean matches(Command command, String filter) throws NotDefinedException {
        return command.getId().toLowerCase(Locale.ROOT).contains(filter)
                || command.getName().toLowerCase(Locale.ROOT).contains(filter);
    }

    /** One command: id, name, category, whether a handler is active, and its parameters. */
    private static JsonObject describe(Command command) throws NotDefinedException {
        JsonObject json = new JsonObject();
        json.addProperty("id", command.getId());
        json.addProperty("name", command.getName());
        json.addProperty("category", command.getCategory().getName());
        json.addProperty("handled", command.isHandled());
        IParameter[] parameters = command.getParameters();
        if (parameters != null && parameters.length > 0) {
            JsonArray ids = new JsonArray();
            for (IParameter parameter : parameters) {
                ids.add(parameter.getId() + (parameter.isOptional() ? "" : " (required)"));
            }
            json.add("parameters", ids);
        }
        return json;
    }

    /** Runs the command, waiting in case it opens a dialog. */
    private static ToolResult runCommand(Args args) throws Exception {
        String id = args.requiredString("id");
        Map<String, String> parameters = args.stringMap("parameters");
        return UiActionRunner.runAndReport("run " + id, () -> execute(id, parameters), args.integer("wait_ms", 1000));
    }

    /** Executes the command through the window's handler service, as a key binding would. UI thread only. */
    private static String execute(String id, Map<String, String> parameters) throws CommandException {
        IWorkbenchWindow window = Ui.window();
        Command command = window.getService(ICommandService.class).getCommand(id);
        if (!command.isDefined()) {
            throw new ToolException("Unknown command: " + id + "; list_commands finds ids");
        }
        ParameterizedCommand parameterized = ParameterizedCommand.generateCommand(command, parameters);
        if (parameterized == null) {
            throw new ToolException("The parameters " + parameters.keySet() + " do not fit " + id);
        }
        Object value = window.getService(IHandlerService.class).executeCommand(parameterized, null);
        return value == null ? "Executed " + id : "Executed " + id + ", result: " + value;
    }
}
