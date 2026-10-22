package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools that type and press keys. Before posting keys they give the target
 * shell the keyboard focus, since X otherwise sends keys to whatever window is
 * under the pointer.
 */
final class KeyboardTools {

    private static final List<String> TYPE_METHODS = List.of("auto", "insert", "keys");

    /** Not instantiable: the class only has static members. */
    private KeyboardTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(typeTextTool(), pressKeysTool());
    }

    /** Defines type_text, which enters text into the focused widget. */
    private static Tool typeTextTool() {
        return Tool.builder("type_text").title("Type text")
                .description("Types text into the focused widget of a shell, or into the given widget after focusing"
                        + " it. method=auto inserts the text directly into text fields and types key by key"
                        + " elsewhere; keys always types (US layout for symbols); insert requires a text field.")
                .input(Schema.object().string("text", "Text to type; \\n presses Enter")
                        .string("shell", ArgumentTexts.SHELL).integer("widget", ArgumentTexts.WIDGET + ", focused first")
                        .choice("method", "How to enter the text (default auto)", "auto", "insert", "keys")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 300)").required("text"))
                .handler(KeyboardTools::typeText).build();
    }

    /** Defines press_keys, for shortcuts and navigation keys. */
    private static Tool pressKeysTool() {
        return Tool.builder("press_keys").title("Press keys")
                .description("Presses keys or key combinations in order, e.g. \"ctrl+s\", \"ctrl+a delete\", \"esc\","
                        + " \"shift+f10\", \"alt+shift+x\". The keys go to the focused widget of the shell, or to the"
                        + " given widget after focusing it.")
                .input(Schema.object().string("keys", "Key combinations separated by spaces or commas")
                        .string("shell", ArgumentTexts.SHELL).integer("widget", ArgumentTexts.WIDGET + ", focused first")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 500)").required("keys"))
                .handler(KeyboardTools::pressKeys).build();
    }

    /** Inserts the text into a text field when possible, else types it key by key. */
    private static ToolResult typeText(Args args) throws Exception {
        String text = args.string("text");
        if (text == null) {
            throw new IllegalArgumentException("Missing argument \"text\"");
        }
        String method = args.string("method", "auto").toLowerCase(Locale.ROOT);
        if (!TYPE_METHODS.contains(method)) {
            throw new IllegalArgumentException("method must be auto, insert or keys");
        }
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        boolean inserted = Ui.call(() -> focusAndInsert(selector, widget, text, method));
        if (!inserted) {
            Ui.pause(100);
            new SyntheticInput(Ui.display()).type(text);
        }
        return InputReport.after((inserted ? "insert " : "type ") + text.length() + " characters",
                args.integer("wait_ms", 300));
    }

    /**
     * Focuses the target and inserts the text into it when it is a text field
     * and the method allows; returns whether it did. UI thread only.
     */
    private static boolean focusAndInsert(String selector, Integer widget, String text, String method) {
        Control target = focus(selector, widget);
        boolean textField = target instanceof Text || target instanceof StyledText;
        if ("insert".equals(method) && !textField) {
            throw new ToolException("method=insert needs a focused Text or StyledText, not "
                    + (target == null ? "nothing" : target.getClass().getSimpleName()));
        }
        if ("keys".equals(method) || !textField || text.indexOf('\n') >= 0) {
            return false;
        }
        insert(target, text);
        return true;
    }

    /** Inserts at the caret, replacing the selection, as typing does. */
    private static void insert(Control target, String text) {
        if (target instanceof Text) {
            ((Text) target).insert(text);
            return;
        }
        StyledText styledText = (StyledText) target;
        styledText.insert(text);
        styledText.setCaretOffset(styledText.getCaretOffset() + text.length());
    }

    /** Presses each combination in order. */
    private static ToolResult pressKeys(Args args) throws Exception {
        List<KeyCombo> keys = new ArrayList<>();
        for (String combo : args.requiredString("keys").trim().split("[\\s,]+")) {
            if (!combo.isEmpty()) {
                keys.add(KeyCombo.parse(combo));
            }
        }
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        Ui.call(() -> focus(selector, widget));
        Ui.pause(100);
        SyntheticInput input = new SyntheticInput(Ui.display());
        for (KeyCombo key : keys) {
            input.press(key);
            Ui.pause(60);
        }
        return InputReport.after("press " + args.string("keys"), args.integer("wait_ms", 500));
    }

    /**
     * Gives the shell the keyboard focus and focuses the widget, when given;
     * returns the widget that has the focus now. UI thread only.
     */
    private static Control focus(String selector, Integer widget) {
        Shell shell = Shells.find(selector);
        Shells.activate(shell);
        if (widget == null) {
            return Ui.display().getFocusControl();
        }
        Control control = Widgets.widget(shell, widget);
        if (!control.setFocus()) {
            control.forceFocus();
        }
        return control;
    }
}
