package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** One HTTP response: status, headers and body, written with Content-Length so connections can stay open. */
final class HttpResponse {

    private static final Map<Integer, String> REASONS = Map.ofEntries(Map.entry(200, "OK"),
            Map.entry(202, "Accepted"), Map.entry(204, "No Content"), Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"), Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"), Map.entry(413, "Payload Too Large"),
            Map.entry(422, "Unprocessable Entity"), Map.entry(431, "Request Header Fields Too Large"),
            Map.entry(500, "Internal Server Error"));

    private final int status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final byte[] body;

    /** Use the factory methods. */
    private HttpResponse(int status, byte[] body) {
        this.status = status;
        this.body = body;
    }

    /** A response with a JSON body. */
    static HttpResponse json(int status, String json) {
        return new HttpResponse(status, json.getBytes(StandardCharsets.UTF_8)).header("Content-Type",
                "application/json; charset=utf-8");
    }

    /** A response without a body, such as 202 Accepted for notifications. */
    static HttpResponse empty(int status) {
        return new HttpResponse(status, new byte[0]);
    }

    /** Adds or replaces a header. */
    HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** The status code, for tests and logs. */
    int status() {
        return status;
    }

    /** Writes the response and tells the client whether the connection stays open. */
    void write(OutputStream out, boolean keepAlive) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(REASONS.getOrDefault(status, "Status")).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        if (status != 204) {
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("Cache-Control: no-store\r\n");
        head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
