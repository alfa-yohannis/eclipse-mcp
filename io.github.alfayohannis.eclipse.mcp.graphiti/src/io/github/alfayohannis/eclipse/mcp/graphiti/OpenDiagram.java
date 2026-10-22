package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.graphiti.features.IFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.IContext;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.ui.editor.DiagramEditor;
import org.eclipse.graphiti.ui.editor.DiagramEditorInput;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.part.MultiPageEditorPart;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.internal.Labels;
import io.github.alfayohannis.eclipse.mcp.internal.Ui;

/**
 * A Graphiti diagram editor that the tools work on, with the parts of it they
 * need. It also finds the diagram page inside multi-page editors such as the
 * BPMN2 Modeler's. UI thread only.
 */
final class OpenDiagram {

    private final IEditorPart part;
    private final DiagramEditor editor;

    /** The workbench part and the Graphiti editor inside it, which may be the same object. */
    private OpenDiagram(IEditorPart part, DiagramEditor editor) {
        this.part = part;
        this.editor = editor;
    }

    /**
     * The open diagram whose title or input path contains the selector, or the
     * active editor's diagram, or the first open diagram when no selector is given.
     */
    static OpenDiagram find(String selector) {
        IWorkbenchPage page = Ui.page();
        if (selector == null || selector.isBlank()) {
            DiagramEditor active = diagramEditorOf(page.getActiveEditor());
            if (active != null) {
                return new OpenDiagram(page.getActiveEditor(), active);
            }
        }
        for (IEditorReference reference : page.getEditorReferences()) {
            OpenDiagram candidate = candidate(reference, selector);
            if (candidate != null) {
                return candidate;
            }
        }
        throw new ToolException(selector == null || selector.isBlank()
                ? "No Graphiti diagram editor is open; open a diagram with open_file first"
                : "No open diagram editor matches \"" + selector + "\"");
    }

    /** The diagram of the editor when it is one and matches the selector, else null. */
    private static OpenDiagram candidate(IEditorReference reference, String selector) {
        IEditorPart part = reference.getEditor(true);
        DiagramEditor editor = diagramEditorOf(part);
        if (editor == null) {
            return null;
        }
        OpenDiagram diagram = new OpenDiagram(part, editor);
        boolean matches = selector == null || selector.isBlank()
                || Labels.key(diagram.title()).contains(Labels.key(selector))
                || Labels.key(diagram.inputPath()).contains(Labels.key(selector));
        return matches ? diagram : null;
    }

    /** The Graphiti editor of a part: the part itself, its adapter, or a page of a multi-page editor. */
    private static DiagramEditor diagramEditorOf(IEditorPart part) {
        if (part == null) {
            return null;
        }
        if (part instanceof DiagramEditor) {
            return (DiagramEditor) part;
        }
        DiagramEditor adapted = part.getAdapter(DiagramEditor.class);
        if (adapted != null || !(part instanceof MultiPageEditorPart)) {
            return adapted;
        }
        for (IEditorPart page : ((MultiPageEditorPart) part).findEditors(part.getEditorInput())) {
            if (page instanceof DiagramEditor) {
                return (DiagramEditor) page;
            }
        }
        return null;
    }

    /** The editor as the workbench knows it, for activation and titles. */
    IEditorPart part() {
        return part;
    }

    /** The Graphiti editor. */
    DiagramEditor editor() {
        return editor;
    }

    /** The Graphiti diagram, the root of the pictogram model. */
    Diagram diagram() {
        return editor.getDiagramTypeProvider().getDiagram();
    }

    /** The feature provider, which supplies the create, move, delete and update features. */
    IFeatureProvider featureProvider() {
        return editor.getDiagramTypeProvider().getFeatureProvider();
    }

    /** The GEF viewer that draws the diagram. */
    GraphicalViewer viewer() {
        return editor.getGraphicalViewer();
    }

    /** The transactional editing domain that all model changes must go through. */
    TransactionalEditingDomain editingDomain() {
        return editor.getEditingDomain();
    }

    /** The editor tab title. */
    String title() {
        return part.getTitle();
    }

    /** The workspace path of the edited file, else the diagram's URI, else the input's name. */
    String inputPath() {
        IEditorInput input = part.getEditorInput();
        IFile file = input.getAdapter(IFile.class);
        if (file != null) {
            return file.getFullPath().toString();
        }
        URI uri = input instanceof DiagramEditorInput ? ((DiagramEditorInput) input).getUri() : null;
        if (uri == null) {
            return input.getName();
        }
        return uri.isPlatformResource() ? uri.trimFragment().toPlatformString(true) : uri.trimFragment().toString();
    }

    /** Whether there are unsaved changes. */
    boolean isDirty() {
        return part.isDirty();
    }

    /** Brings the editor to the front, so mouse input reaches its canvas and palette. */
    void activate() {
        part.getSite().getPage().activate(part);
    }

    /**
     * Runs a feature through the diagram behavior, which records it on the
     * command stack so Edit &gt; Undo can revert it.
     */
    Object execute(IFeature feature, IContext context) {
        return editor.getDiagramBehavior().executeFeature(feature, context);
    }
}
