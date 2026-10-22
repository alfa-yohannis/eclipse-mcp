package io.github.alfayohannis.eclipse.mcp.client.mcp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.client.EclipseMcpException;

/**
 * Waits for Eclipse after the proxy had to answer tools/list without it, and
 * sends notifications/tools/list_changed once the real tool list differs, so
 * the client fetches it.
 */
final class EclipseWatcher {

    private static final int POLL_SECONDS = 5;

    private final PluginLink plugin;
    private final MessageWriter writer;
    private final ToolListCache cache;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(EclipseWatcher::daemon);
    private ScheduledFuture<?> polling;
    private JsonArray servedTools;

    /** A watcher that asks the plug-in through the link and notifies through the writer. */
    EclipseWatcher(PluginLink plugin, MessageWriter writer, ToolListCache cache) {
        this.plugin = plugin;
        this.writer = writer;
        this.cache = cache;
    }

    /** Starts polling, remembering the tools the client got; a running watch keeps going. */
    synchronized void watch(JsonArray toolsServed) {
        servedTools = toolsServed;
        if (polling == null) {
            polling = scheduler.scheduleWithFixedDelay(this::poll, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** Stops polling, for shutdown. */
    void stop() {
        scheduler.shutdownNow();
    }

    /** Asks Eclipse for its tools; when it answers, stops polling and notifies if the list changed. */
    private void poll() {
        JsonArray tools;
        try {
            tools = toolsOf(plugin.forward(JsonRpc.request("watch", "tools/list", new JsonObject())));
        } catch (EclipseMcpException e) {
            return;
        }
        if (tools != null) {
            cache.save(tools);
            finish(tools);
        }
    }

    /** Ends the watch and sends the notification when the tools differ from those served. */
    private synchronized void finish(JsonArray tools) {
        if (polling != null) {
            polling.cancel(false);
            polling = null;
        }
        if (!tools.equals(servedTools)) {
            writer.write(JsonRpc.notification("notifications/tools/list_changed"));
        }
    }

    /** The tools array of a tools/list reply, or null when the reply has another shape. */
    static JsonArray toolsOf(JsonElement reply) {
        if (reply == null || !reply.isJsonObject() || !reply.getAsJsonObject().has("result")) {
            return null;
        }
        JsonElement result = reply.getAsJsonObject().get("result");
        JsonElement tools = result.isJsonObject() ? result.getAsJsonObject().get("tools") : null;
        return tools != null && tools.isJsonArray() ? tools.getAsJsonArray() : null;
    }

    /** A daemon thread, so the watcher never keeps the process alive. */
    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "eclipse-mcp watcher");
        thread.setDaemon(true);
        return thread;
    }
}
