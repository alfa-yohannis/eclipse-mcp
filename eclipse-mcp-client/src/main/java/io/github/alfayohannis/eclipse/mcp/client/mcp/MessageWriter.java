package io.github.alfayohannis.eclipse.mcp.client.mcp;

import java.io.PrintStream;

import com.google.gson.JsonElement;

/**
 * Writes JSON-RPC messages to standard output, one per line as the MCP stdio
 * transport requires. Replies and notifications come from different threads,
 * so writes are serialized.
 */
final class MessageWriter {

    private final PrintStream out;

    /** A writer on the stream. */
    MessageWriter(PrintStream out) {
        this.out = out;
    }

    /** Writes one message and flushes, so the client sees it at once. */
    synchronized void write(JsonElement message) {
        out.print(message.toString());
        out.print('\n');
        out.flush();
    }
}
