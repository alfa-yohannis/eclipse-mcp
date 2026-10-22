package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Walks the menu bar of a window. Eclipse fills most menus only when they are
 * about to show, so the walker sends each submenu SWT.Show before reading it
 * and remembers it, so that {@link #hideShownMenus()} can send SWT.Hide
 * afterwards. Use one walker per walk, on the UI thread.
 */
final class MenuWalker {

    private final Menu menuBar;
    private final List<Menu> shownMenus = new ArrayList<>();

    /** A walker over this menu bar. */
    MenuWalker(Menu menuBar) {
        this.menuBar = menuBar;
    }

    /** The item at the label path, showing each submenu on the way. */
    MenuItem find(List<String> path) {
        if (path.isEmpty()) {
            throw new ToolException("Give a menu path such as \"File > New > Project...\"");
        }
        Menu menu = menuBar;
        MenuItem item = null;
        for (int level = 0; level < path.size(); level++) {
            if (item != null) {
                menu = submenuOf(item);
            }
            item = itemAt(menu, path, level);
        }
        return item;
    }

    /** The items of the submenu at the path, or of the menu bar when the path is empty, to the given depth. */
    JsonArray list(List<String> path, int depth) {
        Menu menu = menuBar;
        if (!path.isEmpty()) {
            menu = submenuOf(find(path));
        }
        return describeItems(menu, depth);
    }

    /** Sends SWT.Hide to the menus this walker showed, innermost first. */
    void hideShownMenus() {
        for (int i = shownMenus.size() - 1; i >= 0; i--) {
            Menu menu = shownMenus.get(i);
            if (!menu.isDisposed()) {
                menu.notifyListeners(SWT.Hide, new Event());
            }
        }
        shownMenus.clear();
    }

    /** The item's submenu after showing it, so its items exist. */
    private Menu submenuOf(MenuItem item) {
        Menu submenu = item.getMenu();
        if (submenu == null) {
            throw new ToolException("\"" + Labels.plain(item.getText()) + "\" is an item, not a menu");
        }
        submenu.notifyListeners(SWT.Show, new Event());
        shownMenus.add(submenu);
        return submenu;
    }

    /** The item of the menu whose label matches the path at this level. */
    private static MenuItem itemAt(Menu menu, List<String> path, int level) {
        List<MenuItem> items = labelledItems(menu);
        MenuItem item = Labels.best(items, MenuItem::getText, path.get(level));
        if (item == null) {
            String where = level == 0 ? " in the menu bar" : " under \"" + String.join(" > ", path.subList(0, level)) + "\"";
            throw new ToolException("No menu item \"" + path.get(level) + "\"" + where + ". Items: "
                    + Labels.list(items, MenuItem::getText));
        }
        return item;
    }

    /** The menu's items as JSON, with submenus to the given depth. */
    private JsonArray describeItems(Menu menu, int depth) {
        JsonArray array = new JsonArray();
        for (MenuItem item : labelledItems(menu)) {
            array.add(describeItem(item, depth));
        }
        return array;
    }

    /** One item: label, shortcut, enabled and checked state, and its submenu. */
    private JsonObject describeItem(MenuItem item, int depth) {
        JsonObject json = new JsonObject();
        json.addProperty("label", Labels.plain(item.getText()));
        String accelerator = Labels.accelerator(item.getText());
        if (accelerator != null && !accelerator.isEmpty()) {
            json.addProperty("accelerator", accelerator);
        }
        if (!item.isEnabled()) {
            json.addProperty("enabled", false);
        }
        if ((item.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
            json.addProperty("checked", item.getSelection());
        }
        if (item.getMenu() != null && depth > 1) {
            json.add("items", describeItems(submenuOf(item), depth - 1));
        } else if (item.getMenu() != null) {
            json.addProperty("submenu", true);
        }
        return json;
    }

    /** The items that a person can see: separators and empty labels are skipped. */
    private static List<MenuItem> labelledItems(Menu menu) {
        List<MenuItem> items = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if ((item.getStyle() & SWT.SEPARATOR) == 0 && !Labels.plain(item.getText()).isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }
}
