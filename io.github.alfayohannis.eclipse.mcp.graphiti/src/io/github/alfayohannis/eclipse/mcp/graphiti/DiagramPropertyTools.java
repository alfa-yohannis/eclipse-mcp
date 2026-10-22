package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** Tools that change the business objects behind diagram elements: names and other attributes. */
final class DiagramPropertyTools {

    /** Not instantiable: the class only has static members. */
    private DiagramPropertyTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(renameTool(), setPropertyTool());
    }

    /** Defines diagram_rename, the most common property change. */
    private static Tool renameTool() {
        return Tool.builder("diagram_rename").title("Diagram rename")
                .description("Renames a diagram element: sets the name of its business object in an undoable"
                        + " transaction, then updates the element so the new label shows.")
                .input(Schema.object().string("id", "Element id from diagram_info").string("name", "The new name")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("id", "name"))
                .handler(DiagramPropertyTools::rename).build();
    }

    /** Defines diagram_set_property, for any single-valued attribute of the business object. */
    private static Tool setPropertyTool() {
        return Tool.builder("diagram_set_property").title("Diagram set property")
                .description("Sets a single-valued attribute of a diagram element's business object, e.g."
                        + " isInterrupting=false on a boundary event or gatewayDirection=Diverging on a gateway. The"
                        + " value is text, converted to the attribute's type; an unknown attribute fails with the"
                        + " list of settable ones.")
                .input(Schema.object().string("id", "Element id from diagram_info")
                        .string("attribute", "Attribute name of the business object").string("value", "The new value")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("id", "attribute", "value"))
                .handler(DiagramPropertyTools::setProperty).build();
    }

    /** Sets the name. */
    private static ToolResult rename(Args args) throws Exception {
        return setAttribute(args, "name", args.requiredString("name"));
    }

    /** Sets the named attribute. */
    private static ToolResult setProperty(Args args) throws Exception {
        String value = args.string("value");
        if (value == null) {
            throw new IllegalArgumentException("Missing argument \"value\"");
        }
        return setAttribute(args, args.requiredString("attribute"), value);
    }

    /** Sets an attribute and reports the change. */
    private static ToolResult setAttribute(Args args, String attribute, String value) throws Exception {
        String editor = args.string("editor");
        String id = args.requiredString("id");
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = FeatureRun.run(editor, DiagramArguments.waitMs(args),
                diagram -> DiagramFeatures.setAttribute(diagram, id, attribute, value));
        return change.report("set " + attribute + " of " + id, outcome);
    }
}
