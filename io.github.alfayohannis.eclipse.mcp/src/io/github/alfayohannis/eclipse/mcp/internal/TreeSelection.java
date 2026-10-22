package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.widgets.Item;

/** A tree or table item that a tool selected, kept with its walker so it can be opened afterwards. */
final class TreeSelection {

    private final TreeWalker walker;
    private final Item item;

    /** The selected item and the walker over its tree or table. */
    TreeSelection(TreeWalker walker, Item item) {
        this.walker = walker;
        this.item = item;
    }

    /** Double-clicks the item and names it. UI thread only. */
    String open() {
        walker.open(item);
        return "Opened " + item.getText();
    }

    /** The item's label. UI thread only. */
    String label() {
        return item.getText();
    }
}
