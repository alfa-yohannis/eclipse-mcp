package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.osgi.framework.Bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** The eclipse_status tool, the first thing a client calls to see the state of the IDE. */
final class StatusTools {

    private static final int MAX_JOBS = 20;

    /** Not instantiable: the class only has static members. */
    private StatusTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(Tool.builder("eclipse_status").title("Eclipse status")
                .description("Describes the running Eclipse: version, workspace, window, perspective, active part,"
                        + " open editors, open shells (the window and any dialogs) and running jobs. Call it first"
                        + " to see the state of the IDE.")
                .readOnly(true).handler(StatusTools::status).build());
    }

    /** Collects the workbench state on the UI thread and the running jobs here. */
    private static ToolResult status(Args args) throws Exception {
        JsonObject status = Ui.call(StatusTools::describeWorkbench);
        status.add("runningJobs", runningJobs());
        return ToolResult.json(status);
    }

    /** Product, versions, workspace, window and editors. UI thread only. */
    private static JsonObject describeWorkbench() {
        JsonObject status = new JsonObject();
        IProduct product = Platform.getProduct();
        if (product != null) {
            status.addProperty("product", product.getName() + " (" + product.getId() + ")");
        }
        Bundle platform = Platform.getBundle("org.eclipse.platform");
        if (platform != null) {
            status.addProperty("platformVersion", platform.getVersion().toString());
        }
        status.addProperty("java", System.getProperty("java.version"));
        status.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch") + ", windowing "
                + Platform.getWS());
        status.addProperty("workspace", ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());
        status.addProperty("projects", ResourcesPlugin.getWorkspace().getRoot().getProjects().length);
        IWorkbenchWindow window = Ui.window();
        status.addProperty("windowTitle", window.getShell().getText());
        IWorkbenchPage page = window.getActivePage();
        if (page != null) {
            describePage(page, status);
        }
        status.add("shells", Shells.describeAll());
        return status;
    }

    /** The perspective, the active part and the open editors of the page. */
    private static void describePage(IWorkbenchPage page, JsonObject status) {
        IPerspectiveDescriptor perspective = page.getPerspective();
        if (perspective != null) {
            status.addProperty("perspective", perspective.getLabel() + " (" + perspective.getId() + ")");
        }
        IWorkbenchPartReference activePart = page.getActivePartReference();
        if (activePart != null) {
            status.addProperty("activePart", activePart.getPartName() + " (" + activePart.getId() + ")");
        }
        JsonArray editors = new JsonArray();
        for (IEditorReference reference : page.getEditorReferences()) {
            editors.add(describeEditor(reference, page.getActiveEditor()));
        }
        status.add("editors", editors);
    }

    /** One editor: title, id, input, and whether it is dirty or active. */
    private static JsonObject describeEditor(IEditorReference reference, IEditorPart activeEditor) {
        JsonObject editor = new JsonObject();
        editor.addProperty("title", reference.getTitle());
        editor.addProperty("id", reference.getId());
        try {
            editor.addProperty("input", inputPath(reference.getEditorInput()));
        } catch (PartInitException e) {
            // the editor has not been restored yet, so its input is unknown
        }
        if (reference.isDirty()) {
            editor.addProperty("dirty", true);
        }
        if (activeEditor != null && reference.getPart(false) == activeEditor) {
            editor.addProperty("active", true);
        }
        return editor;
    }

    /** The workspace path of an editor input, else its URI, else its name. */
    static String inputPath(IEditorInput input) {
        IFile file = input.getAdapter(IFile.class);
        if (file != null) {
            return file.getFullPath().toString();
        }
        return input instanceof IURIEditorInput ? ((IURIEditorInput) input).getURI().toString() : input.getName();
    }

    /** Names of the user-visible jobs that are running, such as builds and indexing. */
    private static JsonArray runningJobs() {
        JsonArray jobs = new JsonArray();
        for (Job job : Job.getJobManager().find(null)) {
            if (!job.isSystem() && job.getState() == Job.RUNNING && jobs.size() < MAX_JOBS) {
                jobs.add(job.getName());
            }
        }
        return jobs;
    }
}
