package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.graphiti.datatypes.ILocation;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * The elements of a diagram with stable ids. An element's id is its business
 * object's id when it has one, such as Task_1, else a path of child indexes.
 * Each business object counts once: its first shape that is not a label is the
 * element, and labels and decorations of the same object are left out.
 * UI thread only.
 */
final class ElementIndex {

    /** BPMN2 Modeler and other editors mark label shapes with this property. */
    private static final String LABEL_PROPERTY = "label.shape";

    private final Map<String, PictogramElement> elementsById = new LinkedHashMap<>();
    private final Map<PictogramElement, String> idsByElement = new HashMap<>();
    private final Map<EObject, String> idsByObject = new HashMap<>();
    private final List<Shape> shapes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    /** Use {@link #of(Diagram)}. */
    private ElementIndex() {
    }

    /** Indexes the shapes, depth first, and the connections of the diagram. */
    static ElementIndex of(Diagram diagram) {
        ElementIndex index = new ElementIndex();
        index.addShapes(diagram, "shape:");
        for (int position = 0; position < diagram.getConnections().size(); position++) {
            index.addConnection(diagram.getConnections().get(position), position);
        }
        return index;
    }

    /** The shape with the id; fails with the known ids. */
    Shape shape(String id) {
        PictogramElement element = element(id);
        if (!(element instanceof Shape)) {
            throw new ToolException(id + " is a connection, not a shape");
        }
        return (Shape) element;
    }

    /** The shape or connection with the id; fails with the known ids. */
    PictogramElement element(String id) {
        PictogramElement element = elementsById.get(id);
        if (element == null) {
            throw new ToolException("No diagram element " + id + ". Elements: " + String.join(", ", elementsById.keySet()));
        }
        return element;
    }

    /** The id of an indexed element, or of the element that owns a label or decoration; null otherwise. */
    String idOf(PictogramElement element) {
        String id = idsByElement.get(element);
        if (id != null) {
            return id;
        }
        EObject object = BusinessObjects.of(element);
        return object == null ? null : idsByObject.get(object);
    }

    /** Descriptions of all elements by id, for comparing before and after a change. */
    Map<String, JsonObject> describeAll() {
        Map<String, JsonObject> descriptions = new LinkedHashMap<>();
        for (Shape shape : shapes) {
            descriptions.put(idOf(shape), describeShape(shape));
        }
        for (Connection connection : connections) {
            descriptions.put(idOf(connection), describeConnection(connection));
        }
        return descriptions;
    }

    /** The shapes as JSON. */
    JsonArray describeShapes() {
        JsonArray array = new JsonArray();
        for (Shape shape : shapes) {
            array.add(describeShape(shape));
        }
        return array;
    }

    /** The connections as JSON. */
    JsonArray describeConnections() {
        JsonArray array = new JsonArray();
        for (Connection connection : connections) {
            array.add(describeConnection(connection));
        }
        return array;
    }

    /** Bounds of a shape in diagram coordinates, the coordinates the tools take. */
    static int[] bounds(Shape shape) {
        ILocation location = Graphiti.getPeLayoutService().getLocationRelativeToDiagram(shape);
        GraphicsAlgorithm algorithm = shape.getGraphicsAlgorithm();
        int width = algorithm == null ? 0 : algorithm.getWidth();
        int height = algorithm == null ? 0 : algorithm.getHeight();
        return new int[] { location.getX(), location.getY(), width, height };
    }

    /** Adds the container's children and their descendants, numbering anonymous ones by path. */
    private void addShapes(ContainerShape container, String pathPrefix) {
        for (int position = 0; position < container.getChildren().size(); position++) {
            Shape child = container.getChildren().get(position);
            String path = pathPrefix + position;
            if (child.isVisible() && isElement(child)) {
                register(child, path);
            }
            if (child instanceof ContainerShape) {
                addShapes((ContainerShape) child, path + "/");
            }
        }
    }

    /** Whether the shape is the element of its business object rather than a label or a decoration. */
    private boolean isElement(Shape shape) {
        if (Graphiti.getPeService().getPropertyValue(shape, LABEL_PROPERTY) != null) {
            return false;
        }
        EObject object = BusinessObjects.of(shape);
        return object == null ? shape.isActive() && !shape.getAnchors().isEmpty() : !idsByObject.containsKey(object);
    }

    /** Adds a connection; connections without their own object get a positional id. */
    private void addConnection(Connection connection, int position) {
        EObject object = BusinessObjects.of(connection);
        if (object == null || !idsByObject.containsKey(object)) {
            register(connection, "connection:" + position);
            connections.add(connection);
        }
    }

    /** Gives the element its id: the business object's id when it is free, else the path. */
    private void register(PictogramElement element, String path) {
        EObject object = BusinessObjects.of(element);
        String objectId = object == null ? null : BusinessObjects.id(object);
        String id = objectId != null && !objectId.isEmpty() && !elementsById.containsKey(objectId) ? objectId : path;
        elementsById.put(id, element);
        idsByElement.put(element, id);
        if (object != null) {
            idsByObject.putIfAbsent(object, id);
        }
        if (element instanceof Shape) {
            shapes.add((Shape) element);
        }
    }

    /** One shape: id, type, name, bounds and container. */
    private JsonObject describeShape(Shape shape) {
        JsonObject json = describeObject(shape);
        int[] bounds = bounds(shape);
        JsonObject box = new JsonObject();
        box.addProperty("x", bounds[0]);
        box.addProperty("y", bounds[1]);
        box.addProperty("width", bounds[2]);
        box.addProperty("height", bounds[3]);
        json.add("bounds", box);
        ContainerShape container = shape.getContainer();
        if (container != null && !(container instanceof Diagram)) {
            json.addProperty("container", idOf(container));
        }
        return json;
    }

    /** One connection: id, type, name, and the ids of its source and target. */
    private JsonObject describeConnection(Connection connection) {
        JsonObject json = describeObject(connection);
        json.addProperty("source", endId(connection.getStart() == null ? null : connection.getStart().getParent()));
        json.addProperty("target", endId(connection.getEnd() == null ? null : connection.getEnd().getParent()));
        return json;
    }

    /** The id, type and name shared by shapes and connections. */
    private JsonObject describeObject(PictogramElement element) {
        JsonObject json = new JsonObject();
        json.addProperty("id", idOf(element));
        EObject object = BusinessObjects.of(element);
        json.addProperty("type", object == null ? element.eClass().getName() : BusinessObjects.type(object));
        String name = object == null ? null : BusinessObjects.name(object);
        if (name != null) {
            json.addProperty("name", name);
        }
        return json;
    }

    /** The id of the element at one end of a connection. */
    private String endId(AnchorContainer end) {
        return end == null ? null : idOf(end);
    }
}
