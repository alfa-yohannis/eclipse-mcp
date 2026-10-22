package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * A handler of the MCP menu whose enablement follows the server state. It
 * tells the command framework when the state changes, so Start and Stop are
 * enabled in the right state even in menus that are already built.
 */
abstract class ServerStateHandler extends AbstractHandler {

    private final Runnable stateListener = this::announceEnablementChange;

    /** Listens to server starts and stops. */
    ServerStateHandler() {
        ServerControl.addStateListener(stateListener);
    }

    /** Stops listening when the workbench discards the handler. */
    @Override
    public void dispose() {
        ServerControl.removeStateListener(stateListener);
        super.dispose();
    }

    /** Starts the server, telling the user in a dialog when it cannot, since they asked for it. */
    static void startOrExplain(Shell shell) {
        try {
            ServerControl.start();
        } catch (java.io.IOException | RuntimeException e) {
            Log.error("The MCP server could not start", e);
            MessageDialog.openError(shell, "MCP Server", "The MCP server could not start: " + e.getMessage());
        }
    }

    /** Tells the framework that isEnabled() may answer differently now. */
    private void announceEnablementChange() {
        fireHandlerChanged(new HandlerEvent(this, true, false));
    }
}
