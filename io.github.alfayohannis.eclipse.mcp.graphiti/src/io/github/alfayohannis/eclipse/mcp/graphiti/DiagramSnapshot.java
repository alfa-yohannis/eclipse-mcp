package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The elements of a diagram at one moment, so a change can be reported as the
 * elements it created, changed and removed. UI thread only for taking it.
 */
final class DiagramSnapshot {

    private final Map<String, JsonObject> elements;

    /** A snapshot of these element descriptions by id. */
    private DiagramSnapshot(Map<String, JsonObject> elements) {
        this.elements = elements;
    }

    /** The elements of the diagram the selector names now. */
    static DiagramSnapshot take(String editorSelector) {
        return new DiagramSnapshot(ElementIndex.of(OpenDiagram.find(editorSelector).diagram()).describeAll());
    }

    /** The ids that exist in the later snapshot but not in this one. */
    List<String> createdIn(DiagramSnapshot later) {
        List<String> created = new ArrayList<>();
        for (String id : later.elements.keySet()) {
            if (!elements.containsKey(id)) {
                created.add(id);
            }
        }
        return created;
    }

    /** created, changed and removed elements between this snapshot and the later one. */
    JsonObject differencesTo(DiagramSnapshot later) {
        JsonArray created = new JsonArray();
        JsonArray changed = new JsonArray();
        for (Map.Entry<String, JsonObject> entry : later.elements.entrySet()) {
            JsonObject before = elements.get(entry.getKey());
            if (before == null) {
                created.add(entry.getValue());
            } else if (!before.equals(entry.getValue())) {
                changed.add(entry.getValue());
            }
        }
        JsonArray removed = new JsonArray();
        for (String id : elements.keySet()) {
            if (!later.elements.containsKey(id)) {
                removed.add(id);
            }
        }
        JsonObject json = new JsonObject();
        json.add("created", created);
        json.add("changed", changed);
        json.add("removed", removed);
        return json;
    }
}
