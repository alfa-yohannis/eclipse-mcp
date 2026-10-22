package io.github.alfayohannis.eclipse.mcp.api;

/**
 * An expected failure of a tool, such as a dialog that is not open. The
 * message goes back to the caller as the tool's error result, so it should
 * say what to do differently.
 */
public class ToolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** A failure described by its message alone. */
    public ToolException(String message) {
        super(message);
    }

    /** A failure caused by another exception, which stays available for logging. */
    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
