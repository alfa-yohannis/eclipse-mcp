package io.github.alfayohannis.eclipse.mcp.api;

import java.util.List;

/**
 * Contributes tools through the {@code io.github.alfayohannis.eclipse.mcp.tools}
 * extension point, so a plug-in can add tools without code that runs at start-up.
 */
public interface ToolProvider {

    /**
     * The tools to add. The registry asks once, the first time a client lists
     * or calls tools; a tool whose name already exists is ignored.
     */
    List<Tool> tools();
}
