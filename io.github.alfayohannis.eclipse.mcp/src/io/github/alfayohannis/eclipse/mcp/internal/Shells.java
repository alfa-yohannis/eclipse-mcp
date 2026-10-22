package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Finds and describes the open shells: the workbench window and its dialogs.
 * Tools name a shell by index or title; without a name they act on the shell
 * a person would act on. All methods run on the UI thread.
 */
public final class Shells {

    private static final int MODAL = SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL;

    /** Not instantiable: the class only has static members. */
    private Shells() {
    }

    /** The visible shells, numbered in this order by the tools. */
    public static List<Shell> visible() {
        List<Shell> shells = new ArrayList<>();
        for (Shell shell : Ui.display().getShells()) {
            if (!shell.isDisposed() && shell.isVisible()) {
                shells.add(shell);
            }
        }
        return shells;
    }

    /** The shell with this index or title; null or blank picks the preferred shell. */
    public static Shell find(String selector) {
        List<Shell> shells = visible();
        if (selector == null || selector.isBlank()) {
            return preferred(shells);
        }
        String trimmed = selector.trim();
        Shell shell = trimmed.matches("\\d+") ? byIndex(shells, Integer.parseInt(trimmed))
                : Labels.best(shells, Shell::getText, trimmed);
        if (shell == null) {
            throw new ToolException("No open shell \"" + trimmed + "\". Open shells: " + Labels.list(shells, Shell::getText));
        }
        return shell;
    }

    /**
     * The innermost modal dialog if one is open, since it blocks the others;
     * else the active shell; else the workbench window.
     */
    public static Shell preferred(List<Shell> shells) {
        Shell innermost = null;
        for (Shell shell : shells) {
            if (isModal(shell) && (innermost == null || depth(shell) > depth(innermost))) {
                innermost = shell;
            }
        }
        if (innermost != null) {
            return innermost;
        }
        Shell active = Ui.display().getActiveShell();
        return active != null && active.isVisible() ? active : Ui.window().getShell();
    }

    /**
     * Gives the shell the keyboard focus, so posted keys reach it. Without a
     * window manager, X sends keys to the window under the pointer unless a
     * window takes the focus this way. A shell blocked by a modal child is left
     * alone, since raising it would hide the dialog.
     */
    public static void activate(Shell shell) {
        if (!hasVisibleModalChild(shell)) {
            shell.forceActive();
        }
    }

    /** Summaries of all visible shells, marking the preferred and the active one. */
    public static JsonArray describeAll() {
        List<Shell> shells = visible();
        Shell preferred = shells.isEmpty() ? null : preferred(shells);
        Shell active = Ui.display().getActiveShell();
        JsonArray array = new JsonArray();
        for (int index = 0; index < shells.size(); index++) {
            array.add(describe(shells, index, preferred, active));
        }
        return array;
    }

    /** Whether the shell is the shell of a workbench window rather than a dialog. */
    public static boolean isWorkbenchWindow(Shell shell) {
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            if (window.getShell() == shell) {
                return true;
            }
        }
        return false;
    }

    /** One shell's summary: index, title, flags, parent, default button and bounds. */
    private static JsonObject describe(List<Shell> shells, int index, Shell preferred, Shell active) {
        Shell shell = shells.get(index);
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("title", shell.getText());
        addFlag(json, "default", shell == preferred);
        addFlag(json, "active", shell == active);
        addFlag(json, "modal", isModal(shell));
        addFlag(json, "workbenchWindow", isWorkbenchWindow(shell));
        int parent = shells.indexOf(shell.getParent());
        if (parent >= 0) {
            json.addProperty("parent", parent);
        }
        Button defaultButton = shell.getDefaultButton();
        if (defaultButton != null && !defaultButton.isDisposed()) {
            json.addProperty("defaultButton", Buttons.describe(defaultButton));
        }
        json.add("bounds", Widgets.bounds(shell.getBounds()));
        return json;
    }

    /** Adds a true flag; false flags are left out to keep the output short. */
    private static void addFlag(JsonObject json, String name, boolean value) {
        if (value) {
            json.addProperty(name, true);
        }
    }

    /** The shell at this index, or null. */
    private static Shell byIndex(List<Shell> shells, int index) {
        return index < shells.size() ? shells.get(index) : null;
    }

    /** Whether the shell blocks input to its parent or the whole application. */
    private static boolean isModal(Shell shell) {
        return (shell.getStyle() & MODAL) != 0;
    }

    /** Whether a visible modal dialog sits on top of the shell. */
    private static boolean hasVisibleModalChild(Shell shell) {
        for (Shell child : shell.getShells()) {
            if (child.isVisible() && isModal(child)) {
                return true;
            }
        }
        return false;
    }

    /** How many parent shells the shell has; nested dialogs are deeper. */
    private static int depth(Shell shell) {
        int depth = 0;
        for (Composite parent = shell.getParent(); parent != null; parent = parent.getParent()) {
            depth++;
        }
        return depth;
    }
}
