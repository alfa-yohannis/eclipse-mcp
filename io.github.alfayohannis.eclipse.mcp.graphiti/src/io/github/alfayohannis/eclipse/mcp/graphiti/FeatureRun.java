package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.concurrent.CompletableFuture;

import io.github.alfayohannis.eclipse.mcp.internal.Ui;

/**
 * Runs a feature-mode action on the UI thread without letting a dialog block
 * the caller: when the editor asks something in a modal dialog, the action
 * keeps waiting for it while the caller gets an answer after wait_ms.
 */
final class FeatureRun {

    /** One action on an open diagram. */
    @FunctionalInterface
    interface DiagramAction {

        /** Changes the diagram and describes what it did. UI thread. */
        String apply(OpenDiagram diagram) throws Exception;
    }

    /** Not instantiable: the class only has static members. */
    private FeatureRun() {
    }

    /** Runs the action on the diagram the selector names and returns its description, or a note about a dialog. */
    static String run(String editorSelector, int waitMs, DiagramAction action) throws Exception {
        CompletableFuture<String> future = Ui.start(() -> applyTo(editorSelector, action));
        if (Ui.await(future, waitMs)) {
            return future.getNow(null);
        }
        return "Still running after " + waitMs + " ms, most likely because the editor opened a dialog; list_shells"
                + " shows it";
    }

    /** Finds the diagram and applies the action. UI thread only. */
    private static String applyTo(String editorSelector, DiagramAction action) throws Exception {
        return action.apply(OpenDiagram.find(editorSelector));
    }
}
