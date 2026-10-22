package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.commands.ExecutionEvent;

/** MCP &gt; Stop Server: stops the server and removes its instance file; enabled only while it runs. */
public final class StopServerHandler extends ServerStateHandler {

    /** Enabled while the server runs. */
    @Override
    public boolean isEnabled() {
        return ServerControl.isRunning();
    }

    /** Stops the server; a request that is being answered still gets its answer. */
    @Override
    public Object execute(ExecutionEvent event) {
        ServerControl.stop();
        return null;
    }
}
