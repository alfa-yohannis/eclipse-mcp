package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * Turns coordinates relative to a shell or widget, the origin of their
 * screenshots, into screen coordinates for synthetic input. UI thread only.
 */
final class ScreenPoints {

    /** Not instantiable: the class only has static members. */
    private ScreenPoints() {
    }

    /**
     * The screen point of x,y relative to the widget when one is given (its
     * centre when x or y is missing), else relative to the shell.
     */
    static Point resolve(String shellSelector, Integer widget, Integer x, Integer y) {
        Shell shell = Shells.find(shellSelector);
        if (widget != null) {
            Control control = Widgets.widget(shell, widget);
            Point size = control.getSize();
            return control.toDisplay(x != null ? x : size.x / 2, y != null ? y : size.y / 2);
        }
        if (x == null || y == null) {
            throw new IllegalArgumentException("Give x and y, or a widget");
        }
        Rectangle bounds = shell.getBounds();
        return new Point(bounds.x + x, bounds.y + y);
    }
}
