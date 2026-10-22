package io.github.alfayohannis.eclipse.mcp.client.mcp;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.alfayohannis.eclipse.mcp.client.ClientSettings;
import io.github.alfayohannis.eclipse.mcp.client.EclipseMcpException;
import io.github.alfayohannis.eclipse.mcp.client.EclipseNotRunningException;

/**
 * Forwards JSON-RPC messages to the plug-in's /mcp endpoint. It looks the
 * running Eclipse up for every message, so the proxy keeps working when
 * Eclipse restarts on another port.
 */
final class PluginLink {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ClientSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** A link that finds Eclipse through these settings. */
    PluginLink(ClientSettings settings) {
        this.settings = settings;
    }

    /**
     * Sends the message and returns the reply, or null when the plug-in
     * accepted it without one (a notification).
     *
     * @throws EclipseNotRunningException when no Eclipse can be reached
     */
    JsonElement forward(JsonObject message) {
        URI endpoint = settings.resolveBaseUrl().resolve("/mcp");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(settings.timeout())
                .header("Authorization", "Bearer " + settings.resolveToken()).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(message.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = send(request, endpoint);
        if (response.statusCode() == 202) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new EclipseMcpException("Eclipse answered HTTP " + response.statusCode() + ": " + response.body());
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (JsonParseException e) {
            throw new EclipseMcpException("Eclipse answered with something that is not JSON", e);
        }
    }

    /** Sends the request; a refused connection means Eclipse is gone. */
    private HttpResponse<String> send(HttpRequest request, URI endpoint) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw new EclipseNotRunningException("Nothing answers at " + endpoint + "; start Eclipse with the MCP"
                    + " server (MCP > Start Server)", e);
        } catch (java.io.IOException e) {
            throw new EclipseMcpException("Request to " + endpoint + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EclipseMcpException("Interrupted while waiting for Eclipse", e);
        }
    }
}
