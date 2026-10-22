package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.palette.IConnectionCreationToolEntry;
import org.eclipse.graphiti.palette.ICreationToolEntry;
import org.eclipse.graphiti.palette.IObjectCreationToolEntry;
import org.eclipse.graphiti.palette.IPaletteCompartmentEntry;
import org.eclipse.graphiti.palette.IStackToolEntry;
import org.eclipse.graphiti.palette.IToolEntry;

import com.google.gson.JsonArray;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.internal.Labels;

/**
 * The creation tools of a diagram's palette, flattened from its drawers and
 * stacks, so tools can name them by the labels people see. UI thread only.
 */
final class PaletteCatalog {

    private final List<PaletteTool> tools = new ArrayList<>();

    /** Use {@link #of(OpenDiagram)}. */
    private PaletteCatalog() {
    }

    /** The palette the editor's tool behavior provider offers right now. */
    static PaletteCatalog of(OpenDiagram diagram) {
        PaletteCatalog catalog = new PaletteCatalog();
        IPaletteCompartmentEntry[] drawers = diagram.editor().getDiagramTypeProvider().getCurrentToolBehaviorProvider()
                .getPalette();
        for (IPaletteCompartmentEntry drawer : drawers) {
            for (IToolEntry entry : drawer.getToolEntries()) {
                catalog.add(drawer.getLabel(), entry);
            }
        }
        return catalog;
    }

    /** All tools as JSON. */
    JsonArray describe() {
        JsonArray array = new JsonArray();
        for (PaletteTool tool : tools) {
            array.add(tool.describe());
        }
        return array;
    }

    /** The create feature of the shape tool with this label. */
    ICreateFeature createFeature(String label) {
        PaletteTool tool = find(label, PaletteTool::createsShapes, "shape");
        return ((IObjectCreationToolEntry) tool.entry()).getCreateFeature();
    }

    /** The connection tool with this label. */
    IConnectionCreationToolEntry connectionEntry(String label) {
        return (IConnectionCreationToolEntry) find(label, PaletteTool::createsConnections, "connection").entry();
    }

    /** The label of the tool that matches, as the palette shows it, for clicking it. */
    String exactLabel(String label, boolean connection) {
        return connection ? find(label, PaletteTool::createsConnections, "connection").label()
                : find(label, PaletteTool::createsShapes, "shape").label();
    }

    /** The tool of the kind whose label matches best; fails with the labels of that kind. */
    private PaletteTool find(String label, Predicate<PaletteTool> kind, String kindName) {
        List<PaletteTool> candidates = new ArrayList<>();
        for (PaletteTool tool : tools) {
            if (kind.test(tool)) {
                candidates.add(tool);
            }
        }
        PaletteTool tool = Labels.best(candidates, PaletteTool::label, label);
        if (tool == null) {
            throw new ToolException("No " + kindName + " tool \"" + label + "\" in the palette. " + kindName
                    + " tools: " + Labels.list(candidates, PaletteTool::label));
        }
        return tool;
    }

    /** Adds an entry; a stack adds each of its creation tools. */
    private void add(String drawer, IToolEntry entry) {
        if (entry instanceof IStackToolEntry) {
            for (ICreationToolEntry stacked : ((IStackToolEntry) entry).getCreationToolEntries()) {
                tools.add(new PaletteTool(stacked.getLabel(), drawer, stacked));
            }
        } else {
            tools.add(new PaletteTool(entry.getLabel(), drawer, entry));
        }
    }
}
