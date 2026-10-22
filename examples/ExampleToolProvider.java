package com.example.mcptools;

import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolProvider;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * A tool contributed by another plug-in. The plug-in requires the bundle
 * io.github.alfayohannis.eclipse.mcp and declares the provider in its
 * plugin.xml:
 *
 * <pre>
 * &lt;extension point="io.github.alfayohannis.eclipse.mcp.tools"&gt;
 *    &lt;provider class="com.example.mcptools.ExampleToolProvider"/&gt;
 * &lt;/extension&gt;
 * </pre>
 *
 * MCP clients, the REST API and in-process callers then see count_files next
 * to the built-in tools.
 */
public class ExampleToolProvider implements ToolProvider {

    /** The tools this plug-in adds. */
    @Override
    public List<Tool> tools() {
        return List.of(Tool.builder("count_files").title("Count files")
                .description("Counts the files of a workspace project, as a minimal example of a contributed tool.")
                .input(Schema.object().string("project", "Project name").required("project"))
                .readOnly(true).handler(ExampleToolProvider::countFiles).build());
    }

    /** Counts the files below the project; the workspace API needs no UI thread. */
    private static ToolResult countFiles(Args args) throws Exception {
        String project = args.requiredString("project");
        int[] files = { 0 };
        ResourcesPlugin.getWorkspace().getRoot().getProject(project).accept(resource -> countFile(resource, files));
        JsonObject result = new JsonObject();
        result.addProperty("project", project);
        result.addProperty("files", files[0]);
        return ToolResult.json(result);
    }

    /** Counts the resource when it is a file and keeps visiting. */
    private static boolean countFile(org.eclipse.core.resources.IResource resource, int[] files) {
        if (resource.getType() == org.eclipse.core.resources.IResource.FILE) {
            files[0]++;
        }
        return true;
    }
}
