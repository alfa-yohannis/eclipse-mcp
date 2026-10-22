import java.nio.file.Path;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.client.EclipseClient;
import io.github.alfayohannis.eclipse.mcp.client.EclipseNotRunningException;
import io.github.alfayohannis.eclipse.mcp.client.ToolFailedException;

/**
 * Uses the Eclipse MCP client library from a plain Java program: lists the
 * workspace projects and the errors, then saves a screenshot of the Eclipse
 * window. Run it from the repository root after building the client:
 *
 * <pre>
 * ./eclipse-mcp status        # builds the client on first run
 * java -cp eclipse-mcp-client/target/eclipse-mcp-client-0.1.0-all.jar examples/ClientExample.java
 * </pre>
 */
public class ClientExample {

    /** Connects to the newest running Eclipse and prints what it finds. */
    public static void main(String[] args) throws Exception {
        EclipseClient eclipse;
        try {
            eclipse = EclipseClient.connect();
        } catch (EclipseNotRunningException e) {
            System.err.println(e.getMessage());
            System.exit(3);
            return;
        }
        System.out.println("Connected to " + eclipse.baseUrl());
        printProjects(eclipse.listProjects());
        printErrors(eclipse.getProblems("error", null, 10));
        try {
            Path file = eclipse.screenshot("window", null, null, 0.5).save(Path.of("eclipse-window.png"));
            System.out.println("Saved " + file);
        } catch (ToolFailedException e) {
            System.out.println("No screenshot: " + e.result().text());
        }
    }

    /** One line per project with its problem counts. */
    private static void printProjects(JsonObject result) {
        for (JsonElement element : result.getAsJsonArray("projects")) {
            JsonObject project = element.getAsJsonObject();
            String counts = project.has("errors") ? project.get("errors") + " errors, " + project.get("warnings")
                    + " warnings" : "closed";
            System.out.println("Project " + project.get("name").getAsString() + ": " + counts);
        }
    }

    /** The error markers, one per line. */
    private static void printErrors(JsonObject result) {
        System.out.println(result.get("errors").getAsInt() + " errors in the workspace");
        for (JsonElement element : result.getAsJsonArray("problems")) {
            JsonObject problem = element.getAsJsonObject();
            System.out.println("  " + problem.get("path").getAsString() + ": " + problem.get("message").getAsString());
        }
    }
}
