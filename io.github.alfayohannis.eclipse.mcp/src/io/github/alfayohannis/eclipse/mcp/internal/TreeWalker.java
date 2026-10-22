package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Finds, expands, lists and selects items of SWT trees and tables by their
 * labels. Expanding sends SWT.Expand before opening an item, because JFace
 * viewers create the children only then. UI thread only.
 */
final class TreeWalker {

    private final Control control;

    /** A walker over a Tree or a Table. */
    private TreeWalker(Control control) {
        this.control = control;
    }

    /** The walker for the Tree or Table given by widget index, by the title of the view that holds it, or the first. */
    static TreeWalker find(Shell shell, Integer widget, String view) {
        if (widget != null) {
            return new TreeWalker(requireTreeOrTable(Widgets.widget(shell, widget), widget));
        }
        for (Control candidate : Widgets.controls(shell)) {
            if (isTreeOrTable(candidate) && isInView(candidate, view)) {
                return new TreeWalker(candidate);
            }
        }
        throw new ToolException(view == null ? "No tree or table in \"" + shell.getText() + "\""
                : "No tree or table in a view titled \"" + view + "\"");
    }

    /** Activates the open view whose title matches, so its tree is visible and its selection becomes current. */
    static void activateView(IWorkbenchPage page, String view) {
        List<IViewReference> references = Arrays.asList(page.getViewReferences());
        IViewReference reference = Labels.best(references, IViewReference::getPartName, view);
        if (reference == null) {
            throw new ToolException("No open view is titled \"" + view + "\". Open views: "
                    + Labels.list(references, IViewReference::getPartName));
        }
        IWorkbenchPart part = reference.getPart(true);
        if (part != null) {
            page.activate(part);
        }
    }

    /** The SWT type of the control, for results. */
    String type() {
        return control.getClass().getSimpleName();
    }

    /** The item at the label path, expanding the items on the way. */
    Item walk(List<String> path) {
        if (path.isEmpty()) {
            throw new ToolException("Give the item's label path, such as \"General > Appearance\"");
        }
        if (control instanceof Table) {
            return tableRow(path.get(0));
        }
        TreeItem item = null;
        for (int level = 0; level < path.size(); level++) {
            item = childAt(item, path, level);
        }
        return item;
    }

    /** Selects the item as a click would: the selection changes and listeners hear a Selection event. */
    void select(Item item) {
        if (control instanceof Tree) {
            ((Tree) control).setSelection((TreeItem) item);
            ((Tree) control).showItem((TreeItem) item);
        } else {
            ((Table) control).setSelection((TableItem) item);
            ((Table) control).showItem((TableItem) item);
        }
        control.setFocus();
        notify(SWT.Selection, item);
    }

    /** Opens the item as a double-click would, which may open an editor or a dialog. */
    void open(Item item) {
        notify(SWT.DefaultSelection, item);
    }

    /** The labels of the items below the path, or of the top items, down to the given depth. */
    JsonArray list(List<String> path, int depth, int limit) {
        int[] budget = { limit };
        if (control instanceof Table) {
            return tableRows(budget);
        }
        TreeItem[] items = ((Tree) control).getItems();
        if (!path.isEmpty()) {
            TreeItem parent = (TreeItem) walk(path);
            expand(parent);
            items = parent.getItems();
        }
        return treeItems(items, depth, budget);
    }

    /** The child of the parent (or a top item) whose label matches the path at this level. */
    private TreeItem childAt(TreeItem parent, List<String> path, int level) {
        if (parent != null) {
            expand(parent);
        }
        List<TreeItem> candidates = Arrays.asList(parent == null ? ((Tree) control).getItems() : parent.getItems());
        TreeItem item = Labels.best(candidates, TreeItem::getText, path.get(level));
        if (item == null) {
            String where = level == 0 ? " at the top" : " under \"" + String.join(" > ", path.subList(0, level)) + "\"";
            throw new MissingItemException("No item \"" + path.get(level) + "\"" + where + ". Items: "
                    + Labels.list(candidates, TreeItem::getText));
        }
        return item;
    }

    /** The table row whose text matches. */
    private TableItem tableRow(String label) {
        List<TableItem> rows = Arrays.asList(((Table) control).getItems());
        TableItem row = Labels.best(rows, TreeWalker::rowText, label);
        if (row == null) {
            throw new MissingItemException("No row \"" + label + "\". Rows: " + Labels.list(rows, TreeWalker::rowText));
        }
        return row;
    }

    /** Opens the item, first letting JFace create its children. */
    private void expand(TreeItem item) {
        if (!item.getExpanded()) {
            notify(SWT.Expand, item);
            item.setExpanded(true);
        }
    }

    /** Sends the control an event about the item. */
    private void notify(int type, Item item) {
        Event event = new Event();
        event.item = item;
        control.notifyListeners(type, event);
    }

    /** The table rows as JSON, within the budget. */
    private JsonArray tableRows(int[] budget) {
        JsonArray rows = new JsonArray();
        for (TableItem row : ((Table) control).getItems()) {
            if (budget[0]-- <= 0) {
                break;
            }
            JsonObject json = new JsonObject();
            json.addProperty("label", rowText(row));
            rows.add(json);
        }
        return rows;
    }

    /** The tree items as JSON, expanding them down to the depth, within the budget. */
    private JsonArray treeItems(TreeItem[] items, int depth, int[] budget) {
        JsonArray array = new JsonArray();
        for (TreeItem item : items) {
            if (budget[0]-- <= 0) {
                break;
            }
            if (!isPlaceholder(item)) {
                array.add(treeItem(item, depth, budget));
            }
        }
        return array;
    }

    /** One tree item with its children, or a hasChildren flag at the last level. */
    private JsonObject treeItem(TreeItem item, int depth, int[] budget) {
        JsonObject json = new JsonObject();
        json.addProperty("label", item.getText());
        if (item.getItemCount() > 0 && depth > 1) {
            expand(item);
            json.add("children", treeItems(item.getItems(), depth - 1, budget));
        } else if (item.getItemCount() > 0) {
            json.addProperty("hasChildren", true);
        }
        return json;
    }

    /** Whether the item is JFace's empty stand-in for children that do not exist yet. */
    private static boolean isPlaceholder(TreeItem item) {
        return item.getText().isEmpty() && item.getItemCount() == 0;
    }

    /** The non-empty cells of a row joined with " | ", so any column can match. */
    private static String rowText(TableItem row) {
        int columns = Math.max(1, row.getParent().getColumnCount());
        List<String> cells = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            if (!row.getText(column).isEmpty()) {
                cells.add(row.getText(column));
            }
        }
        return String.join(" | ", cells);
    }

    /** Whether the control is a Tree or Table. */
    private static boolean isTreeOrTable(Control control) {
        return control instanceof Tree || control instanceof Table;
    }

    /** Whether the control sits in the view with this title; any control matches when no view is given. */
    private static boolean isInView(Control control, String view) {
        if (view == null) {
            return true;
        }
        String part = Widgets.part(control);
        return part != null && Labels.key(part).contains(Labels.key(view));
    }

    /** The control when it is a Tree or Table, else a clear failure. */
    private static Control requireTreeOrTable(Control control, int widget) {
        if (!isTreeOrTable(control)) {
            throw new ToolException("Widget #" + widget + " is a " + control.getClass().getSimpleName()
                    + ", not a Tree or Table");
        }
        return control;
    }
}
