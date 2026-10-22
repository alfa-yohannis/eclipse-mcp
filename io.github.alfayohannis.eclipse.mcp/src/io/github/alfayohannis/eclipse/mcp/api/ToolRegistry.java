package io.github.alfayohannis.eclipse.mcp.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.internal.BuiltinTools;
import io.github.alfayohannis.eclipse.mcp.internal.Config;
import io.github.alfayohannis.eclipse.mcp.internal.Failures;
import io.github.alfayohannis.eclipse.mcp.internal.Log;

/**
 * The tools of this Eclipse instance: the built-in ones, those contributed
 * through the {@code io.github.alfayohannis.eclipse.mcp.tools} extension point,
 * and those registered at run time. The MCP endpoint, the REST API and
 * in-process callers all go through {@link #call(String, JsonObject)}, so they
 * behave the same.
 */
public final class ToolRegistry {

    /** Removes a tool added with {@link ToolRegistry#register(Tool)}. */
    public interface Registration extends AutoCloseable {

        /** Removes the tool; later calls do nothing. */
        @Override
        void close();
    }

    private static final ToolRegistry DEFAULT = new ToolRegistry();

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private boolean loaded;

    /** Use {@link #getDefault()}; there is one registry per Eclipse instance. */
    private ToolRegistry() {
    }

    /** The registry of this Eclipse instance. */
    public static ToolRegistry getDefault() {
        return DEFAULT;
    }

    /** A snapshot of all tools, the built-in ones first. */
    public synchronized List<Tool> tools() {
        loadOnce();
        return new ArrayList<>(tools.values());
    }

    /** The tool with that name, if any. */
    public synchronized Optional<Tool> find(String name) {
        loadOnce();
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Adds a tool until the returned registration is closed.
     *
     * @throws IllegalArgumentException if a tool with that name exists
     */
    public synchronized Registration register(Tool tool) {
        loadOnce();
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException("A tool named " + tool.name() + " is already registered");
        }
        tools.put(tool.name(), tool);
        return () -> unregister(tool);
    }

    /**
     * Calls a tool. Expected failures, such as wrong arguments, come back as an
     * error result; unexpected ones are logged and come back as an internal
     * error result. The caller never sees the exception itself.
     *
     * @throws NoSuchElementException if no tool has that name
     */
    public ToolResult call(String name, JsonObject arguments) {
        Tool tool = find(name).orElseThrow(() -> unknownTool(name));
        try {
            ToolResult result = tool.call(new Args(arguments));
            return result == null ? ToolResult.text("Done.") : result;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            return Failures.toResult(tool.name(), e);
        }
    }

    /** The exception for a name that no tool has. */
    private static NoSuchElementException unknownTool(String name) {
        return new NoSuchElementException("Unknown tool: " + name);
    }

    /** Removes the tool, unless another tool with the same name replaced it meanwhile. */
    private synchronized void unregister(Tool tool) {
        tools.remove(tool.name(), tool);
    }

    /** Loads the built-in and contributed tools the first time they are needed. */
    private void loadOnce() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (Tool tool : BuiltinTools.all()) {
            tools.put(tool.name(), tool);
        }
        for (IConfigurationElement element : Platform.getExtensionRegistry()
                .getConfigurationElementsFor(Config.EXTENSION_POINT)) {
            loadProvider(element);
        }
    }

    /** Asks one contributed provider for its tools; a broken provider is logged and skipped. */
    private void loadProvider(IConfigurationElement element) {
        String contributor = element.getContributor().getName();
        try {
            ToolProvider provider = (ToolProvider) element.createExecutableExtension("class");
            for (Tool tool : provider.tools()) {
                addContributedTool(contributor, tool);
            }
        } catch (CoreException | RuntimeException | LinkageError e) {
            Log.error("Could not load the MCP tools of " + contributor, e);
        }
    }

    /** Adds a contributed tool unless its name is taken, which is logged. */
    private void addContributedTool(String contributor, Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            Log.info(contributor + " contributes a tool named " + tool.name() + ", which already exists; it is ignored");
        }
    }
}
