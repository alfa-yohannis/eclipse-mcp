package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools for the menu bar of the workbench window, for actions that people
 * reach through menus and that have no well-known command id.
 */
final class MenuTools {

    /** Not instantiable: the class only has static members. */
    private MenuTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(listMenuTool(), clickMenuTool());
    }

    /** Defines list_menu, which shows what the menus offer right now, including contributed menus. */
    private static Tool listMenuTool() {
        return Tool.builder("list_menu").title("List menu")
                .description("Lists the menu bar of the workbench window, or the items under a label path such as"
                        + " \"File > New\", down to the given depth.")
                .input(Schema.object().string("path", "Labels separated by '>'; the menu bar when omitted")
                        .integer("depth", "Levels of submenus to include (default 1)"))
                .readOnly(true).handler(MenuTools::listMenu).build();
    }

    /** Defines click_menu, which clicks an item the way a person would. */
    private static Tool clickMenuTool() {
        return Tool.builder("click_menu").title("Click menu")
                .description("Clicks a menu item of the workbench window by its label path, e.g. \"File > New >"
                        + " Project...\". Labels match without mnemonics, shortcut text or a trailing ellipsis,"
                        + " first exactly, then by prefix, then by substring. A modal dialog that the item opens"
                        + " stays open; the result lists the shells.")
                .input(Schema.object().string("path", "Labels separated by '>'")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 1000)").required("path"))
                .handler(MenuTools::clickMenu).build();
    }

    /** Lists the menu bar or the submenu at the path. */
    private static ToolResult listMenu(Args args) throws Exception {
        List<String> path = args.path("path");
        int depth = Math.max(1, args.integer("depth", 1));
        return ToolResult.json(Ui.call(() -> describeMenu(path, depth)));
    }

    /** The menu's items, hiding the submenus the walk showed. UI thread only. */
    private static JsonObject describeMenu(List<String> path, int depth) {
        MenuWalker walker = new MenuWalker(menuBar());
        try {
            JsonObject json = new JsonObject();
            json.addProperty("menu", path.isEmpty() ? "(menu bar)" : String.join(" > ", path));
            json.add("items", walker.list(path, depth));
            return json;
        } finally {
            walker.hideShownMenus();
        }
    }

    /** Finds the item, then clicks it while waiting in case it opens a dialog. */
    private static ToolResult clickMenu(Args args) throws Exception {
        List<String> path = args.path("path");
        MenuWalker walker = Ui.call(() -> new MenuWalker(menuBar()));
        MenuItem item = Ui.call(() -> findClickable(walker, path));
        String label = String.join(" > ", path);
        return UiActionRunner.runAndReport("click " + label, () -> click(walker, item, label), args.integer("wait_ms", 1000));
    }

    /** The enabled item at the path; the walker hides its menus again when the path does not lead to one. */
    private static MenuItem findClickable(MenuWalker walker, List<String> path) {
        try {
            MenuItem item = walker.find(path);
            if (item.getMenu() != null) {
                throw new ToolException("\"" + String.join(" > ", path) + "\" opens a submenu; add the label of an"
                        + " item in it");
            }
            if (!item.isEnabled()) {
                throw new ToolException("\"" + String.join(" > ", path) + "\" is disabled right now");
            }
            return item;
        } catch (RuntimeException e) {
            walker.hideShownMenus();
            throw e;
        }
    }

    /** Clicks the item as SWT reports a real click, then hides the menus. UI thread only. */
    private static String click(MenuWalker walker, MenuItem item, String label) {
        try {
            if ((item.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
                item.setSelection(!item.getSelection());
            }
            item.notifyListeners(SWT.Selection, new Event());
        } finally {
            walker.hideShownMenus();
        }
        return "Clicked " + label;
    }

    /** The menu bar of the workbench window. UI thread only. */
    private static Menu menuBar() {
        Menu bar = Ui.window().getShell().getMenuBar();
        if (bar == null) {
            throw new ToolException("The workbench window has no menu bar");
        }
        return bar;
    }
}
