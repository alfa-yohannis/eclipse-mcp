package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.graphiti.features.ICreateConnectionFeature;
import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.features.IDeleteFeature;
import org.eclipse.graphiti.features.IFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.IMoveShapeFeature;
import org.eclipse.graphiti.features.IResizeShapeFeature;
import org.eclipse.graphiti.features.IUpdateFeature;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.features.context.impl.DeleteContext;
import org.eclipse.graphiti.features.context.impl.MoveShapeContext;
import org.eclipse.graphiti.features.context.impl.MultiDeleteInfo;
import org.eclipse.graphiti.features.context.impl.ResizeShapeContext;
import org.eclipse.graphiti.features.context.impl.UpdateContext;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.palette.IConnectionCreationToolEntry;
import org.eclipse.graphiti.services.Graphiti;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Changes a diagram through the editor's own Graphiti features, the same code
 * its palette and mouse gestures run, so the result is what a person would get
 * and each change can be undone. Coordinates are diagram coordinates. UI
 * thread only.
 */
final class DiagramFeatures {

    /** Not instantiable: the class only has static members. */
    private DiagramFeatures() {
    }

    /**
     * Creates a shape with the palette tool at x,y. Without a container it
     * takes the innermost shape at the point that accepts the new element,
     * else the diagram, as a drop with the mouse does.
     */
    static String create(OpenDiagram diagram, String toolLabel, String containerId, int x, int y, Integer width,
            Integer height) {
        ICreateFeature feature = PaletteCatalog.of(diagram).createFeature(toolLabel);
        ElementIndex index = ElementIndex.of(diagram.diagram());
        CreateContext context = containerId != null
                ? createContext(container(index, containerId), x, y, width, height)
                : createContextAt(diagram.diagram(), feature, x, y, width, height);
        if (!feature.canCreate(context)) {
            throw new ToolException(feature.getName() + " cannot create an element at " + x + "," + y
                    + (containerId == null ? "" : " in " + containerId));
        }
        diagram.execute(feature, context);
        return "Created " + feature.getName();
    }

    /** Connects two elements with the connection tool, trying its features until one accepts the pair. */
    static String connect(OpenDiagram diagram, String toolLabel, String sourceId, String targetId) {
        IConnectionCreationToolEntry entry = PaletteCatalog.of(diagram).connectionEntry(toolLabel);
        ElementIndex index = ElementIndex.of(diagram.diagram());
        CreateConnectionContext context = new CreateConnectionContext();
        PictogramElement source = index.element(sourceId);
        PictogramElement target = index.element(targetId);
        context.setSourcePictogramElement(source);
        context.setTargetPictogramElement(target);
        context.setSourceAnchor(anchorOf(source));
        context.setTargetAnchor(anchorOf(target));
        for (IFeature feature : entry.getCreateConnectionFeatures()) {
            if (feature instanceof ICreateConnectionFeature && ((ICreateConnectionFeature) feature).canCreate(context)) {
                diagram.execute(feature, context);
                return "Connected " + sourceId + " to " + targetId + " with " + feature.getName();
            }
        }
        throw new ToolException(entry.getLabel() + " cannot connect " + sourceId + " to " + targetId);
    }

    /** Moves a shape so its top-left corner lands at x,y, optionally into another container. */
    static String move(OpenDiagram diagram, String id, String containerId, int x, int y) {
        ElementIndex index = ElementIndex.of(diagram.diagram());
        Shape shape = index.shape(id);
        ContainerShape target = containerId == null ? shape.getContainer() : container(index, containerId);
        int[] origin = originOf(target);
        int[] before = ElementIndex.bounds(shape);
        MoveShapeContext context = new MoveShapeContext(shape);
        context.setSourceContainer(shape.getContainer());
        context.setTargetContainer(target);
        context.setLocation(x - origin[0], y - origin[1]);
        context.setDeltaX(x - before[0]);
        context.setDeltaY(y - before[1]);
        IMoveShapeFeature feature = diagram.featureProvider().getMoveShapeFeature(context);
        if (feature == null || !feature.canMoveShape(context)) {
            throw new ToolException(id + " cannot move to " + x + "," + y + (containerId == null ? "" : " in " + containerId));
        }
        diagram.execute(feature, context);
        return "Moved " + id;
    }

    /** Resizes a shape to the given bounds in diagram coordinates. */
    static String resize(OpenDiagram diagram, String id, int x, int y, int width, int height) {
        Shape shape = ElementIndex.of(diagram.diagram()).shape(id);
        int[] origin = originOf(shape.getContainer());
        ResizeShapeContext context = new ResizeShapeContext(shape);
        context.setLocation(x - origin[0], y - origin[1]);
        context.setSize(width, height);
        IResizeShapeFeature feature = diagram.featureProvider().getResizeShapeFeature(context);
        if (feature == null || !feature.canResizeShape(context)) {
            throw new ToolException(id + " cannot take the size " + width + "x" + height);
        }
        diagram.execute(feature, context);
        return "Resized " + id;
    }

