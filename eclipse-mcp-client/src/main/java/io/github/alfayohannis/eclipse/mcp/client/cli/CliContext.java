package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.io.InputStream;
import java.io.PrintStream;

import io.github.alfayohannis.eclipse.mcp.client.ClientSettings;
import io.github.alfayohannis.eclipse.mcp.client.EclipseClient;

/**
 * What a command needs: the settings, the output and a client that connects
 * on first use, so commands such as help and mcp work without Eclipse.
 */
public final class CliContext {

    private final ClientSettings settings;
    private final Output output;
    private final InputStream in;
    private final PrintStream out;
    private EclipseClient client;

    /** A context over the settings and the standard streams. */
    public CliContext(ClientSettings settings, Output output, InputStream in, PrintStream out) {
        this.settings = settings;
        this.output = output;
        this.in = in;
        this.out = out;
    }

    /** The client, connected on first use. */
    public EclipseClient client() {
        if (client == null) {
            client = EclipseClient.connect(settings);
        }
        return client;
    }

    /** The connection settings. */
    public ClientSettings settings() {
        return settings;
    }

    /** Where results go. */
    public Output output() {
        return output;
    }

    /** Standard input, which the MCP server reads. */
    public InputStream in() {
        return in;
    }

    /** Standard output, which the MCP server writes. */
    public PrintStream out() {
        return out;
    }
}
