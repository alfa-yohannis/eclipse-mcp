package io.github.alfayohannis.eclipse.mcp.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

/**
 * Where the client connects and with which token. Explicit values win, then
 * the environment (ECLIPSE_MCP_URL, ECLIPSE_MCP_PORT, ECLIPSE_MCP_WORKSPACE,
 * ECLIPSE_MCP_TOKEN, ECLIPSE_MCP_CONFIG), then the files the plug-in writes in
 * ~/.config/eclipse-mcp.
 */
public final class ClientSettings {

    private final URI url;
    private final Integer port;
    private final String workspace;
    private final String token;
    private final Path configDirectory;
    private final Duration timeout;

    /** Settings from the builder; unset values stay null until resolved. */
    private ClientSettings(Builder builder) {
        this.url = builder.url;
        this.port = builder.port;
        this.workspace = builder.workspace;
        this.token = builder.token;
        this.configDirectory = builder.configDirectory;
        this.timeout = builder.timeout;
    }

    /** Starts empty settings, which resolve everything from the environment and the files. */
    public static Builder builder() {
        return new Builder();
    }

    /** The configuration directory: explicit, else ECLIPSE_MCP_CONFIG, else ~/.config/eclipse-mcp. */
    public Path configDirectory() {
        if (configDirectory != null) {
            return configDirectory;
        }
        String fromEnvironment = System.getenv("ECLIPSE_MCP_CONFIG");
        return fromEnvironment != null && !fromEnvironment.isBlank() ? Paths.get(fromEnvironment)
                : Paths.get(System.getProperty("user.home"), ".config", "eclipse-mcp");
    }

    /** How long one call may take; builds and dialogs can be slow. */
    public Duration timeout() {
        return timeout;
    }

    /**
     * The server address: an explicit URL or port, else the environment, else
     * the newest running instance (optionally the one whose workspace matches).
     */
    public URI resolveBaseUrl() {
        if (url != null) {
            return url;
        }
        if (port != null) {
            return localUrl(port);
        }
        String environmentUrl = System.getenv("ECLIPSE_MCP_URL");
        if (environmentUrl != null && !environmentUrl.isBlank()) {
            return URI.create(environmentUrl.trim());
        }
        String environmentPort = System.getenv("ECLIPSE_MCP_PORT");
        if (environmentPort != null && !environmentPort.isBlank()) {
            return localUrl(Integer.parseInt(environmentPort.trim()));
        }
        return newestInstance().baseUrl();
    }

    /** The token: explicit, else ECLIPSE_MCP_TOKEN, else the token file the plug-in created. */
    public String resolveToken() {
        if (token != null) {
            return token;
        }
        String fromEnvironment = System.getenv("ECLIPSE_MCP_TOKEN");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        Path file = configDirectory().resolve("token");
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            throw new EclipseMcpException("No token: set ECLIPSE_MCP_TOKEN or start Eclipse with the MCP plug-in once,"
                    + " which creates " + file, e);
        }
    }

    /** The newest running instance that matches the workspace filter. */
    private Instance newestInstance() {
        String filter = workspace != null ? workspace : System.getenv("ECLIPSE_MCP_WORKSPACE");
        InstanceDirectory instances = new InstanceDirectory(configDirectory());
        Optional<Instance> instance = instances.newest(filter == null || filter.isBlank() ? null : filter);
        return instance.orElseThrow(() -> notRunning(instances, filter));
    }

    /** The failure for no running instance, naming where the client looked. */
    private static EclipseNotRunningException notRunning(InstanceDirectory instances, String filter) {
        String which = filter == null || filter.isBlank() ? "" : " with a workspace matching \"" + filter + "\"";
        return new EclipseNotRunningException("No running Eclipse with the MCP plug-in" + which + " announces itself in "
                + instances.path() + ". Start Eclipse with the plug-in installed (or MCP > Start Server), or pass a"
                + " URL or port.");
    }

    /** The loopback address of a port. */
    private static URI localUrl(int port) {
        return URI.create("http://127.0.0.1:" + port);
    }

    /** Collects settings; everything is optional. */
    public static final class Builder {
        private URI url;
        private Integer port;
        private String workspace;
        private String token;
        private Path configDirectory;
        private Duration timeout = Duration.ofMinutes(10);

        /** Use {@link ClientSettings#builder()}. */
        private Builder() {
        }

        /** Connects to this address, such as http://127.0.0.1:8765, instead of discovering one. */
        public Builder url(String url) {
            this.url = url == null ? null : URI.create(url);
            return this;
        }

        /** Connects to this local port instead of discovering one. */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /** Picks the running Eclipse whose workspace path contains this text. */
        public Builder workspace(String workspace) {
            this.workspace = workspace;
            return this;
        }

        /** Uses this token instead of the environment or the token file. */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /** Reads the token and instance files from this directory instead of ~/.config/eclipse-mcp. */
        public Builder configDirectory(Path configDirectory) {
            this.configDirectory = configDirectory;
            return this;
        }

        /** How long one call may take (default 10 minutes). */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Creates the settings; values left unset resolve later from the environment and the files. */
        public ClientSettings build() {
            return new ClientSettings(this);
        }
    }
}
