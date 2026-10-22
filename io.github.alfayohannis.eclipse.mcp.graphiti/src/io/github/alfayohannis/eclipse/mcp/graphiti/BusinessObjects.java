package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Reads and writes the business objects behind diagram elements by reflection
 * on their EMF class, so the tools work for any Graphiti editor without a
 * compile-time dependency on its model, such as BPMN2.
 */
final class BusinessObjects {

    /** Not instantiable: the class only has static members. */
    private BusinessObjects() {
    }

    /** The business object linked to the pictogram element, or null. */
    static EObject of(PictogramElement element) {
        return Graphiti.getLinkService().getBusinessObjectForLinkedPictogramElement(element);
    }

    /** The EMF class name, such as Task or SequenceFlow. */
    static String type(EObject object) {
        return object.eClass().getName();
    }

    /** The value of the object's "id" attribute, or null. */
    static String id(EObject object) {
        return attribute(object, "id");
    }

    /** The value of the object's "name" attribute, or null. */
    static String name(EObject object) {
        return attribute(object, "name");
    }

    /** The value of a single-valued attribute as text, or null when unset or not an attribute. */
    static String attribute(EObject object, String name) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        if (!(feature instanceof EAttribute) || feature.isMany()) {
            return null;
        }
        Object value = object.eGet(feature);
        return value == null ? null : String.valueOf(value);
    }

    /** A changeable single-valued attribute of the object; fails with the attributes it does have. */
    static EAttribute settableAttribute(EObject object, String name) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(name);
        if (feature instanceof EAttribute && feature.isChangeable() && !feature.isMany()) {
            return (EAttribute) feature;
        }
        StringBuilder names = new StringBuilder();
        for (EAttribute attribute : object.eClass().getEAllAttributes()) {
            if (attribute.isChangeable() && !attribute.isMany()) {
                names.append(names.length() == 0 ? "" : ", ").append(attribute.getName());
            }
        }
        throw new ToolException(type(object) + " has no settable attribute \"" + name + "\"; settable: " + names);
    }

    /** The text converted to the attribute's type, the way EMF reads it from XML. */
    static Object convert(EAttribute attribute, String text) {
        try {
            return EcoreUtil.createFromString(attribute.getEAttributeType(), text);
        } catch (RuntimeException e) {
            throw new ToolException("\"" + text + "\" is not a valid " + attribute.getEAttributeType().getName()
                    + " for " + attribute.getName());
        }
    }
}
