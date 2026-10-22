package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.graphiti.palette.IConnectionCreationToolEntry;
import org.eclipse.graphiti.palette.IObjectCreationToolEntry;
import org.eclipse.graphiti.palette.IToolEntry;

import com.google.gson.JsonObject;

/** One creation tool of a diagram palette with the drawer it sits in. */
final class PaletteTool {

    private final String label;
    private final String drawer;
    private final IToolEntry entry;

    /** A palette tool as Graphiti describes it. */
    PaletteTool(String label, String drawer, IToolEntry entry) {
        this.label = label;
        this.drawer = drawer;
        this.entry = entry;
    }

    /** The label shown in the palette, which the tools match against. */
    String label() {
        return label;
    }

    /** Whether the tool creates shapes. */
    boolean createsShapes() {
        return entry instanceof IObjectCreationToolEntry;
    }

    /** Whether the tool creates connections. */
    boolean createsConnections() {
        return entry instanceof IConnectionCreationToolEntry;
    }

    /** The Graphiti entry, for its features. */
    IToolEntry entry() {
        return entry;
    }

    /** Label, drawer and kind as JSON. */
    JsonObject describe() {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        json.addProperty("drawer", drawer);
        json.addProperty("kind", createsShapes() ? "shape" : createsConnections() ? "connection" : "other");
        return json;
    }
}
