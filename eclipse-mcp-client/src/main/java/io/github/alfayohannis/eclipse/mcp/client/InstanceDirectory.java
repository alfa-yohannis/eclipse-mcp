package io.github.alfayohannis.eclipse.mcp.client;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The instance files in ~/.config/eclipse-mcp/instances, through which every
 * running Eclipse announces its server, so clients need no port setting.
 */
public final class InstanceDirectory {

    private final Path directory;

    /** The instances directory below a configuration directory. */
    public InstanceDirectory(Path configDirectory) {
        this.directory = configDirectory.resolve("instances");
    }

    /** The directory that holds the instance files. */
    public Path path() {
        return directory;
    }

    /** The running instances, newest first; files of dead processes and unreadable files are skipped. */
    public List<Instance> running() {
        List<Instance> instances = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return instances;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                read(file).filter(Instance::isAlive).ifPresent(instances::add);
            }
        } catch (IOException e) {
            throw new EclipseMcpException("Cannot read " + directory + ": " + e.getMessage(), e);
        }
        instances.sort(Comparator.comparing(Instance::started).reversed());
        return instances;
    }

    /** The running instance on the port, if any. */
    public Optional<Instance> onPort(int port) {
        for (Instance instance : running()) {
            if (instance.port() == port) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /** The newest running instance whose workspace path contains the text, or the newest of all. */
    public Optional<Instance> newest(String workspaceFilter) {
        for (Instance instance : running()) {
            if (workspaceFilter == null || instance.workspace().contains(workspaceFilter)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /** One instance file, or empty when it is not a valid one. */
    private static Optional<Instance> read(Path file) {
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            boolean valid = json.isJsonObject() && json.getAsJsonObject().has("url") && json.getAsJsonObject().has("pid");
            return valid ? Optional.of(new Instance(json.getAsJsonObject())) : Optional.empty();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return Optional.empty();
        }
    }
}
