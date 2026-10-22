package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.List;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** Tools that create, move, resize and delete diagram shapes. */
final class DiagramShapeTools {

    /** Not instantiable: the class only has static members. */
    private DiagramShapeTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(createTool(), moveTool(), resizeTool(), deleteTool());
    }

    /** Defines diagram_create, which adds an element the way a palette click on the canvas would. */
    private static Tool createTool() {
        return Tool.builder("diagram_create").title("Diagram create")
                .description("Creates a shape with a palette tool, e.g. \"Task\", \"Start Event\", \"Exclusive"
                        + " Gateway\", dropped at x,y in diagram coordinates as a palette click there would; the editor"
                        + " decides how it places the shape (the BPMN2 Modeler centres it on the point). Optionally"
                        + " inside a container and with a name. Without a container it uses the innermost element at"
                        + " the point that accepts the shape, else the diagram. Returns the created elements with their"
                        + " ids and bounds.")
                .input(Schema.object().string("tool", "Palette tool label, from diagram_info")
                        .integer("x", "Drop point x in diagram coordinates").integer("y", "Drop point y in diagram coordinates")
                        .string("container", "Id of the container element, e.g. a lane or sub-process")
                        .string("name", "Name to give the new element").integer("width", "Width, with height")
                        .integer("height", "Height, with width")
                        .choice("mode", DiagramArguments.MODE, "feature", "mouse")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("tool", "x", "y"))
                .handler(DiagramShapeTools::create).build();
    }

    /** Defines diagram_move, which places a shape at new diagram coordinates. */
    private static Tool moveTool() {
        return Tool.builder("diagram_move").title("Diagram move")
                .description("Moves a shape so its top-left corner lands at x,y in diagram coordinates, optionally into"
                        + " another container. mode=mouse drags it on the canvas.")
                .input(Schema.object().string("id", "Element id from diagram_info").integer("x", "New left")
                        .integer("y", "New top").string("container", "Id of a new container element (feature mode)")
                        .choice("mode", DiagramArguments.MODE, "feature", "mouse")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("id", "x", "y"))
                .handler(DiagramShapeTools::move).build();
    }

    /** Defines diagram_resize, which gives a shape new bounds. */
    private static Tool resizeTool() {
        return Tool.builder("diagram_resize").title("Diagram resize")
                .description("Resizes a shape; x and y default to its current top-left corner.")
                .input(Schema.object().string("id", "Element id from diagram_info").integer("width", "New width")
                        .integer("height", "New height").integer("x", "New left").integer("y", "New top")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("id", "width", "height"))
                .handler(DiagramShapeTools::resize).build();
    }

    /** Defines diagram_delete, which removes an element without the editor's confirmation dialog. */
    private static Tool deleteTool() {
        return Tool.builder("diagram_delete").title("Diagram delete")
                .description("Deletes a diagram element (shape or connection) and its business object, without the"
                        + " confirmation dialog. Edit > Undo brings it back.")
                .input(Schema.object().string("id", "Element id from diagram_info")
                        .string("editor", DiagramArguments.EDITOR).integer("wait_ms", DiagramArguments.WAIT_MS)
                        .required("id"))
                .handler(DiagramShapeTools::delete).build();
    }

    /** Creates the shape in the requested mode, then names it when asked. */
    private static ToolResult create(Args args) throws Exception {
        String editor = args.string("editor");
        String tool = args.requiredString("tool");
        int x = args.requiredInteger("x");
        int y = args.requiredInteger("y");
        String container = args.string("container");
        Integer width = args.integer("width");
        Integer height = args.integer("height");
        int waitMs = DiagramArguments.waitMs(args);
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = DiagramArguments.mouseMode(args) ? new DiagramMouse(editor).create(tool, x, y)
                : FeatureRun.run(editor, waitMs, diagram -> DiagramFeatures.create(diagram, tool, container, x, y, width,
                        height));
        String name = args.string("name");
        List<String> created = change.createdSoFar();
        if (name != null && !created.isEmpty()) {
            String id = created.get(0);
            outcome += "; " + FeatureRun.run(editor, waitMs, diagram -> DiagramFeatures.setAttribute(diagram, id, "name",
                    name));
        }
        return change.report("create " + tool, outcome);
    }

    /** Moves the shape in the requested mode. */
    private static ToolResult move(Args args) throws Exception {
        String editor = args.string("editor");
        String id = args.requiredString("id");
        int x = args.requiredInteger("x");
        int y = args.requiredInteger("y");
        String container = args.string("container");
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = DiagramArguments.mouseMode(args) ? new DiagramMouse(editor).move(id, x, y)
                : FeatureRun.run(editor, DiagramArguments.waitMs(args),
                        diagram -> DiagramFeatures.move(diagram, id, container, x, y));
        return change.report("move " + id, outcome);
    }

    /** Resizes the shape, keeping its position unless x or y is given. */
    private static ToolResult resize(Args args) throws Exception {
        String editor = args.string("editor");
        String id = args.requiredString("id");
        int width = args.requiredInteger("width");
        int height = args.requiredInteger("height");
        Integer x = args.integer("x");
        Integer y = args.integer("y");
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = FeatureRun.run(editor, DiagramArguments.waitMs(args),
                diagram -> resizeShape(diagram, id, x, y, width, height));
        return change.report("resize " + id, outcome);
    }

    /** Resizes with the current position for a missing x or y. UI thread only. */
    private static String resizeShape(OpenDiagram diagram, String id, Integer x, Integer y, int width, int height) {
        int[] bounds = ElementIndex.bounds(ElementIndex.of(diagram.diagram()).shape(id));
        return DiagramFeatures.resize(diagram, id, x != null ? x : bounds[0], y != null ? y : bounds[1], width, height);
    }

    /** Deletes the element. */
    private static ToolResult delete(Args args) throws Exception {
        String editor = args.string("editor");
        String id = args.requiredString("id");
        DiagramChange change = DiagramChange.begin(editor);
        String outcome = FeatureRun.run(editor, DiagramArguments.waitMs(args), diagram -> DiagramFeatures.delete(diagram, id));
        return change.report("delete " + id, outcome);
    }
}
