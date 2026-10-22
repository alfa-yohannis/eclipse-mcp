package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.graphiti.mm.pictograms.PictogramElement;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;
import io.github.alfayohannis.eclipse.mcp.internal.Ui;

/** Tools that read a diagram and select its elements. */
final class DiagramInfoTools {

    /** Not instantiable: the class only has static members. */
    private DiagramInfoTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(infoTool(), selectTool());
    }

    /** Defines diagram_info, the map of a diagram that the editing tools' ids and coordinates refer to. */
    private static Tool infoTool() {
        return Tool.builder("diagram_info").title("Diagram info")
                .description("Describes an open Graphiti diagram editor, such as the BPMN2 Modeler: file, dirty flag,"
                        + " palette tools (shape and connection tools by drawer), shapes (id, type, name, bounds in"
                        + " diagram coordinates, container) and connections (id, type, name, source, target), plus"
                        + " the canvas's screen bounds for screenshots. Ids are the business objects' ids, such as"
                        + " Task_1, where they have one.")
                .input(Schema.object().string("editor", DiagramArguments.EDITOR)
                        .bool("palette", "Include the palette tools (default true)"))
                .readOnly(true).handler(DiagramInfoTools::info).build();
    }

    /** Defines diagram_select, which shows elements selected in screenshots and the Properties view. */
    private static Tool selectTool() {
        return Tool.builder("diagram_select").title("Diagram select")
                .description("Selects diagram elements by id in the editor, as clicking them would, so the Properties"
                        + " view shows them and screenshots highlight them. An empty list clears the selection.")
                .input(Schema.object().stringList("ids", "Element ids from diagram_info")
                        .string("editor", DiagramArguments.EDITOR))
                .handler(DiagramInfoTools::select).build();
    }

    /** Describes the diagram. */
    private static ToolResult info(Args args) throws Exception {
        String editor = args.string("editor");
        boolean palette = args.bool("palette", true);
        return ToolResult.json(Ui.call(() -> describe(editor, palette)));
    }

    /** The diagram description. UI thread only. */
    private static JsonObject describe(String editorSelector, boolean palette) {
        OpenDiagram diagram = OpenDiagram.find(editorSelector);
        ElementIndex index = ElementIndex.of(diagram.diagram());
        JsonObject json = new JsonObject();
        json.addProperty("editor", diagram.title());
        json.addProperty("file", diagram.inputPath());
        json.addProperty("dirty", diagram.isDirty());
        json.add("canvasScreenBounds", DiagramGeometry.canvasScreenBounds(diagram));
        if (palette) {
            json.add("palette", PaletteCatalog.of(diagram).describe());
        }
        json.add("shapes", index.describeShapes());
        json.add("connections", index.describeConnections());
        return json;
    }

    /** Selects the elements. */
    private static ToolResult select(Args args) throws Exception {
        List<String> ids = args.strings("ids");
        String editor = args.string("editor");
        return ToolResult.text(Ui.call(() -> selectElements(editor, ids)));
    }

    /** Activates the editor and selects the elements. UI thread only. */
    private static String selectElements(String editorSelector, List<String> ids) {
        OpenDiagram diagram = OpenDiagram.find(editorSelector);
        ElementIndex index = ElementIndex.of(diagram.diagram());
        List<PictogramElement> elements = new ArrayList<>();
        for (String id : ids) {
            elements.add(index.element(id));
        }
        diagram.activate();
        diagram.editor().selectPictogramElements(elements.toArray(new PictogramElement[0]));
        return ids.isEmpty() ? "Cleared the selection" : "Selected " + String.join(", ", ids);
    }
}