    /** Deletes an element without the confirmation dialog, as a deliberate delete from code. */
    static String delete(OpenDiagram diagram, String id) {
        PictogramElement element = ElementIndex.of(diagram.diagram()).element(id);
        DeleteContext context = new DeleteContext(element);
        context.setMultiDeleteInfo(new MultiDeleteInfo(false, false, 1));
        IDeleteFeature feature = diagram.featureProvider().getDeleteFeature(context);
        if (feature == null || !feature.canDelete(context)) {
            throw new ToolException(id + " cannot be deleted");
        }
        diagram.execute(feature, context);
        return "Deleted " + id;
    }

    /** Sets an attribute of the element's business object, then updates its pictogram, such as its label. */
    static String setAttribute(OpenDiagram diagram, String id, String attributeName, String value) {
        PictogramElement element = ElementIndex.of(diagram.diagram()).element(id);
        EObject object = BusinessObjects.of(element);
        if (object == null) {
            throw new ToolException(id + " has no business object to change");
        }
        EAttribute attribute = BusinessObjects.settableAttribute(object, attributeName);
        Object converted = BusinessObjects.convert(attribute, value);
        diagram.editingDomain().getCommandStack()
                .execute(new SetAttributeCommand(diagram.editingDomain(), object, attribute, converted));
        update(diagram, element);
        return "Set " + attributeName + " of " + id;
    }

    /** Runs the update feature when the pictogram is out of date with its business object. */
    static void update(OpenDiagram diagram, PictogramElement element) {
        IFeatureProvider provider = diagram.featureProvider();
        UpdateContext context = new UpdateContext(element);
        IUpdateFeature feature = provider.getUpdateFeature(context);
        if (feature != null && feature.canUpdate(context) && feature.updateNeeded(context).toBoolean()) {
            diagram.execute(feature, context);
        }
    }

    /** A create context at a point inside the container, whose position is subtracted. */
    private static CreateContext createContext(ContainerShape container, int x, int y, Integer width, Integer height) {
        int[] origin = originOf(container);
        CreateContext context = new CreateContext();
        context.setTargetContainer(container);
        context.setLocation(x - origin[0], y - origin[1]);
        if (width != null && height != null) {
            context.setSize(width, height);
        }
        return context;
    }

    /** The create context for the innermost container at the point that accepts the feature, else the diagram. */
    private static CreateContext createContextAt(Diagram diagram, ICreateFeature feature, int x, int y, Integer width,
            Integer height) {
        ContainerShape container = innermostAccepting(diagram, feature, x, y, width, height);
        return createContext(container == null ? diagram : container, x, y, width, height);
    }

    /** The deepest container shape at the point whose create context the feature accepts, or null. */
    private static ContainerShape innermostAccepting(ContainerShape parent, ICreateFeature feature, int x, int y,
            Integer width, Integer height) {
        for (Shape child : parent.getChildren()) {
            if (!(child instanceof ContainerShape) || !child.isVisible() || !contains(child, x, y)) {
                continue;
            }
            ContainerShape deeper = innermostAccepting((ContainerShape) child, feature, x, y, width, height);
            if (deeper != null) {
                return deeper;
            }
            if (feature.canCreate(createContext((ContainerShape) child, x, y, width, height))) {
                return (ContainerShape) child;
            }
        }
        return null;
    }

    /** Whether the shape's bounds in diagram coordinates contain the point. */
    private static boolean contains(Shape shape, int x, int y) {
        int[] bounds = ElementIndex.bounds(shape);
        return x >= bounds[0] && y >= bounds[1] && x < bounds[0] + bounds[2] && y < bounds[1] + bounds[3];
    }

    /** The container shape with the id. */
    private static ContainerShape container(ElementIndex index, String id) {
        Shape shape = index.shape(id);
        if (!(shape instanceof ContainerShape)) {
            throw new ToolException(id + " cannot contain other elements");
        }
        return (ContainerShape) shape;
    }

    /** The diagram position of a container; the diagram itself is at 0,0. */
    private static int[] originOf(ContainerShape container) {
        if (container instanceof Diagram) {
            return new int[] { 0, 0 };
        }
        int[] bounds = ElementIndex.bounds(container);
        return new int[] { bounds[0], bounds[1] };
    }

    /** The anchor a connection attaches to: the chopbox anchor, else the element's first anchor. */
    private static Anchor anchorOf(PictogramElement element) {
        if (!(element instanceof AnchorContainer)) {
            return null;
        }
        Anchor chopbox = Graphiti.getPeService().getChopboxAnchor((AnchorContainer) element);
        if (chopbox != null) {
            return chopbox;
        }
        return ((AnchorContainer) element).getAnchors().isEmpty() ? null : ((AnchorContainer) element).getAnchors().get(0);
    }
}
