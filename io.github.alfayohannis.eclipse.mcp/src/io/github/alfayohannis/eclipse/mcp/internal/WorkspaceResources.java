package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/** Looks up workspace resources by the paths and names clients send, with errors that say what to fix. */
public final class WorkspaceResources {

    /** Not instantiable: the class only has static members. */
    private WorkspaceResources() {
    }

    /** The resource at a workspace path such as /project/folder; null, "" or "/" mean the workspace root. */
    public static IResource resource(String path) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return root;
        }
        IResource resource = root.findMember(path);
        if (resource == null) {
            throw new ToolException("No resource " + path + " in the workspace; paths start with the project name");
        }
        return resource;
    }

    /** The open project with that name. */
    public static IProject openProject(String name) {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        if (!project.exists()) {
            throw new ToolException("No project named " + name);
        }
        if (!project.isOpen()) {
            throw new ToolException("Project " + name + " is closed");
        }
        return project;
    }

    /** The project with that name, or the workspace root when the name is null. */
    public static IResource projectOrRoot(String name) {
        return name == null ? ResourcesPlugin.getWorkspace().getRoot() : openProject(name);
    }
}
