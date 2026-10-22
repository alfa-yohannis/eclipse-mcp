package io.github.alfayohannis.eclipse.mcp.client;

/**
 * A failure talking to the Eclipse MCP plug-in, such as a wrong token or an
 * unexpected HTTP status. Callers can catch this one type for every failure
 * of the client.
 */
public class EclipseMcpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** A failure described by its message. */
    public EclipseMcpException(String message) {
        super(message);
    }

    /** A failure caused by another exception, such as an I/O error. */
    public EclipseMcpException(String message, Throwable cause) {
        super(message, cause);
    }
}
