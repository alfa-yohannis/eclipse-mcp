package io.github.alfayohannis.eclipse.mcp.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Sends authenticated JSON requests to the plug-in and turns connection and
 * status problems into the client's exceptions.
 */
final class HttpTransport {

    /** A response: status and JSON body (an empty object when there was no body). */
    static final class Reply {
        final int status;
        final JsonElement body;

        /** A reply with its status and parsed body. */
        Reply(int status, JsonElement body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final URI baseUrl;
    private final String token;
    private final Duration timeout;

    /** A transport to the server at the base URL with the token. */
    HttpTransport(URI baseUrl, String token, Duration timeout) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.timeout = timeout;
    }

    /** The server address. */
    URI baseUrl() {
        return baseUrl;
    }

    /** GET a path such as /api/status. */
    Reply get(String path) {
        return send(request(path).GET().build());
    }

    /** POST a JSON body to a path. */
    Reply post(String path, String json) {
        return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    /** A request builder with the token and the timeout. */
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(timeout).header("Authorization", "Bearer " + token)
                .header("Accept", "application/json, text/event-stream");
    }

    /** Sends the request; refused connections mean Eclipse is not running, 401 means a wrong token. */
    private Reply send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw new EclipseNotRunningException("Nothing answers at " + baseUrl + "; is Eclipse running with the MCP"
                    + " server started (MCP > Start Server)?", e);
        } catch (HttpTimeoutException e) {
            throw new EclipseMcpException("Eclipse did not answer within " + timeout.toSeconds() + " s", e);
        } catch (IOException e) {
            throw new EclipseMcpException("Request to " + request.uri() + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EclipseMcpException("Interrupted while waiting for Eclipse", e);
        }
        if (response.statusCode() == 401) {
            throw new EclipseMcpException("Eclipse refused the token (HTTP 401); the token file or ECLIPSE_MCP_TOKEN"
                    + " does not match the server's");
        }
        return new Reply(response.statusCode(), parse(response.body()));
    }

    /** The body as JSON; an empty body becomes an empty object, anything else that is not JSON an error. */
    private static JsonElement parse(String body) {
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(body);
        } catch (JsonParseException e) {
            throw new EclipseMcpException("The server answered with something that is not JSON: " + body, e);
        }
    }
}
