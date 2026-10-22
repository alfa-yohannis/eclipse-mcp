package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Describes the widgets of a shell for list_shells: type, text, state and
 * bounds. Each widget type has its own small describer for the state a person
 * would notice, such as the checked state of a check box. UI thread only.
 */
final class WidgetDescriber {

    private static final int MAX_ITEMS = 30;
    private static final int MAX_SELECTION = 5;

    /** Describers of widget state, checked in order; subclasses come before their superclasses. */
    private static final Map<Class<? extends Control>, BiConsumer<Control, JsonObject>> STATE_DESCRIBERS = stateDescribers();

    /** Not instantiable: the class only has static members. */
    private WidgetDescriber() {
    }

    /**
     * The widgets of the shell: those a person interacts with or reads, or only
     * those of the given lower-case SWT type names.
     */
    static JsonArray describe(Shell shell, Set<String> types, int limit) {
        JsonArray array = new JsonArray();
        List<Control> controls = Widgets.controls(shell);
        Control focus = Ui.display().getFocusControl();
        for (int index = 0; index < controls.size() && array.size() < limit; index++) {
            Control control = controls.get(index);
            if (isWanted(control, types)) {
                array.add(describe(shell, control, index, focus));
            }
        }
        return array;
    }

    /** Whether the control belongs in the list. */
    private static boolean isWanted(Control control, Set<String> types) {
        if (types.isEmpty()) {
            return isInteresting(control);
        }
        return types.contains(control.getClass().getSimpleName().toLowerCase(Locale.ROOT));
    }

    /** Whether a person would interact with or read the control; layout composites are left out. */
    private static boolean isInteresting(Control control) {
        if (hasStateDescriber(control)) {
            return true;
        }
        if (control instanceof Label || control instanceof CLabel || control instanceof Link
                || control instanceof Group || control instanceof Scale || control instanceof DateTime
                || control instanceof Browser || control instanceof Spinner) {
            return true;
        }
        // A canvas without children is a drawing surface, such as a diagram editor.
        return control instanceof Canvas && ((Canvas) control).getChildren().length == 0;
    }

    /** Whether one of the state describers handles the control's type. */
    private static boolean hasStateDescriber(Control control) {
        for (Class<? extends Control> type : STATE_DESCRIBERS.keySet()) {
            if (type.isInstance(control)) {
                return true;
            }
        }
        return false;
    }

    /** One widget's description. */
    private static JsonObject describe(Shell shell, Control control, int index, Control focus) {
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("type", control.getClass().getSimpleName());
        addText(json, "text", Widgets.text(control));
        addText(json, "tooltip", control.getToolTipText());
        if (!control.isEnabled()) {
            json.addProperty("enabled", false);
        }
        if (control == focus) {
            json.addProperty("focused", true);
        }
        describeState(control, json);
        addText(json, "part", Widgets.part(control));
        json.add("bounds", Widgets.relativeBounds(shell, control));
        return json;
    }

    /** Adds a non-empty text property. */
    private static void addText(JsonObject json, String name, String text) {
        if (text != null && !text.isEmpty()) {
            json.addProperty(name, text);
        }
    }

    /** Runs the first describer whose type matches the control. */
    private static void describeState(Control control, JsonObject json) {
        for (Map.Entry<Class<? extends Control>, BiConsumer<Control, JsonObject>> entry : STATE_DESCRIBERS.entrySet()) {
            if (entry.getKey().isInstance(control)) {
                entry.getValue().accept(control, json);
                return;
            }
        }
    }

    /** Builds the ordered table of state describers. */
    private static Map<Class<? extends Control>, BiConsumer<Control, JsonObject>> stateDescribers() {
        Map<Class<? extends Control>, BiConsumer<Control, JsonObject>> describers = new LinkedHashMap<>();
        describers.put(Button.class, WidgetDescriber::describeButton);
        describers.put(Text.class, WidgetDescriber::describeText);
        describers.put(StyledText.class, WidgetDescriber::describeStyledText);
        describers.put(Combo.class, WidgetDescriber::describeCombo);
        describers.put(CCombo.class, WidgetDescriber::describeCCombo);
        describers.put(org.eclipse.swt.widgets.List.class, WidgetDescriber::describeList);
        describers.put(Tree.class, WidgetDescriber::describeTree);
        describers.put(Table.class, WidgetDescriber::describeTable);
        describers.put(CTabFolder.class, WidgetDescriber::describeCTabFolder);
        describers.put(TabFolder.class, WidgetDescriber::describeTabFolder);
        describers.put(ToolBar.class, WidgetDescriber::describeToolBar);
        describers.put(ProgressBar.class, WidgetDescriber::describeProgressBar);
        return describers;
    }

