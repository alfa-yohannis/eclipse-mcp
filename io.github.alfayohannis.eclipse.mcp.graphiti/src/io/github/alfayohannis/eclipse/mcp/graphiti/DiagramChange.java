package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.List;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolResult;
import io.github.alfayohannis.eclipse.mcp.internal.Shells;
import io.github.alfayohannis.eclipse.mcp.internal.Ui;

/**
 * One change of a diagram from the caller's point of view: a snapshot before,
 * the work, and a report of the elements it created, changed and removed, so
 * the caller learns new ids without a second request.
 */
final class DiagramChange {

    /** Gives the editor time to finish the updates that follow a command, such as label layout. */
    private static final int SETTLE_MS = 150;

    private final String editorSelector;
    private final DiagramSnapshot before;

    /** Use {@link #begin(String)}. */
    private DiagramChange(String editorSelector, DiagramSnapshot before) {
        this.editorSelector = editorSelector;
        this.before = before;
    }

    /** Takes the snapshot before the change. */
    static DiagramChange begin(String editorSelector) throws Exception {
        return new DiagramChange(editorSelector, Ui.call(() -> DiagramSnapshot.take(editorSelector)));
    }

    /** The ids created since the change began, for follow-up steps such as naming a new element. */
    List<String> createdSoFar() throws Exception {
        Ui.pause(SETTLE_MS);
        return before.createdIn(Ui.call(() -> DiagramSnapshot.take(editorSelector)));
    }

    /**
     * The report: action, outcome, created/changed/removed elements, the dirty
     * flag, and the shells when a dialog is open, since it may need an answer.
     */
    ToolResult report(String action, String outcome) throws Exception {
        Ui.pause(SETTLE_MS);
        JsonObject json = Ui.call(this::differences);
        json.addProperty("action", action);
        json.addProperty("result", outcome);
        return ToolResult.json(json);
    }

    /** The differences, dirty flag and open dialogs. UI thread only. */
    private JsonObject differences() {
        JsonObject json = before.differencesTo(DiagramSnapshot.take(editorSelector));
        json.addProperty("dirty", OpenDiagram.find(editorSelector).isDirty());
        if (Shells.visible().size() > 1) {
            json.add("shells", Shells.describeAll());
        }
        return json;
    }
}
