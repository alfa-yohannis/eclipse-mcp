package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.List;

import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolProvider;

/**
 * Contributes the diagram tools to the MCP server through its extension point,
 * so the core plug-in installs without Graphiti and gains these tools when
 * Graphiti is present.
 */
public class GraphitiToolProvider implements ToolProvider {

    /** The diagram tools: reading and selecting, shapes, connections and properties. */
    @Override
    public List<Tool> tools() {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(DiagramInfoTools.all());
        tools.addAll(DiagramShapeTools.all());
        tools.addAll(DiagramConnectionTools.all());
        tools.addAll(DiagramPropertyTools.all());
        return tools;
    }
}
