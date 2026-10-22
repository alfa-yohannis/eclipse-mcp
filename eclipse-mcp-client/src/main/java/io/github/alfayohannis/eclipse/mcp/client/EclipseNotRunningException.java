package io.github.alfayohannis.eclipse.mcp.client;

/**
 * No running Eclipse with the MCP plug-in could be reached: no instance file,
 * or the server refused the connection. Separate from other failures, since
 * the cure is to start Eclipse or its server (MCP &gt; Start Server).
 */
public class EclipseNotRunningException extends EclipseMcpException {

    private static final long serialVersionUID = 1L;

    /** The failure with a hint on how to start the server. */
    public EclipseNotRunningException(String message) {
        super(message);
    }

    /** The failure with its cause, such as a refused connection. */
    public EclipseNotRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
