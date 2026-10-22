package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.internal.ui.palette.editparts.DrawerEditPart;
import org.eclipse.gef.palette.PaletteContainer;
import org.eclipse.gef.palette.PaletteEntry;
import org.eclipse.gef.palette.ToolEntry;
import org.eclipse.gef.ui.palette.PaletteViewer;
import org.eclipse.swt.graphics.Point;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.internal.Labels;

/**
 * Finds a palette tool on the screen, so mouse mode can click it as a person
 * would: it opens the tool's drawer, scrolls the tool into view and returns the
 * centre of its figure. UI thread only.
 */
final class PaletteScreen {

    /** Not instantiable: the class only has static members. */
    private PaletteScreen() {
    }

    /** The screen point at the centre of the palette tool with this label. */
    static Point toolCenter(OpenDiagram diagram, String label) {
        PaletteViewer viewer = diagram.editor().getEditDomain().getPaletteViewer();
        if (viewer == null || viewer.getControl() == null || !viewer.getControl().isVisible()) {
            throw new ToolException("The palette is not shown; open it (the arrow at the right edge of the editor) or"
                    + " use mode=feature");
        }
        ToolEntry entry = findEntry(viewer, label);
        EditPart part = (EditPart) viewer.getEditPartRegistry().get(entry);
        openDrawers(viewer, entry);
        viewer.reveal(part);
        layOut(viewer);
        IFigure figure = ((GraphicalEditPart) part).getFigure();
        Rectangle bounds = figure.getBounds().getCopy();
        figure.translateToAbsolute(bounds);
        return viewer.getControl().toDisplay(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /** The GEF tool entry whose label matches. */
    private static ToolEntry findEntry(PaletteViewer viewer, String label) {
        List<ToolEntry> entries = new ArrayList<>();
        collectToolEntries(viewer.getPaletteRoot(), entries);
        ToolEntry entry = Labels.best(entries, ToolEntry::getLabel, label);
        if (entry == null) {
            throw new ToolException("No palette tool \"" + label + "\"; tools: " + Labels.list(entries, ToolEntry::getLabel));
        }
        return entry;
    }

    /** Adds the visible tool entries below the container. */
    private static void collectToolEntries(PaletteContainer container, List<ToolEntry> entries) {
        for (Object child : container.getChildren()) {
            if (child instanceof ToolEntry && ((ToolEntry) child).isVisible()) {
                entries.add((ToolEntry) child);
            } else if (child instanceof PaletteContainer) {
                collectToolEntries((PaletteContainer) child, entries);
            }
        }
    }

    /** Expands the drawers that contain the entry, then lays the palette out so the entry's figure has its final bounds. */
    private static void openDrawers(PaletteViewer viewer, PaletteEntry entry) {
        for (PaletteContainer container = entry.getParent(); container != null; container = container.getParent()) {
            Object part = viewer.getEditPartRegistry().get(container);
            if (part instanceof DrawerEditPart && !((DrawerEditPart) part).isExpanded()) {
                ((DrawerEditPart) part).setExpanded(true);
            }
        }
        layOut(viewer);
    }

    /** Runs draw2d's pending layout and painting now instead of on the next event loop pass. */
    private static void layOut(PaletteViewer viewer) {
        viewer.flush();
        if (viewer.getControl() instanceof FigureCanvas) {
            ((FigureCanvas) viewer.getControl()).getViewport().getUpdateManager().performUpdate();
        }
    }
}
