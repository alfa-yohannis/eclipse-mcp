package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;

import org.eclipse.swt.graphics.Point;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools that click and drag with the real mouse, for canvases such as diagram
 * editors and anything the widget tools cannot reach. They need X11.
 */
final class MouseTools {

    private static final String COORDINATE_SHELL = "Shell index or title that the coordinates refer to; the innermost"
            + " modal dialog, else the active window, when omitted";
    private static final String COORDINATE_WIDGET = ArgumentTexts.WIDGET + "; coordinates are then relative to it and"
            + " default to its centre";

    /** Not instantiable: the class only has static members. */
    private MouseTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(clickTool(), dragTool());
    }

    /** Defines mouse_click, a real click at a point of a shell or widget. */
    private static Tool clickTool() {
        return Tool.builder("mouse_click").title("Mouse click")
                .description("Moves the mouse and clicks at a point relative to the top-left corner of a shell (as in"
                        + " its screenshot at scale 1) or of a widget. count=2 double-clicks. If the click opens a"
                        + " dialog it stays open; the result lists the shells.")
                .input(Schema.object().integer("x", "Horizontal position").integer("y", "Vertical position")
                        .string("shell", COORDINATE_SHELL).integer("widget", COORDINATE_WIDGET)
                        .choice("button", "Mouse button (default left)", "left", "middle", "right")
                        .integer("count", "Number of clicks (default 1)")
                        .string("modifiers", "Keys held during the click, e.g. ctrl or ctrl+shift")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 500)"))
                .handler(MouseTools::click).build();
    }

    /** Defines mouse_drag, a real press-move-release, as drag and drop needs. */
    private static Tool dragTool() {
        return Tool.builder("mouse_drag").title("Mouse drag")
                .description("Presses the mouse at one point, moves in steps to another and releases it there, e.g. to"
                        + " move a shape on a diagram. Points are relative to the shell or widget, like mouse_click.")
                .input(Schema.object().integer("from_x", "Start x").integer("from_y", "Start y")
                        .integer("to_x", "End x").integer("to_y", "End y")
                        .string("shell", COORDINATE_SHELL).integer("widget", COORDINATE_WIDGET)
                        .choice("button", "Mouse button (default left)", "left", "middle", "right")
                        .integer("steps", "Intermediate moves (default 12)")
                        .string("modifiers", "Keys held during the drag, e.g. ctrl")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 500)")
                        .required("from_x", "from_y", "to_x", "to_y"))
                .handler(MouseTools::drag).build();
    }

    /** Clicks at the resolved screen point. */
    private static ToolResult click(Args args) throws Exception {
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        Integer x = args.integer("x");
        Integer y = args.integer("y");
        Point at = Ui.call(() -> ScreenPoints.resolve(selector, widget, x, y));
        new SyntheticInput(Ui.display()).click(at, SyntheticInput.button(args.string("button")),
                Math.max(1, args.integer("count", 1)), KeyCombo.modifiers(args.string("modifiers")));
        return InputReport.after("click at " + at.x + "," + at.y + " on the screen", args.integer("wait_ms", 500));
    }

    /** Drags between the two resolved screen points. */
    private static ToolResult drag(Args args) throws Exception {
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        int fromX = args.requiredInteger("from_x");
        int fromY = args.requiredInteger("from_y");
        int toX = args.requiredInteger("to_x");
        int toY = args.requiredInteger("to_y");
        Point from = Ui.call(() -> ScreenPoints.resolve(selector, widget, fromX, fromY));
        Point to = Ui.call(() -> ScreenPoints.resolve(selector, widget, toX, toY));
        new SyntheticInput(Ui.display()).drag(from, to, SyntheticInput.button(args.string("button")),
                args.integer("steps", 12), KeyCombo.modifiers(args.string("modifiers")));
        return InputReport.after("drag from " + from.x + "," + from.y + " to " + to.x + "," + to.y + " on the screen",
                args.integer("wait_ms", 500));
    }
}
