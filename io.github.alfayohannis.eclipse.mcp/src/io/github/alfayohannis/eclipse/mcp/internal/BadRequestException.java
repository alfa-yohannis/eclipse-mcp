package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;

/** A request the server cannot parse, answered with the given status before the connection closes. */
final class BadRequestException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int status;

    /** A parse failure that maps to an HTTP status such as 400, 413 or 431. */
    BadRequestException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status to answer with. */
    int status() {
        return status;
    }
}
