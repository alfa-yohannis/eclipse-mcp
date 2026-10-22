package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** The tool that connects diagram elements with lines, such as BPMN sequence flows. */
final class DiagramConnectionTools {

    /** Not instantiable: the class only has static members. */
    private DiagramConnectionTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(Tool.builder("diagram_connect").title("Diagram connect")
                .description("Connects two diagram elements with a connection tool of the palette, e.g. \"Sequence"
                        + " Flow\" (the default), \"Message Flow\" or \"Association\". Returns the new connection and"
                        + " its id. mode=mouse clicks the tool, the source and the target on the canvas.")
                .input(Schema.object().string("source", "Id of the source element").string("target", "Id of the target element")
                        .string("tool", "Connection tool label (default Sequence Flow)")
                        .choice("mode", DiagramArguments.MODE, "feature", "mouse")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("source", "target"))
                .handler(DiagramConnectionTools::connect).build());
    }

    /** Connects the elements in the requested mode. */
    private static ToolResult connect(Args args) throws Exception {
        String editor = args.string("editor");
        String source = args.requiredString("source");
        String target = args.requiredString("target");
        String tool = args.string("tool", "Sequence Flow");
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = DiagramArguments.mouseMode(args) ? new DiagramMouse(editor).connect(tool, source, target)
                : FeatureRun.run(editor, DiagramArguments.waitMs(args),
                        diagram -> DiagramFeatures.connect(diagram, tool, source, target));
        return change.report("connect " + source + " to " + target, outcome);
    }
}
