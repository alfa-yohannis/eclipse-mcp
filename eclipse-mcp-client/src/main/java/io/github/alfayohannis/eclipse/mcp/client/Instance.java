package io.github.alfayohannis.eclipse.mcp.client;

import java.net.URI;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A running Eclipse as its instance file announces it: address, process and workspace. */
public final class Instance {

    private final JsonObject json;

    /** Wraps the content of one instance file. */
    public Instance(JsonObject json) {
        this.json = json;
    }

    /** The server address without a path, such as http://127.0.0.1:8765. */
    public URI baseUrl() {
        return URI.create(text("url"));
    }

    /** The port the server listens on, which also names its instance file. */
    public int port() {
        return json.get("port").getAsInt();
    }

    /** The process id of that Eclipse. */
    public long pid() {
        return json.get("pid").getAsLong();
    }

    /** The workspace directory of that Eclipse. */
    public String workspace() {
        return text("workspace");
    }

    /** When the server started, as an ISO-8601 instant; newer instances sort first. */
    public String started() {
        return text("started");
    }

    /** Whether the process is still alive, which rules out files left by a crash. */
    public boolean isAlive() {
        return ProcessHandle.of(pid()).map(ProcessHandle::isAlive).orElse(false);
    }

    /** The instance file's content. */
    public JsonObject json() {
        return json.deepCopy();
    }

    /** A string field, or "" when it is missing. */
    private String text(String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
