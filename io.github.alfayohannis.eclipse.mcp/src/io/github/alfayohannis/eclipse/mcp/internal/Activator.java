package io.github.alfayohannis.eclipse.mcp.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The bundle's activator. It only makes sure the server goes down with the
 * bundle; {@link McpStartup} and the MCP menu start it, since the server needs
 * the workbench.
 */
public class Activator implements BundleActivator {

    /** Nothing to do: starting waits for the workbench, see {@link McpStartup}. */
    @Override
    public void start(BundleContext context) {
        // the server starts after the workbench, not with the bundle
    }

    /** Stops the server so its port and instance file do not outlive the bundle. */
    @Override
    public void stop(BundleContext context) {
        ServerControl.stop();
    }
}
