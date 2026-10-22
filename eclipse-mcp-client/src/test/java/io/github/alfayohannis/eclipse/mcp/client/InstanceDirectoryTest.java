package io.github.alfayohannis.eclipse.mcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Discovery of running Eclipse instances through the instance files. */
class InstanceDirectoryTest {

    @TempDir
    Path config;

    /** Files of dead processes and broken files are ignored; live ones come newest first. */
    @Test
    void listsOnlyLiveInstancesNewestFirst() throws IOException {
        long livePid = ProcessHandle.current().pid();
        write("8765.json", 8765, livePid, "/work/old", "2026-01-01T00:00:00Z");
        write("8766.json", 8766, livePid, "/work/new", "2026-06-01T00:00:00Z");
        write("8767.json", 8767, Long.MAX_VALUE - 1, "/work/dead", "2026-07-01T00:00:00Z");
        Files.writeString(config.resolve("instances").resolve("broken.json"), "{not json");

        List<Instance> running = new InstanceDirectory(config).running();

        assertEquals(2, running.size());
        assertEquals(8766, running.get(0).port());
        assertEquals(8765, running.get(1).port());
    }

    /** The workspace filter picks the newest instance whose workspace matches. */
    @Test
    void filtersByWorkspace() throws IOException {
        long livePid = ProcessHandle.current().pid();
        write("8765.json", 8765, livePid, "/work/daily", "2026-01-01T00:00:00Z");
        write("8766.json", 8766, livePid, "/work/runtime", "2026-06-01T00:00:00Z");

        InstanceDirectory directory = new InstanceDirectory(config);

        assertEquals(8765, directory.newest("daily").orElseThrow().port());
        assertEquals(8766, directory.newest(null).orElseThrow().port());
        assertTrue(directory.newest("nothing").isEmpty());
        assertEquals(8765, directory.onPort(8765).orElseThrow().port());
    }

    /** A missing directory means no instances, not an error. */
    @Test
    void missingDirectoryMeansNoInstances() {
        assertTrue(new InstanceDirectory(config.resolve("absent")).running().isEmpty());
    }

    /** Writes an instance file like the plug-in does. */
    private void write(String name, int port, long pid, String workspace, String started) throws IOException {
        Files.createDirectories(config.resolve("instances"));
        Files.writeString(config.resolve("instances").resolve(name), "{\"url\":\"http://127.0.0.1:" + port
                + "\",\"port\":" + port + ",\"pid\":" + pid + ",\"workspace\":\"" + workspace + "\",\"started\":\""
                + started + "\"}");
    }
}
