package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The ready-to-paste client setups shown in the status dialog: the stdio
 * launcher, which reads the token itself, and plain HTTP with the token in a
 * header, for Claude Code, other MCP clients and curl.
 */
final class ClientSetupText {

    private static final String LAUNCHER_PLACEHOLDER = "/path/to/eclipse-mcp/eclipse-mcp";

    /** Not instantiable: the class only has static members. */
    private ClientSetupText() {
    }

    /** The setups for a server at the base URL. */
    static String forServer(String baseUrl) {
        String token = token();
        String mcpUrl = baseUrl + "/mcp";
        return "# Claude Code through the stdio launcher, which reads the token itself:\n"
                + "claude mcp add eclipse -s user -- " + launcherPath() + " mcp\n\n"
                + "# Claude Code over HTTP:\n"
                + "claude mcp add --transport http eclipse " + mcpUrl + " --header \"Authorization: Bearer " + token
                + "\"\n\n"
                + "# Other MCP clients (JSON):\n"
                + "{\"mcpServers\": {\"eclipse\": {\"type\": \"http\", \"url\": \"" + mcpUrl + "\", \"headers\":"
                + " {\"Authorization\": \"Bearer " + token + "\"}}}}\n\n"
                + "# REST API:\n"
                + "curl -H \"Authorization: Bearer " + token + "\" " + baseUrl + "/api/status\n";
    }

    /** The token, or a pointer to its file when it cannot be read. */
    private static String token() {
        try {
            return TokenFile.token();
        } catch (IOException e) {
            return "<token from " + TokenFile.path() + ">";
        }
    }

    /** The launcher path that the launcher recorded on its last run, or a placeholder. */
    private static String launcherPath() {
        Path recorded = Config.launcherFile();
        try {
            return Files.isRegularFile(recorded) ? Files.readString(recorded).trim() : LAUNCHER_PLACEHOLDER;
        } catch (IOException e) {
            return LAUNCHER_PLACEHOLDER;
        }
    }
}