    /** Push, check, radio, toggle or arrow, and whether a check box or radio button is selected. */
    private static void describeButton(Control control, JsonObject json) {
        Button button = (Button) control;
        int style = button.getStyle();
        if ((style & (SWT.CHECK | SWT.RADIO | SWT.TOGGLE)) != 0) {
            json.addProperty("selected", button.getSelection());
        }
        json.addProperty("style", Buttons.styleName(style));
    }

    /** Whether the field is read-only, and its hint text. */
    private static void describeText(Control control, JsonObject json) {
        Text text = (Text) control;
        if (!text.getEditable()) {
            json.addProperty("editable", false);
        }
        addText(json, "hint", text.getMessage());
    }

    /** Whether the styled text, such as an editor, is read-only. */
    private static void describeStyledText(Control control, JsonObject json) {
        if (!((StyledText) control).getEditable()) {
            json.addProperty("editable", false);
        }
    }

    /** The items of the combo and whether it only allows them. */
    private static void describeCombo(Control control, JsonObject json) {
        Combo combo = (Combo) control;
        json.add("items", firstItems(combo.getItems()));
        if ((combo.getStyle() & SWT.READ_ONLY) != 0) {
            json.addProperty("editable", false);
        }
    }

    /** The items of the custom combo. */
    private static void describeCCombo(Control control, JsonObject json) {
        json.add("items", firstItems(((CCombo) control).getItems()));
    }

    /** The items of the list and the selected ones. */
    private static void describeList(Control control, JsonObject json) {
        org.eclipse.swt.widgets.List list = (org.eclipse.swt.widgets.List) control;
        json.add("items", firstItems(list.getItems()));
        json.add("selection", firstItems(list.getSelection()));
    }

    /** How many top-level items the tree has, and which are selected. */
    private static void describeTree(Control control, JsonObject json) {
        Tree tree = (Tree) control;
        json.addProperty("itemCount", tree.getItemCount());
        JsonArray selection = new JsonArray();
        for (TreeItem item : tree.getSelection()) {
            addLimited(selection, item.getText(), MAX_SELECTION);
        }
        json.add("selection", selection);
    }

    /** How many rows the table has, its column headers, and the selected rows. */
    private static void describeTable(Control control, JsonObject json) {
        Table table = (Table) control;
        json.addProperty("itemCount", table.getItemCount());
        JsonArray columns = new JsonArray();
        for (TableColumn column : table.getColumns()) {
            columns.add(column.getText());
        }
        if (columns.size() > 0) {
            json.add("columns", columns);
        }
        JsonArray selection = new JsonArray();
        for (TableItem item : table.getSelection()) {
            addLimited(selection, item.getText(), MAX_SELECTION);
        }
        json.add("selection", selection);
    }

    /** The tab titles of a part stack or dialog folder, and the selected tab. */
    private static void describeCTabFolder(Control control, JsonObject json) {
        CTabFolder folder = (CTabFolder) control;
        JsonArray tabs = new JsonArray();
        for (CTabItem item : folder.getItems()) {
            tabs.add(Labels.plain(item.getText()));
        }
        json.add("tabs", tabs);
        if (folder.getSelection() != null) {
            json.addProperty("selectedTab", Labels.plain(folder.getSelection().getText()));
        }
    }

    /** The tab titles of a native tab folder, and the selected tab. */
    private static void describeTabFolder(Control control, JsonObject json) {
        TabFolder folder = (TabFolder) control;
        JsonArray tabs = new JsonArray();
        for (TabItem item : folder.getItems()) {
            tabs.add(Labels.plain(item.getText()));
        }
        json.add("tabs", tabs);
        if (folder.getSelectionIndex() >= 0) {
            json.addProperty("selectedTab", Labels.plain(folder.getItem(folder.getSelectionIndex()).getText()));
        }
    }

    /** The labels or tool tips of the tool bar items, marking disabled ones. */
    private static void describeToolBar(Control control, JsonObject json) {
        JsonArray items = new JsonArray();
        for (ToolItem item : ((ToolBar) control).getItems()) {
            String label = Buttons.toolItemLabel(item);
            if (!label.isEmpty()) {
                addLimited(items, Labels.plain(label) + (item.isEnabled() ? "" : " (disabled)"), MAX_ITEMS);
            }
        }
        json.add("items", items);
    }

    /** The progress as value/maximum. */
    private static void describeProgressBar(Control control, JsonObject json) {
        ProgressBar bar = (ProgressBar) control;
        json.addProperty("progress", bar.getSelection() + "/" + bar.getMaximum());
    }

    /** The first items of an array, enough to recognise the widget without flooding the output. */
    private static JsonArray firstItems(String[] values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            addLimited(array, value, MAX_ITEMS);
        }
        return array;
    }

    /** Adds the value while the array is below the limit. */
    private static void addLimited(JsonArray array, String value, int limit) {
        if (array.size() < limit) {
            array.add(value);
        }
    }
}
