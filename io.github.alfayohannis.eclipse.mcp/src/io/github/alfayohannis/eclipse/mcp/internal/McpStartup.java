package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.ui.IStartup;

/** Starts the MCP server once the workbench is open, unless the preference turns that off. */
public class McpStartup implements IStartup {

    /** Called by the workbench on a background thread after start-up. */
    @Override
    public void earlyStartup() {
        if (Config.autoStart()) {
            ServerControl.startQuietly();
        }
    }
}
