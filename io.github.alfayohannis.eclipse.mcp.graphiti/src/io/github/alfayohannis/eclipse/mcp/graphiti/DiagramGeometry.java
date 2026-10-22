package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Scrollable;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.internal.Widgets;

/**
 * Converts diagram coordinates to screen coordinates for mouse input. The
 * draw2d figures apply the zoom and the scroll position, so the conversion
 * goes through them. UI thread only.
 */
final class DiagramGeometry {

    /** Room kept between a target point and the canvas edge when scrolling to it. */
    private static final int SCROLL_MARGIN = 40;

    /** Not instantiable: the class only has static members. */
    private DiagramGeometry() {
    }

    /** The screen point of a diagram point, scrolling the canvas first when the point is outside the view. */
    static org.eclipse.swt.graphics.Point screenPoint(OpenDiagram diagram, int x, int y) {
        GraphicalViewer viewer = diagram.viewer();
        org.eclipse.swt.graphics.Point onCanvas = canvasPoint(viewer, x, y);
        Control control = viewer.getControl();
        if (!visibleArea(control).contains(onCanvas)) {
            scrollTowards(control, onCanvas);
            onCanvas = canvasPoint(viewer, x, y);
        }
        return control.toDisplay(onCanvas);
    }

    /** The screen point of a shape's centre, where a click or drag on it should start. */
    static org.eclipse.swt.graphics.Point shapeCenter(OpenDiagram diagram, Shape shape) {
        int[] bounds = ElementIndex.bounds(shape);
        return screenPoint(diagram, bounds[0] + bounds[2] / 2, bounds[1] + bounds[3] / 2);
    }

    /** The screen bounds of the diagram canvas, for screenshots. */
    static JsonObject canvasScreenBounds(OpenDiagram diagram) {
        Control control = diagram.viewer().getControl();
        org.eclipse.swt.graphics.Point origin = control.toDisplay(0, 0);
        org.eclipse.swt.graphics.Point size = control.getSize();
        return Widgets.bounds(new org.eclipse.swt.graphics.Rectangle(origin.x, origin.y, size.x, size.y));
    }

    /** The part of the control that shows content, in its own coordinates; scroll bars are left out. */
    static org.eclipse.swt.graphics.Rectangle visibleArea(Control control) {
        if (control instanceof Scrollable) {
            return ((Scrollable) control).getClientArea();
        }
        org.eclipse.swt.graphics.Point size = control.getSize();
        return new org.eclipse.swt.graphics.Rectangle(0, 0, size.x, size.y);
    }

    /** The point on the canvas control of a diagram point: zoom and scroll applied by the figures. */
    private static org.eclipse.swt.graphics.Point canvasPoint(GraphicalViewer viewer, int x, int y) {
        IFigure contents = ((GraphicalEditPart) viewer.getContents()).getFigure();
        Point point = new Point(x, y);
        contents.translateToAbsolute(point);
        return new org.eclipse.swt.graphics.Point(point.x, point.y);
    }

    /** Scrolls the canvas so the point comes into view, with a margin. */
    private static void scrollTowards(Control control, org.eclipse.swt.graphics.Point onCanvas) {
        if (!(control instanceof FigureCanvas)) {
            throw new ToolException("The point is outside the visible part of the diagram, and the canvas cannot scroll");
        }
        FigureCanvas canvas = (FigureCanvas) control;
        Rectangle view = canvas.getViewport().getClientArea();
        org.eclipse.swt.graphics.Rectangle area = visibleArea(control);
        int scrollX = view.x + onCanvas.x - Math.min(Math.max(onCanvas.x, SCROLL_MARGIN), area.width - SCROLL_MARGIN);
        int scrollY = view.y + onCanvas.y - Math.min(Math.max(onCanvas.y, SCROLL_MARGIN), area.height - SCROLL_MARGIN);
        canvas.scrollTo(Math.max(0, scrollX), Math.max(0, scrollY));
        canvas.getViewport().getUpdateManager().performUpdate();
    }
}
