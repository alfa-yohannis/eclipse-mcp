package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The file that announces a running server in ~/.config/eclipse-mcp/instances,
 * named after its port, so clients find the port without being told. Files of
 * servers whose process died are removed when a server starts.
 */
final class InstanceFile {

    private final Path directory;
    private final Path file;

    /** The instance file of the server on this port. */
    InstanceFile(Path directory, int port) {
        this.directory = directory;
        this.file = directory.resolve(port + ".json");
    }

    /** Writes the server description after removing stale files; a failure is only logged. */
    void write(JsonObject description) {
        try {
            Files.createDirectories(directory);
            removeStaleFiles();
            Files.writeString(file, description + "\n");
        } catch (IOException | RuntimeException e) {
            Log.error("Could not write the MCP instance file " + file, e);
        }
    }

    /** Removes the file when the server stops. */
    void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Log.error("Could not delete the MCP instance file " + file, e);
        }
    }

    /** Deletes the files whose process no longer runs. */
    private void removeStaleFiles() throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path candidate : files) {
                if (!isAlive(candidate)) {
                    Files.deleteIfExists(candidate);
                }
            }
        }
    }

    /** Whether the process named in an instance file still runs; unreadable files count as dead. */
    private static boolean isAlive(Path candidate) {
        try {
            JsonElement pid = JsonParser.parseString(Files.readString(candidate)).getAsJsonObject().get("pid");
            return pid != null && ProcessHandle.of(pid.getAsLong()).map(ProcessHandle::isAlive).orElse(false);
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException
                | NumberFormatException e) {
            return false;
        }
    }
}
