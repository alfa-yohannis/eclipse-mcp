package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;

import io.github.alfayohannis.eclipse.mcp.api.Tool;

/** The tools this plug-in brings itself, in the order clients list them. */
public final class BuiltinTools {

    /** Not instantiable: the class only has static members. */
    private BuiltinTools() {
    }

    /** All built-in tools, grouped from overview to low-level input. */
    public static List<Tool> all() {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(StatusTools.all());
        tools.addAll(WorkspaceTools.all());
        tools.addAll(EditorTools.all());
        tools.addAll(CommandTools.all());
        tools.addAll(MenuTools.all());
        tools.addAll(ShellTools.all());
        tools.addAll(TreeTools.all());
        tools.addAll(ScreenshotTools.all());
        tools.addAll(MouseTools.all());
        tools.addAll(KeyboardTools.all());
        tools.addAll(LaunchTools.all());
        return tools;
    }
}
