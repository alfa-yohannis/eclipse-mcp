package io.github.alfayohannis.eclipse.mcp.internal;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * A label of a tree path that is not among the items yet. JFace may fill
 * children in the background, so callers retry before reporting it.
 */
final class MissingItemException extends ToolException {

    private static final long serialVersionUID = 1L;

    /** The failure with the labels that do exist at that level. */
    MissingItemException(String message) {
        super(message);
    }
}
