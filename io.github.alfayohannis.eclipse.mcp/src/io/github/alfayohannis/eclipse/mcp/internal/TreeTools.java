package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;

import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools for trees and tables, such as the Preferences page tree, wizard lists
 * and the Project Explorer.
 */
final class TreeTools {

    /** JFace may fill children in the background, so a missing label is retried this many times. */
    private static final int ATTEMPTS = 8;
    private static final int RETRY_PAUSE_MS = 300;

    /** Not instantiable: the class only has static members. */
    private TreeTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(selectTreeItemTool(), listTreeItemsTool());
    }

    /** Defines select_tree_item, which picks pages, wizards and resources by their labels. */
    private static Tool selectTreeItemTool() {
        return Tool.builder("select_tree_item").title("Select tree item")
                .description("Selects an item of a tree or table by its label path, expanding the items on the way:"
                        + " e.g. \"General > Appearance\" in Preferences, or \"project > folder > file.bpmn\" with"
                        + " view=\"Project Explorer\". open=true double-clicks it, which may open an editor or a"
                        + " dialog.")
                .input(Schema.object().string("path", "Labels separated by '>'").string("shell", ArgumentTexts.SHELL)
                        .string("view", "Title of a view in the workbench window that holds the tree")
                        .integer("widget", ArgumentTexts.WIDGET + " of the Tree or Table")
                        .bool("open", "Double-click the item after selecting it")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + ", with open (default 1000)").required("path"))
                .handler(TreeTools::selectTreeItem).build();
    }

    /** Defines list_tree_items, which shows what a tree contains before selecting in it. */
    private static Tool listTreeItemsTool() {
        return Tool.builder("list_tree_items").title("List tree items")
                .description("Lists the items of a tree or table, the top level or those under a label path,"
                        + " expanding them down to the given depth.")
                .input(Schema.object().string("path", "Labels separated by '>'; the top level when omitted")
                        .string("shell", ArgumentTexts.SHELL)
                        .string("view", "Title of a view in the workbench window that holds the tree")
                        .integer("widget", ArgumentTexts.WIDGET + " of the Tree or Table")
                        .integer("depth", "Levels to list (default 1)").integer("limit", "At most this many items (default 300)"))
                .readOnly(true).handler(TreeTools::listTreeItems).build();
    }

    /** Selects the item, retrying while children appear, then opens it when asked. */
    private static ToolResult selectTreeItem(Args args) throws Exception {
        List<String> path = args.path("path");
        String selector = args.string("shell");
        String view = args.string("view");
        Integer widget = args.integer("widget");
        TreeSelection selection = selectWithRetries(selector, view, widget, path);
        if (args.bool("open", false)) {
            return UiActionRunner.runAndReport("open " + String.join(" > ", path), selection::open,
                    args.integer("wait_ms", 1000));
        }
        JsonObject result = new JsonObject();
        result.addProperty("selected", String.join(" > ", path));
        result.addProperty("label", Ui.call(selection::label));
        return ToolResult.json(result);
    }

    /** Tries the selection a few times, pausing between attempts so background fills can finish. */
    private static TreeSelection selectWithRetries(String selector, String view, Integer widget, List<String> path)
            throws Exception {
        MissingItemException lastMiss = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (attempt > 0) {
                Ui.pause(RETRY_PAUSE_MS);
            }
            try {
                return Ui.call(() -> select(selector, view, widget, path));
            } catch (MissingItemException e) {
                lastMiss = e;
            }
        }
        throw lastMiss;
    }

    /** Walks to the item and selects it. UI thread only. */
    private static TreeSelection select(String selector, String view, Integer widget, List<String> path) {
        TreeWalker walker = walker(selector, view, widget);
        Item item = walker.walk(path);
        walker.select(item);
        return new TreeSelection(walker, item);
    }

    /** Lists the items under the path. */
    private static ToolResult listTreeItems(Args args) throws Exception {
        List<String> path = args.path("path");
        String selector = args.string("shell");
        String view = args.string("view");
        Integer widget = args.integer("widget");
        int depth = Math.max(1, args.integer("depth", 1));
        int limit = args.integer("limit", 300);
        return ToolResult.json(Ui.call(() -> describeItems(selector, view, widget, path, depth, limit)));
    }

    /** The items as JSON. UI thread only. */
    private static JsonObject describeItems(String selector, String view, Integer widget, List<String> path, int depth,
            int limit) {
        TreeWalker walker = walker(selector, view, widget);
        JsonObject json = new JsonObject();
        json.addProperty("type", walker.type());
        json.addProperty("path", String.join(" > ", path));
        json.add("items", walker.list(path, depth, limit));
        return json;
    }

    /**
     * The walker for the tree to act on. A view title without a shell means a
     * view of the workbench window, which is activated first so its tree is
     * visible and its selection becomes current. UI thread only.
     */
    private static TreeWalker walker(String selector, String view, Integer widget) {
        if (view != null && selector == null) {
            TreeWalker.activateView(Ui.page(), view);
            return TreeWalker.find(Ui.window().getShell(), widget, view);
        }
        Shell shell = Shells.find(selector);
        return TreeWalker.find(shell, widget, view);
    }
}
