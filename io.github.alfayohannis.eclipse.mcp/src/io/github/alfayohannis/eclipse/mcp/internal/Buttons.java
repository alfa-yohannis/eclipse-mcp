package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Finds buttons and tool bar items by label and presses them the way a click
 * would: check boxes toggle, radio buttons take the selection from their
 * siblings, and listeners hear a Selection event. UI thread only.
 */
final class Buttons {

    /** Not instantiable: the class only has static members. */
    private Buttons() {
    }

    /** The button, or failing that the tool bar item, whose label or tool tip matches. */
    static Widget find(Shell shell, String label) {
        List<Button> buttons = new ArrayList<>();
        List<ToolItem> toolItems = new ArrayList<>();
        for (Control control : Widgets.controls(shell)) {
            collect(control, buttons, toolItems);
        }
        Button button = Labels.best(buttons, Button::getText, label);
        if (button != null) {
            return button;
        }
        ToolItem item = Labels.best(toolItems, Buttons::toolItemLabel, label);
        if (item != null) {
            return item;
        }
        throw new ToolException("No button \"" + label + "\" in \"" + shell.getText() + "\". Buttons: "
                + Labels.list(buttons, Button::getText));
    }

    /** The button with this widget index, for buttons that show only an image. */
    static Widget atIndex(Shell shell, int index) {
        Control control = Widgets.widget(shell, index);
        if (!(control instanceof Button)) {
            throw new ToolException("Widget #" + index + " is a " + control.getClass().getSimpleName() + ", not a button");
        }
        return control;
    }

    /** Whether the button or tool bar item can be pressed. */
    static boolean isEnabled(Widget widget) {
        return widget instanceof Button ? ((Button) widget).isEnabled() : ((ToolItem) widget).isEnabled();
    }

    /** Presses the button or tool bar item. */
    static void press(Widget widget) {
        if (widget instanceof ToolItem) {
            widget.notifyListeners(SWT.Selection, new Event());
            return;
        }
        Button button = (Button) widget;
        int style = button.getStyle();
        if ((style & (SWT.CHECK | SWT.TOGGLE)) != 0) {
            button.setSelection(!button.getSelection());
        } else if ((style & SWT.RADIO) != 0) {
            selectRadio(button);
        }
        button.setFocus();
        button.notifyListeners(SWT.Selection, new Event());
    }

    /** The button's label, marked when it is disabled, for shell summaries. */
    static String describe(Button button) {
        return Labels.plain(button.getText()) + (button.isEnabled() ? "" : " (disabled)");
    }

    /** A name for the button style, as list_shells reports it. */
    static String styleName(int style) {
        if ((style & SWT.CHECK) != 0) {
            return "check";
        }
        if ((style & SWT.RADIO) != 0) {
            return "radio";
        }
        if ((style & SWT.TOGGLE) != 0) {
            return "toggle";
        }
        return (style & SWT.ARROW) != 0 ? "arrow" : "push";
    }

    /** The text of a tool bar item, or its tool tip for icon-only items. */
    static String toolItemLabel(ToolItem item) {
        String text = item.getText();
        if (text != null && !text.isEmpty()) {
            return text;
        }
        return item.getToolTipText() == null ? "" : item.getToolTipText();
    }

    /** Adds a button, or the items of a tool bar, to the candidates. */
    private static void collect(Control control, List<Button> buttons, List<ToolItem> toolItems) {
        if (control instanceof Button) {
            buttons.add((Button) control);
        } else if (control instanceof ToolBar) {
            toolItems.addAll(List.of(((ToolBar) control).getItems()));
        }
    }

    /** Selects the radio button and clears its siblings, as SWT does for a real click. */
    private static void selectRadio(Button button) {
        Composite parent = button.getParent();
        if ((parent.getStyle() & SWT.NO_RADIO_GROUP) == 0) {
            for (Control sibling : parent.getChildren()) {
                clearRadio(sibling, button);
            }
        }
        button.setSelection(true);
    }

    /** Clears a selected sibling radio button and tells its listeners. */
    private static void clearRadio(Control sibling, Button chosen) {
        boolean selectedRadio = sibling != chosen && sibling instanceof Button && (sibling.getStyle() & SWT.RADIO) != 0
                && ((Button) sibling).getSelection();
        if (selectedRadio) {
            ((Button) sibling).setSelection(false);
            sibling.notifyListeners(SWT.Selection, new Event());
        }
    }
}
