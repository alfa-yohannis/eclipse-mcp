package io.github.alfayohannis.eclipse.mcp.internal;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.runtime.Platform;

/**
 * Settings of the server. A system property or an environment variable wins,
 * so test runs and second instances can override them; otherwise the port and
 * the start-up behaviour come from the preferences (Window &gt; Preferences &gt;
 * MCP Server) and the files live in ~/.config/eclipse-mcp.
 */
public final class Config {

    public static final String BUNDLE_ID = "io.github.alfayohannis.eclipse.mcp";
    public static final String EXTENSION_POINT = BUNDLE_ID + ".tools";
    static final String PREF_AUTO_START = "autoStart";
    static final String PREF_PORT = "port";
    static final int DEFAULT_PORT = 8765;
    /** How many ports above the preferred one a second Eclipse instance may try. */
    static final int PORT_TRIES = 10;

    /** Not instantiable: the class only has static members. */
    private Config() {
    }

    /** The first port to try: the override if set, else the preference. */
    static int port() {
        String override = override("eclipse.mcp.port", "ECLIPSE_MCP_PORT");
        if (override != null) {
            return Integer.parseInt(override.trim());
        }
        return Platform.getPreferencesService().getInt(BUNDLE_ID, PREF_PORT, DEFAULT_PORT, null);
    }

    /** An overridden port is used as is; a preferred port moves up when another instance holds it. */
    static boolean portFixed() {
        return override("eclipse.mcp.port", "ECLIPSE_MCP_PORT") != null;
    }

    /** Whether the server starts with Eclipse. */
    static boolean autoStart() {
        return Platform.getPreferencesService().getBoolean(BUNDLE_ID, PREF_AUTO_START, true, null);
    }

    /** The directory of the token, instance and launcher files. */
    static Path directory() {
        String override = override("eclipse.mcp.config", "ECLIPSE_MCP_CONFIG");
        return override != null ? Paths.get(override.trim())
                : Paths.get(System.getProperty("user.home"), ".config", "eclipse-mcp");
    }

    /** Where clients find the running servers, one JSON file per port. */
    static Path instancesDirectory() {
        return directory().resolve("instances");
    }

    /** The client launcher writes its own path here when it runs, so the status dialog can show it. */
    static Path launcherFile() {
        return directory().resolve("launcher");
    }

    /** The token override from a system property or the environment, or null. */
    static String tokenOverride() {
        return override("eclipse.mcp.token", "ECLIPSE_MCP_TOKEN");
    }

    /** The value of the system property, else of the environment variable, or null when neither is set. */
    private static String override(String property, String environmentVariable) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? null : value;
    }
}
