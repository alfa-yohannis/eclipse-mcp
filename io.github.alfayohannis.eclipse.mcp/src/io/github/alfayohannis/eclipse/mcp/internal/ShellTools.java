package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools that read and fill dialogs: list the shells and their widgets, press
 * buttons and set input fields, through widget calls that need no mouse.
 */
final class ShellTools {

    /** Not instantiable: the class only has static members. */
    private ShellTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(listShellsTool(), pressButtonTool(), setTextTool());
    }

    /** Defines list_shells, the way to read a dialog before filling it. */
    private static Tool listShellsTool() {
        return Tool.builder("list_shells").title("List shells")
                .description("Lists the open shells: the workbench window and any dialogs, with the default one"
                        + " that tools use when shell is omitted. With widgets=true it also lists the widgets of one"
                        + " shell (labels, buttons, fields, trees, tables, tabs) with their index, state and bounds"
                        + " relative to the shell.")
                .input(Schema.object().string("shell", ArgumentTexts.SHELL)
                        .bool("widgets", "Also list the widgets of the shell")
                        .stringList("types", "Only widgets of these SWT types, e.g. [\"Button\", \"Text\"]")
                        .integer("limit", "At most this many widgets (default 200)"))
                .readOnly(true).handler(ShellTools::listShells).build();
    }

    /** Defines press_button, which closes or advances dialogs. */
    private static Tool pressButtonTool() {
        return Tool.builder("press_button").title("Press button")
                .description("Presses a button, check box, radio button or tool bar item by its label (or tool tip)"
                        + " in a shell, e.g. OK, Finish, Cancel, or by widget index for buttons without a label, such"
                        + " as image buttons. Fails when the button is disabled. If pressing opens another dialog it"
                        + " stays open; the result lists the shells.")
                .input(Schema.object().string("label", "Button label, without mnemonics")
                        .integer("widget", ArgumentTexts.WIDGET + ", instead of a label")
                        .string("shell", ArgumentTexts.SHELL)
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 500)"))
                .handler(ShellTools::pressButton).build();
    }

    /** Defines set_text, which fills an input field so the dialog validates it. */
    private static Tool setTextTool() {
        return Tool.builder("set_text").title("Set text")
                .description("Sets the text of an input field (Text, Combo, CCombo, Spinner, StyledText) and fires"
                        + " the events typing would, so dialogs validate it. index counts the input fields of the"
                        + " shell from 0; widget is an index from list_shells.")
                .input(Schema.object().string("value", "The new text").string("shell", ArgumentTexts.SHELL)
                        .integer("index", "Which input field, counting from 0 (default 0)")
                        .integer("widget", ArgumentTexts.WIDGET + ", instead of index").required("value"))
                .handler(ShellTools::setText).build();
    }

    /** Lists the shells, and the widgets of one shell when asked. */
    private static ToolResult listShells(Args args) throws Exception {
        String selector = args.string("shell");
        boolean widgets = args.bool("widgets", false);
        Set<String> types = new HashSet<>();
        for (String type : args.strings("types")) {
            types.add(type.toLowerCase(Locale.ROOT));
        }
        int limit = args.integer("limit", 200);
        return ToolResult.json(Ui.call(() -> describeShells(selector, widgets, types, limit)));
    }

    /** The shell summaries plus, when asked, one shell's widgets. UI thread only. */
    private static JsonObject describeShells(String selector, boolean widgets, Set<String> types, int limit) {
        JsonObject json = new JsonObject();
        json.add("shells", Shells.describeAll());
        if (widgets || selector != null) {
            Shell shell = Shells.find(selector);
            json.addProperty("shell", shell.getText());
            json.addProperty("shellIndex", Shells.visible().indexOf(shell));
            if (widgets) {
                json.add("widgets", WidgetDescriber.describe(shell, types, limit));
            }
        }
        return json;
    }

    /** Finds the enabled button, then presses it while waiting in case it opens a dialog. */
    private static ToolResult pressButton(Args args) throws Exception {
        String label = args.string("label");
        Integer widget = args.integer("widget");
        if (label == null && widget == null) {
            throw new IllegalArgumentException("Give the button's label or its widget index");
        }
        String selector = args.string("shell");
        String name = label != null ? label : "widget #" + widget;
        Widget button = Ui.call(() -> enabledButton(selector, label, widget));
        return UiActionRunner.runAndReport("press " + name, () -> press(button, name), args.integer("wait_ms", 500));
    }

    /** The button with the label or widget index, which must be enabled. UI thread only. */
    private static Widget enabledButton(String selector, String label, Integer widget) {
        Shell shell = Shells.find(selector);
        Widget button = widget != null ? Buttons.atIndex(shell, widget) : Buttons.find(shell, label);
        if (!Buttons.isEnabled(button)) {
            throw new ToolException("The button is disabled in \"" + shell.getText()
                    + "\"; list_shells with widgets=true shows the dialog's message");
        }
        return button;
    }

    /** Presses the button and names it in the result. UI thread only. */
    private static String press(Widget button, String label) {
        Buttons.press(button);
        return "Pressed " + label;
    }

    /** Sets the field's text and reports the field and the default button. */
    private static ToolResult setText(Args args) throws Exception {
        String value = args.string("value");
        if (value == null) {
            throw new IllegalArgumentException("Missing argument \"value\"");
        }
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        int index = args.integer("index", 0);
        return ToolResult.json(Ui.call(() -> fillField(selector, widget, index, value)));
    }

    /** Sets the field given by widget index or field index. UI thread only. */
    private static JsonObject fillField(String selector, Integer widget, int index, String value) {
        Shell shell = Shells.find(selector);
        Control field = widget != null ? Widgets.widget(shell, widget) : textField(shell, index);
        TextInput.set(field, value);
        JsonObject json = new JsonObject();
        json.addProperty("shell", shell.getText());
        json.addProperty("widget", Widgets.controls(shell).indexOf(field));
        json.addProperty("type", field.getClass().getSimpleName());
        json.addProperty("text", Widgets.text(field));
        Button defaultButton = shell.getDefaultButton();
        if (defaultButton != null && !defaultButton.isDisposed()) {
            json.addProperty("defaultButton", Buttons.describe(defaultButton));
        }
        return json;
    }

    /** The input field with this index among the shell's input fields. */
    private static Control textField(Shell shell, int index) {
        List<Control> fields = Widgets.textFields(shell);
        if (index < 0 || index >= fields.size()) {
            throw new ToolException("\"" + shell.getText() + "\" has " + fields.size() + " input fields, so there is"
                    + " no field " + index);
        }
        return fields.get(index);
    }
}
