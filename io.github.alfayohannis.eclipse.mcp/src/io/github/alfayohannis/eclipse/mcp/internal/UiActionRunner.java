package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Runs a UI action that may open a modal dialog, such as a menu item or a
 * button. A modal dialog blocks its action until it closes, so the runner waits
 * only a while and then reports the open shells, letting the caller fill and
 * close the dialog with other tools.
 */
public final class UiActionRunner {

    /** Handlers often open their dialogs with asyncExec, so a finished action gets this long for them to appear. */
    private static final int SETTLE_MS = 250;

    private static final String STILL_RUNNING = "Still running, most likely because it opened a modal dialog."
            + " The dialog is among the shells below; read it with list_shells (widgets=true), fill it with"
            + " set_text or select_tree_item and close it with press_button.";

    /** Not instantiable: the class only has static members. */
    private UiActionRunner() {
    }

    /**
     * Starts the action on the UI thread, waits up to waitMs and reports
     * whether it finished, its result, and the open shells. A failure within
     * the wait is thrown; a later one is logged, since the caller has gone.
     */
    public static ToolResult runAndReport(String action, Callable<String> task, int waitMs) throws Exception {
        CompletableFuture<String> future = Ui.start(task);
        boolean finished = Ui.await(future, waitMs);
        if (!finished) {
            future.whenComplete((value, failure) -> logLateFailure(action, failure));
        }
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        json.addProperty("finished", finished);
        if (finished) {
            addResult(json, future.getNow(null));
            Ui.pause(Math.min(Math.max(waitMs, 0), SETTLE_MS));
        } else {
            json.addProperty("note", STILL_RUNNING);
        }
        json.add("shells", Ui.call(Shells::describeAll));
        return ToolResult.json(json);
    }

    /** Adds the action's own result text, when it has one. */
    private static void addResult(JsonObject json, String value) {
        if (value != null) {
            json.addProperty("result", value);
        }
    }

    /** Logs a failure that happened after the tool had already answered. */
    private static void logLateFailure(String action, Throwable failure) {
        if (failure != null) {
            Log.error(action + " failed after the MCP call returned", failure);
        }
    }
}
