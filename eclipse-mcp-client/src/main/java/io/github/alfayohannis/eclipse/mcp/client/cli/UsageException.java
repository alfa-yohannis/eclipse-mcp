package io.github.alfayohannis.eclipse.mcp.client.cli;

/** A command line the CLI cannot run, answered with the usage and exit code 2. */
public final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The problem with the command line. */
    public UsageException(String message) {
        super(message);
    }
}
