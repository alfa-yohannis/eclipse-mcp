package io.github.alfayohannis.eclipse.mcp.internal;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * The result of a synthetic input tool. Posted input is processed later by the
 * UI thread, so the report waits a moment and then lists the shells, which
 * shows whether the input opened or closed a dialog.
 */
final class InputReport {

    /** Not instantiable: the class only has static members. */
    private InputReport() {
    }

    /** Waits waitMs, then reports the action and the open shells. */
    static ToolResult after(String action, int waitMs) throws Exception {
        Ui.pause(Math.max(0, waitMs));
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        json.add("shells", Ui.call(Shells::describeAll));
        return ToolResult.json(json);
    }
}
