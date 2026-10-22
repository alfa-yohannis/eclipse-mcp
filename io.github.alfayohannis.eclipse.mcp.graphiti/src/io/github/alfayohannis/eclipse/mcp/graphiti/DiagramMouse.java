package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.gef.GraphicalViewer;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.internal.KeyCombo;
import io.github.alfayohannis.eclipse.mcp.internal.Shells;
import io.github.alfayohannis.eclipse.mcp.internal.SyntheticInput;
import io.github.alfayohannis.eclipse.mcp.internal.Ui;

/**
 * Mouse mode: changes a diagram with real input, clicking the palette and the
 * canvas and dragging shapes as a person does, so the work is visible in
 * screenshots and demos. Runs on a server thread; the positions are computed
 * on the UI thread just before each gesture, after any scrolling.
 */
final class DiagramMouse {

    private static final int LEFT_BUTTON = 1;
    private static final int GESTURE_PAUSE_MS = 400;
    private static final int DRAG_STEPS = 15;

    private final String editorSelector;
    private final SyntheticInput input;

    /** Mouse input for the diagram editor the selector names. */
    DiagramMouse(String editorSelector) {
        this.editorSelector = editorSelector;
        this.input = new SyntheticInput(Ui.display());
    }

    /** Clicks the shape tool in the palette, then the canvas at x,y, and leaves the name editor that may open. */
    String create(String toolLabel, int x, int y) throws Exception {
        click(Ui.call(() -> paletteTool(toolLabel, false)));
        click(Ui.call(() -> canvasPoint(x, y)));
        pressEscape();
        return "Clicked the palette tool " + toolLabel + ", then the canvas at " + x + "," + y;
    }

    /** Clicks the connection tool, then the source and the target, as GEF's click-click connection gesture. */
    String connect(String toolLabel, String sourceId, String targetId) throws Exception {
        click(Ui.call(() -> paletteTool(toolLabel, true)));
        click(Ui.call(() -> shapeCenter(sourceId)));
        click(Ui.call(() -> shapeCenter(targetId)));
        pressEscape();
        return "Clicked the palette tool " + toolLabel + ", then " + sourceId + " and " + targetId;
    }

    /** Drags the shape from its centre so that its top-left corner lands at x,y. */
    String move(String id, int x, int y) throws Exception {
        Point[] points = Ui.call(() -> dragPoints(id, x, y));
        input.drag(points[0], points[1], LEFT_BUTTON, DRAG_STEPS, 0);
        Ui.pause(GESTURE_PAUSE_MS);
        return "Dragged " + id + " to " + x + "," + y;
    }

    /** One left click and a pause for the editor to react. */
    private void click(Point at) {
        input.click(at, LEFT_BUTTON, 1, 0);
        Ui.pause(GESTURE_PAUSE_MS);
    }

    /** Escape ends a direct-edit name field or a tool that stays active; the shell gets the keys first. */
    private void pressEscape() throws Exception {
        Ui.run(this::focusWorkbench);
        input.press(KeyCombo.parse("esc"));
        Ui.pause(GESTURE_PAUSE_MS);
    }

    /** Gives the workbench window the keyboard focus. UI thread only. */
    private void focusWorkbench() {
        Shells.activate(Ui.window().getShell());
    }

    /** The screen centre of the palette tool, with the editor in front. UI thread only. */
    private Point paletteTool(String label, boolean connection) {
        OpenDiagram diagram = OpenDiagram.find(editorSelector);
        diagram.activate();
        String exactLabel = PaletteCatalog.of(diagram).exactLabel(label, connection);
        return PaletteScreen.toolCenter(diagram, exactLabel);
    }

    /** The screen point of a diagram point. UI thread only. */
    private Point canvasPoint(int x, int y) {
        return DiagramGeometry.screenPoint(OpenDiagram.find(editorSelector), x, y);
    }

    /** The screen centre of the shape with the id. UI thread only. */
    private Point shapeCenter(String id) {
        OpenDiagram diagram = OpenDiagram.find(editorSelector);
        return DiagramGeometry.shapeCenter(diagram, ElementIndex.of(diagram.diagram()).shape(id));
    }

    /**
     * The start and end of the drag: the shape's centre now and the centre it
     * should have. The end is computed first, since reaching it may scroll.
     * UI thread only.
     */
    private Point[] dragPoints(String id, int x, int y) {
        OpenDiagram diagram = OpenDiagram.find(editorSelector);
        diagram.activate();
        Shape shape = ElementIndex.of(diagram.diagram()).shape(id);
        int[] bounds = ElementIndex.bounds(shape);
        Point end = DiagramGeometry.screenPoint(diagram, x + bounds[2] / 2, y + bounds[3] / 2);
        Point start = DiagramGeometry.shapeCenter(diagram, shape);
        requireOnCanvas(diagram.viewer(), start, id);
        return new Point[] { start, end };
    }

    /** Fails when the start of a gesture is outside the visible canvas, where the press would miss the shape. */
    private static void requireOnCanvas(GraphicalViewer viewer, Point screenPoint, String id) {
        Control canvas = viewer.getControl();
        Point origin = canvas.toDisplay(0, 0);
        Rectangle area = DiagramGeometry.visibleArea(canvas);
        Rectangle onScreen = new Rectangle(origin.x, origin.y, area.width, area.height);
        if (!onScreen.contains(screenPoint)) {
            throw new ToolException(id + " and its target do not fit in the visible canvas together; move it in"
                    + " smaller steps or use mode=feature");
        }
    }
}
