package io.github.alfayohannis.eclipse.mcp.internal;

/** A JSON-RPC error with its code, such as -32601 for an unknown method. */
final class RpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /** An error that the protocol answers with this code and message. */
    RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** The JSON-RPC error code. */
    int code() {
        return code;
    }
}
