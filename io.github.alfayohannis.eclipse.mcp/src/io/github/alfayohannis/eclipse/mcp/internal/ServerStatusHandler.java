package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.handlers.HandlerUtil;

/** MCP &gt; Server Status...: shows where the server runs and how to connect a client. */
public final class ServerStatusHandler extends AbstractHandler {

    /** Opens the status dialog. */
    @Override
    public Object execute(ExecutionEvent event) {
        new ServerStatusDialog(HandlerUtil.getActiveShell(event)).open();
        return null;
    }
}
