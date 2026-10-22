package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.ui.DebugUITools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** Tools for launch configurations (Run/Debug Configurations): list, launch and terminate. */
final class LaunchTools {

    /** Not instantiable: the class only has static members. */
    private LaunchTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(listTool(), launchTool(), terminateTool());
    }

    /** Defines list_launch_configurations, which also shows what is running. */
    private static Tool listTool() {
        return Tool.builder("list_launch_configurations").title("List launch configurations")
                .description("Lists the launch configurations (Run/Debug Configurations) and the launches that are"
                        + " running or ended, with their processes.")
                .readOnly(true).handler(LaunchTools::list).build();
    }

    /** Defines launch, which starts a configuration the way the Run menu does. */
    private static Tool launchTool() {
        return Tool.builder("launch").title("Launch")
                .description("Launches a launch configuration by name in run or debug mode, as the Run menu does."
                        + " Dialogs that the launch opens stay open; the result lists the shells and the launches.")
                .input(Schema.object().string("name", "Launch configuration name")
                        .choice("mode", "Launch mode (default run)", "run", "debug")
                        .integer("wait_ms", ArgumentTexts.WAIT_MS + " (default 3000)").required("name"))
                .handler(LaunchTools::launch).build();
    }

    /** Defines terminate_launch, which stops what launch started. */
    private static Tool terminateTool() {
        return Tool.builder("terminate_launch").title("Terminate launch")
                .description("Terminates the running launches of a launch configuration.")
                .input(Schema.object().string("name", "Launch configuration name").required("name"))
                .handler(LaunchTools::terminate).build();
    }

    /** Lists the configurations and the launches. */
    private static ToolResult list(Args args) throws CoreException {
        JsonArray configurations = new JsonArray();
        for (ILaunchConfiguration configuration : launchManager().getLaunchConfigurations()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", configuration.getName());
            json.addProperty("type", configuration.getType().getName());
            json.addProperty("typeId", configuration.getType().getIdentifier());
            configurations.add(json);
        }
        JsonObject result = new JsonObject();
        result.add("configurations", configurations);
        result.add("launches", describeLaunches());
        return ToolResult.json(result);
    }

    /** Launches the configuration through the debug UI, which saves editors and reports errors as people expect. */
    private static ToolResult launch(Args args) throws Exception {
        ILaunchConfiguration configuration = configuration(args.requiredString("name"));
        String mode = args.string("mode", "run");
        if (!"run".equals(mode) && !"debug".equals(mode)) {
            throw new IllegalArgumentException("mode must be run or debug");
        }
        ToolResult report = UiActionRunner.runAndReport("launch " + configuration.getName(),
                () -> startLaunch(configuration, mode), args.integer("wait_ms", 3000));
        JsonObject json = report.structuredContent();
        json.add("launches", describeLaunches());
        return ToolResult.json(json);
    }

    /** Starts the launch; the launch itself runs in a job. UI thread only. */
    private static String startLaunch(ILaunchConfiguration configuration, String mode) {
        DebugUITools.launch(configuration, mode);
        return "Launching " + configuration.getName() + " in " + mode + " mode";
    }

    /** Terminates every running launch of the configuration. */
    private static ToolResult terminate(Args args) {
        String name = args.requiredString("name");
        int terminated = 0;
        for (ILaunch launch : launchManager().getLaunches()) {
            if (isRunningLaunchOf(launch, name)) {
                terminate(launch, name);
                terminated++;
            }
        }
        if (terminated == 0) {
            throw new ToolException("No running launch of " + name);
        }
        return ToolResult.text("Terminated " + terminated + " launch(es) of " + name);
    }

    /** Whether the launch belongs to the named configuration and can still be terminated. */
    private static boolean isRunningLaunchOf(ILaunch launch, String name) {
        ILaunchConfiguration configuration = launch.getLaunchConfiguration();
        return configuration != null && configuration.getName().equals(name) && launch.canTerminate();
    }

    /** Terminates one launch. */
    private static void terminate(ILaunch launch, String name) {
        try {
            launch.terminate();
        } catch (DebugException e) {
            throw new ToolException("Could not terminate " + name + ": " + e.getMessage());
        }
    }

    /** The configuration with that exact name, else the best label match. */
    private static ILaunchConfiguration configuration(String name) throws CoreException {
        List<ILaunchConfiguration> configurations = Arrays.asList(launchManager().getLaunchConfigurations());
        for (ILaunchConfiguration configuration : configurations) {
            if (configuration.getName().equals(name)) {
                return configuration;
            }
        }
        ILaunchConfiguration match = Labels.best(configurations, ILaunchConfiguration::getName, name);
        if (match == null) {
            throw new ToolException("No launch configuration named " + name + ". Configurations: "
                    + Labels.list(configurations, ILaunchConfiguration::getName));
        }
        return match;
    }

    /** The launches with their mode, state and processes. */
    private static JsonArray describeLaunches() {
        JsonArray launches = new JsonArray();
        for (ILaunch launch : launchManager().getLaunches()) {
            ILaunchConfiguration configuration = launch.getLaunchConfiguration();
            JsonObject json = new JsonObject();
            json.addProperty("configuration", configuration == null ? "(none)" : configuration.getName());
            json.addProperty("mode", launch.getLaunchMode());
            json.addProperty("terminated", launch.isTerminated());
            JsonArray processes = new JsonArray();
            for (IProcess process : launch.getProcesses()) {
                processes.add(describeProcess(process));
            }
            json.add("processes", processes);
            launches.add(json);
        }
        return launches;
    }

    /** One process: label, whether it ended, and its exit value then. */
    private static JsonObject describeProcess(IProcess process) {
        JsonObject json = new JsonObject();
        json.addProperty("label", process.getLabel());
        json.addProperty("terminated", process.isTerminated());
        if (process.isTerminated()) {
            try {
                json.addProperty("exitValue", process.getExitValue());
            } catch (DebugException e) {
                // the process reports no exit value
            }
        }
        return json;
    }

    /** The workspace's launch manager. */
    private static ILaunchManager launchManager() {
        return DebugPlugin.getDefault().getLaunchManager();
    }
}
