package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.handlers.HandlerUtil;

/** MCP &gt; Restart Server: stops and starts the server, which also applies a changed port preference. */
public final class RestartServerHandler extends ServerStateHandler {

    /** Restarts the server, or explains why it cannot start again. */
    @Override
    public Object execute(ExecutionEvent event) {
        ServerControl.stop();
        startOrExplain(HandlerUtil.getActiveShell(event));
        return null;
    }
}
