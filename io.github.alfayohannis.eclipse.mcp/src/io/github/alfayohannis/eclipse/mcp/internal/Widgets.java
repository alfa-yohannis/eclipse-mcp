package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Finds the controls of a shell and reads what a person sees on them. Widget
 * indexes count the visible controls depth first, so the same dialog always
 * gets the same numbers. All methods run on the UI thread.
 */
public final class Widgets {

    private static final int MAX_TEXT = 400;

    /** Not instantiable: the class only has static members. */
    private Widgets() {
    }

    /** All visible controls of the shell, depth first. */
    public static List<Control> controls(Shell shell) {
        List<Control> controls = new ArrayList<>();
        collectVisible(shell, controls);
        return controls;
    }

    /** The control with this index; fails with a hint when the index is out of range. */
    public static Control widget(Shell shell, int index) {
        List<Control> controls = controls(shell);
        if (index < 0 || index >= controls.size()) {
            throw new ToolException("\"" + shell.getText() + "\" has no widget #" + index
                    + "; list_shells with widgets=true shows the indexes");
        }
        return controls.get(index);
    }

    /** The input fields in widget order: Text, StyledText, Combo, CCombo and Spinner. */
    public static List<Control> textFields(Shell shell) {
        List<Control> fields = new ArrayList<>();
        for (Control control : controls(shell)) {
            if (isTextField(control)) {
                fields.add(control);
            }
        }
        return fields;
    }

    /** Whether the control takes typed text. */
    public static boolean isTextField(Control control) {
        return control instanceof Text || control instanceof StyledText || control instanceof Combo
                || control instanceof CCombo || control instanceof Spinner;
    }

    /** The title of the view or editor tab that holds the control, or null outside parts. */
    public static String part(Control control) {
        Control child = control;
        for (Composite parent = control.getParent(); parent != null && !(parent instanceof Shell); parent = parent
                .getParent()) {
            String title = parent instanceof CTabFolder ? tabTitle((CTabFolder) parent, child) : null;
            if (title != null) {
                return title;
            }
            child = parent;
        }
        return null;
    }

    /** Bounds of the control relative to the top-left corner of the shell, the origin of shell screenshots. */
    public static JsonObject relativeBounds(Shell shell, Control control) {
        Point origin = control.toDisplay(0, 0);
        Rectangle shellBounds = shell.getBounds();
        Point size = control.getSize();
        return bounds(new Rectangle(origin.x - shellBounds.x, origin.y - shellBounds.y, size.x, size.y));
    }

    /** A rectangle as {x, y, width, height}. */
    public static JsonObject bounds(Rectangle bounds) {
        JsonObject json = new JsonObject();
        json.addProperty("x", bounds.x);
        json.addProperty("y", bounds.y);
        json.addProperty("width", bounds.width);
        json.addProperty("height", bounds.height);
        return json;
    }

    /** The text a person sees on the control, or null for controls without text; passwords are masked. */
    public static String text(Control control) {
        String text = rawText(control);
        return text == null ? null : Labels.clip(text, MAX_TEXT);
    }

    /** The control's text as the widget holds it, without clipping. */
    private static String rawText(Control control) {
        if (control instanceof Button) {
            return Labels.plain(((Button) control).getText());
        }
        if (control instanceof Label) {
            return Labels.plain(((Label) control).getText());
        }
        if (control instanceof CLabel) {
            return Labels.plain(((CLabel) control).getText());
        }
        if (control instanceof Link) {
            return ((Link) control).getText().replaceAll("</?a[^>]*>", "");
        }
        if (control instanceof Group) {
            return Labels.plain(((Group) control).getText());
        }
        return fieldText(control);
    }

    /** The content of an input field, or null for other controls. */
    private static String fieldText(Control control) {
        if (control instanceof Text) {
            String text = ((Text) control).getText();
            return (control.getStyle() & SWT.PASSWORD) != 0 && !text.isEmpty() ? "********" : text;
        }
        if (control instanceof StyledText) {
            return ((StyledText) control).getText();
        }
        if (control instanceof Combo) {
            return ((Combo) control).getText();
        }
        if (control instanceof CCombo) {
            return ((CCombo) control).getText();
        }
        if (control instanceof Spinner) {
            return ((Spinner) control).getText();
        }
        return null;
    }

    /** The title of the tab whose control is the child, or null. */
    private static String tabTitle(CTabFolder folder, Control child) {
        for (CTabItem item : folder.getItems()) {
            if (item.getControl() == child) {
                return Labels.plain(item.getText());
            }
        }
        return null;
    }

    /** Adds the visible children of the composite and their visible descendants. */
    private static void collectVisible(Composite parent, List<Control> controls) {
        for (Control child : parent.getChildren()) {
            if (!child.getVisible()) {
                continue;
            }
            controls.add(child);
            if (child instanceof Composite) {
                collectVisible((Composite) child, controls);
            }
        }
    }
}
