package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Tools for workspace resources: projects, problems, refresh and build. They
 * use the workspace API on the server thread, since it needs no UI.
 */
final class WorkspaceTools {

    /** Not instantiable: the class only has static members. */
    private WorkspaceTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(listProjectsTool(), problemsTool(), refreshTool(), buildTool());
    }

    /** Defines list_projects, the overview of the workspace a client needs before working on files. */
    private static Tool listProjectsTool() {
        return Tool.builder("list_projects").title("List projects")
                .description("Lists the workspace projects with their location, natures and counts of error and"
                        + " warning markers.")
                .readOnly(true).handler(WorkspaceTools::listProjects).build();
    }

    /** Defines get_problems, which shows what the builders and validators complain about. */
    private static Tool problemsTool() {
        return Tool.builder("get_problems").title("Get problems")
                .description("Lists problem markers (compiler errors, validation problems) in the workspace or one"
                        + " project, most severe first.")
                .input(Schema.object()
                        .choice("severity", "Lowest severity to include; warning (the default) means errors and"
                                + " warnings", "error", "warning", "info")
                        .string("project", "Only this project").integer("limit", "At most this many markers (default 100)"))
                .readOnly(true).handler(WorkspaceTools::problems).build();
    }

    /** Defines refresh, needed after files change on disk behind Eclipse's back. */
    private static Tool refreshTool() {
        return Tool.builder("refresh").title("Refresh")
                .description("Refreshes workspace resources from the file system, for files changed outside Eclipse.")
                .input(Schema.object().string("path", "Workspace path such as /project/folder; the whole workspace"
                        + " when omitted"))
                .handler(WorkspaceTools::refresh).build();
    }

    /** Defines build, which builds and waits so the problem counts are final. */
    private static Tool buildTool() {
        return Tool.builder("build").title("Build")
                .description("Builds the workspace or one project, waits for the build to finish and reports the"
                        + " error and warning counts.")
                .input(Schema.object().string("project", "Only this project")
                        .choice("kind", "incremental (default), full, or clean followed by a full build", "incremental",
                                "full", "clean")
                        .integer("timeout_s", "Give up waiting after this many seconds (default 300)"))
                .handler(WorkspaceTools::build).build();
    }

    /** Lists every project with its location, natures and problem counts. */
    private static ToolResult listProjects(Args args) throws CoreException {
        JsonArray projects = new JsonArray();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            projects.add(describeProject(project));
        }
        JsonObject result = new JsonObject();
        result.add("projects", projects);
        return ToolResult.json(result);
    }

    /** One project; natures and problems only when it is open, since closed projects have none to read. */
    private static JsonObject describeProject(IProject project) throws CoreException {
        JsonObject json = new JsonObject();
        json.addProperty("name", project.getName());
        json.addProperty("open", project.isOpen());
        if (project.getLocation() != null) {
            json.addProperty("location", project.getLocation().toOSString());
        }
        if (project.isOpen()) {
            JsonArray natures = new JsonArray();
            for (String nature : project.getDescription().getNatureIds()) {
                natures.add(nature);
            }
            json.add("natures", natures);
            new ProblemMarkers(project).addCounts(json);
        }
        return json;
    }

    /** Lists the problems of at least the requested severity. */
    private static ToolResult problems(Args args) throws CoreException {
        int minimumSeverity = ProblemMarkers.severity(args.string("severity", "warning"));
        ProblemMarkers markers = new ProblemMarkers(WorkspaceResources.projectOrRoot(args.string("project")));
        JsonArray list = markers.describe(minimumSeverity, args.integer("limit", 100));
        JsonObject result = new JsonObject();
        markers.addCounts(result);
        result.addProperty("infos", markers.count(IMarker.SEVERITY_INFO));
        result.addProperty("shown", list.size());
        result.add("problems", list);
        return ToolResult.json(result);
    }

    /** Refreshes the resource and everything below it. */
    private static ToolResult refresh(Args args) throws CoreException {
        IResource resource = WorkspaceResources.resource(args.string("path", "/"));
        resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
        return ToolResult.text("Refreshed " + resource.getFullPath());
    }

    /** Builds, waits for the auto-build to settle within the deadline, and reports the problem counts. */
    private static ToolResult build(Args args) throws CoreException {
        String kind = args.string("kind", "incremental").toLowerCase(Locale.ROOT);
        String projectName = args.string("project");
        DeadlineMonitor monitor = new DeadlineMonitor(args.integer("timeout_s", 300));
        boolean finished = runBuild(projectName, kind, monitor);
        JsonObject result = new JsonObject();
        result.addProperty("built", projectName == null ? "workspace" : projectName);
        result.addProperty("kind", kind);
        result.addProperty("finished", finished);
        new ProblemMarkers(WorkspaceResources.projectOrRoot(projectName)).addCounts(result);
        return ToolResult.json(result);
    }

    /** Runs the build; returns false when the deadline cut it or the wait short. */
    private static boolean runBuild(String projectName, String kind, DeadlineMonitor monitor) throws CoreException {
        int buildKind = buildKind(kind);
        try {
            if ("clean".equals(kind)) {
                buildScope(projectName, IncrementalProjectBuilder.CLEAN_BUILD, monitor);
            }
            buildScope(projectName, buildKind, monitor);
            Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
            return true;
        } catch (OperationCanceledException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Builds one project, or the whole workspace when no project is named. */
    private static void buildScope(String projectName, int buildKind, DeadlineMonitor monitor) throws CoreException {
        if (projectName == null) {
            ResourcesPlugin.getWorkspace().build(buildKind, monitor);
        } else {
            WorkspaceResources.openProject(projectName).build(buildKind, monitor);
        }
    }

    /** The builder kind for a kind name; clean is followed by a full build. */
    private static int buildKind(String kind) {
        switch (kind) {
        case "incremental":
            return IncrementalProjectBuilder.INCREMENTAL_BUILD;
        case "full":
        case "clean":
            return IncrementalProjectBuilder.FULL_BUILD;
        default:
            throw new IllegalArgumentException("kind must be incremental, full or clean");
        }
    }
}
