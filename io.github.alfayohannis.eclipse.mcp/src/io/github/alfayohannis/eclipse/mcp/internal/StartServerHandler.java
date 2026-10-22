package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.handlers.HandlerUtil;

/** MCP &gt; Start Server: starts the server; enabled only while it is stopped. */
public final class StartServerHandler extends ServerStateHandler {

    /** Enabled while the server is stopped. */
    @Override
    public boolean isEnabled() {
        return !ServerControl.isRunning();
    }

    /** Starts the server, or explains why it cannot. */
    @Override
    public Object execute(ExecutionEvent event) {
        startOrExplain(HandlerUtil.getActiveShell(event));
        return null;
    }
}
