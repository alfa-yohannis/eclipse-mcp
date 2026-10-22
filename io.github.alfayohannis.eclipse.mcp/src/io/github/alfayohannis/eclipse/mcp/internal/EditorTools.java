package io.github.alfayohannis.eclipse.mcp.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** Tools that open files in editors and select resources in the Project Explorer. */
final class EditorTools {

    /** Not instantiable: the class only has static members. */
    private EditorTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(openFileTool(), selectResourceTool());
    }

    /** Defines open_file, which works for workspace files and for files anywhere on disk. */
    private static Tool openFileTool() {
        return Tool.builder("open_file").title("Open file")
                .description("Opens a file in an editor. Workspace paths start with the project name"
                        + " (/project/folder/file.txt); an absolute file system path outside the workspace opens as"
                        + " an external file. editor_id picks a specific editor, e.g."
                        + " org.eclipse.ui.DefaultTextEditor to read a model file as text.")
                .input(Schema.object().string("path", "Workspace path or absolute file system path")
                        .integer("line", "Line to reveal in a text editor (1-based)")
                        .string("editor_id", "Editor id; the default editor for the file when omitted")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 2000)").required("path"))
                .handler(EditorTools::openFile).build();
    }

    /** Defines select_resource, which also makes the resource the selection that commands act on. */
    private static Tool selectResourceTool() {
        return Tool.builder("select_resource").title("Select resource")
                .description("Shows the Project Explorer and selects and reveals a workspace resource in it, which"
                        + " also makes it the current selection for commands.")
                .input(Schema.object().string("path", "Workspace path such as /project/folder/file").required("path"))
                .handler(EditorTools::selectResource).build();
    }

    /** Opens the file, waiting in case the editor asks something in a dialog. */
    private static ToolResult openFile(Args args) throws Exception {
        String path = args.requiredString("path");
        Integer line = args.integer("line");
        String editorId = args.string("editor_id");
        IFile workspaceFile = workspaceFile(path);
        IFileStore externalFile = workspaceFile == null ? externalFile(path) : null;
        return UiActionRunner.runAndReport("open " + path, () -> openEditor(workspaceFile, externalFile, editorId, line),
                args.integer("wait_ms", 2000));
    }

    /** The workspace file at a workspace path or inside the workspace on disk, or null. */
    private static IFile workspaceFile(String path) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IResource member = path.startsWith("/") ? root.findMember(path) : null;
        if (member instanceof IFile) {
            return (IFile) member;
        }
        Path location = Paths.get(path);
        if (!location.isAbsolute()) {
            return null;
        }
        for (IFile file : root.findFilesForLocationURI(location.toUri())) {
            if (file.isAccessible()) {
                return file;
            }
        }
        return null;
    }

    /** A file outside the workspace; it must exist. */
    private static IFileStore externalFile(String path) {
        Path location = Paths.get(path);
        if (!location.isAbsolute() || !Files.isRegularFile(location)) {
            throw new ToolException("No file " + path + " in the workspace or on disk");
        }
        return EFS.getLocalFileSystem().getStore(location.toUri());
    }

    /** Opens the editor and reveals the line. UI thread only. */
    private static String openEditor(IFile workspaceFile, IFileStore externalFile, String editorId, Integer line)
            throws PartInitException, BadLocationException {
        IWorkbenchPage page = Ui.page();
        IEditorPart editor;
        if (workspaceFile != null) {
            editor = editorId == null ? IDE.openEditor(page, workspaceFile, true)
                    : IDE.openEditor(page, workspaceFile, editorId, true);
        } else {
            editor = editorId == null ? IDE.openEditorOnFileStore(page, externalFile)
                    : IDE.openEditor(page, externalFile.toURI(), editorId, true);
        }
        if (editor == null) {
            return "No editor opened";
        }
        if (line != null && line > 0) {
            revealLine(editor, line);
        }
        return "Opened " + editor.getTitle() + " in " + editor.getSite().getId();
    }

    /** Moves the caret to the start of the line. */
    private static void revealLine(IEditorPart editor, int line) throws BadLocationException {
        ITextEditor textEditor = editor.getAdapter(ITextEditor.class);
        if (textEditor == null) {
            throw new ToolException(editor.getTitle() + " is not a text editor, so it cannot reveal line " + line);
        }
        IDocument document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        textEditor.selectAndReveal(document.getLineOffset(Math.min(line, document.getNumberOfLines()) - 1), 0);
    }

    /** Selects the resource in the Project Explorer. */
    private static ToolResult selectResource(Args args) throws Exception {
        IResource resource = WorkspaceResources.resource(args.requiredString("path"));
        Ui.run(() -> revealInProjectExplorer(resource));
        return ToolResult.text("Selected " + resource.getFullPath() + " in the Project Explorer");
    }

    /** Activates the Project Explorer first, so the selection becomes the window's current selection. UI thread only. */
    private static void revealInProjectExplorer(IResource resource) {
        IWorkbenchWindow window = Ui.window();
        try {
            window.getActivePage().showView(IPageLayout.ID_PROJECT_EXPLORER);
        } catch (PartInitException e) {
            throw new ToolException("Could not show the Project Explorer: " + e.getMessage());
        }
        BasicNewResourceWizard.selectAndReveal(resource, window);
    }
}
