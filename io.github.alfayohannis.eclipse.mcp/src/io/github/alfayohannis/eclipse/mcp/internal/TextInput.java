package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Sets the value of an input field and fires the events typing would, so the
 * dialog validates the new value: Modify comes from SWT itself, Selection for
 * combos and spinners, and KeyUp for JFace field editors that validate on key
 * release. UI thread only.
 */
final class TextInput {

    /** Not instantiable: the class only has static members. */
    private TextInput() {
    }

    /** Sets the field to the value. */
    static void set(Control control, String value) {
        if (!control.isEnabled()) {
            throw new ToolException("The " + control.getClass().getSimpleName() + " is disabled");
        }
        control.setFocus();
        if (control instanceof Text) {
            setText((Text) control, value);
        } else if (control instanceof StyledText) {
            setStyledText((StyledText) control, value);
        } else if (control instanceof Combo) {
            setCombo((Combo) control, value);
        } else if (control instanceof CCombo) {
            setCCombo((CCombo) control, value);
        } else if (control instanceof Spinner) {
            setSpinner((Spinner) control, value);
        } else {
            throw new ToolException("Widget is a " + control.getClass().getSimpleName() + ", not an input field");
        }
        control.notifyListeners(SWT.KeyUp, new Event());
    }

    /** A plain text field, unless it is read-only. */
    private static void setText(Text text, String value) {
        if (!text.getEditable()) {
            throw new ToolException("The text field is read-only");
        }
        text.setText(value);
    }

    /** A styled text field such as an editor, unless it is read-only. */
    private static void setStyledText(StyledText text, String value) {
        if (!text.getEditable()) {
            throw new ToolException("The text field is read-only");
        }
        text.setText(value);
    }

    /** A combo: a read-only one must get one of its items, an editable one takes any text. */
    private static void setCombo(Combo combo, String value) {
        int item = combo.indexOf(value);
        if ((combo.getStyle() & SWT.READ_ONLY) != 0 && item < 0) {
            throw new ToolException("The combo has no item \"" + value + "\"; items: " + String.join(", ", combo.getItems()));
        }
        if (item >= 0) {
            combo.select(item);
        } else {
            combo.setText(value);
        }
        combo.notifyListeners(SWT.Selection, new Event());
    }

    /** A custom combo: one of its items, or any text. */
    private static void setCCombo(CCombo combo, String value) {
        int item = combo.indexOf(value);
        if (item >= 0) {
            combo.select(item);
        } else {
            combo.setText(value);
        }
        combo.notifyListeners(SWT.Selection, new Event());
    }

    /** A spinner, which takes a whole number. */
    private static void setSpinner(Spinner spinner, String value) {
        try {
            spinner.setSelection(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            throw new ToolException("A spinner takes a whole number");
        }
        spinner.notifyListeners(SWT.Selection, new Event());
    }
}
