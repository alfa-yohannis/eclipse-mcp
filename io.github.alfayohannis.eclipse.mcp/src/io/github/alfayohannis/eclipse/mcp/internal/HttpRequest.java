package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One HTTP/1.1 request read from a connection: request line, headers and a
 * body sent with Content-Length or chunked encoding. The limits keep a broken
 * or hostile client from filling the memory.
 */
final class HttpRequest {

    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

    private final String method;
    private final String target;
    private final String version;
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private byte[] body = new byte[0];

    /** A request with its request line; headers and body are filled in by {@link #read}. */
    private HttpRequest(String method, String target, String version) {
        this.method = method;
        this.target = target;
        this.version = version;
    }

    /**
     * Reads the next request, or returns null when the client closed the
     * connection between requests. It answers "100 Continue" when asked, as
     * curl does for larger bodies.
     */
    static HttpRequest read(InputStream in, OutputStream out) throws IOException {
        String requestLine = firstLine(in);
        if (requestLine == null) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/1.")) {
            throw new BadRequestException(400, "Malformed request line");
        }
        HttpRequest request = new HttpRequest(parts[0].toUpperCase(Locale.ROOT), parts[1], parts[2]);
        request.readHeaders(in, requestLine.length());
        if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
            out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
        request.body = request.readBody(in);
        return request;
    }

    /** The method in upper case, such as POST. */
    String method() {
        return method;
    }

    /** The request target as sent, with any query string. */
    String target() {
        return target;
    }

    /** The body bytes, empty when there is none. */
    byte[] body() {
        return body;
    }

    /** The body as UTF-8 text. */
    String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** A header value, or null; repeated headers are joined with commas. */
    String header(String name) {
        return headers.get(name);
    }

    /** The path without query string and without a trailing slash. */
    String path() {
        int query = target.indexOf('?');
        String path = query >= 0 ? target.substring(0, query) : target;
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /** Whether the client wants the connection kept open, by the rules of its HTTP version. */
    boolean keepAlive() {
        String connection = header("Connection");
        if ("HTTP/1.0".equals(version)) {
            return "keep-alive".equalsIgnoreCase(connection);
        }
        return connection == null || !connection.toLowerCase(Locale.ROOT).contains("close");
    }

    /** The request line, skipping the empty lines some clients send between requests; null at the end. */
    private static String firstLine(InputStream in) throws IOException {
        String line = readLine(in, true);
        while (line != null && line.isEmpty()) {
            line = readLine(in, true);
        }
        return line;
    }

    /** Reads header lines until the empty line, enforcing the size limit. */
    private void readHeaders(InputStream in, int bytesSoFar) throws IOException {
        int headerBytes = bytesSoFar;
        for (String line = readLine(in, false); !line.isEmpty(); line = readLine(in, false)) {
            headerBytes += line.length();
            if (headerBytes > MAX_HEADER_BYTES) {
                throw new BadRequestException(431, "Request headers are too large");
            }
            addHeader(line);
        }
    }

    /** Parses one "Name: value" line. */
    private void addHeader(String line) throws BadRequestException {
        int colon = line.indexOf(':');
        if (colon <= 0) {
            throw new BadRequestException(400, "Malformed header line");
        }
        headers.merge(line.substring(0, colon).trim(), line.substring(colon + 1).trim(), HttpRequest::joinValues);
    }

    /** Joins the values of a repeated header the way HTTP defines it. */
    private static String joinValues(String first, String second) {
        return first + ", " + second;
    }

    /** Reads the body by Transfer-Encoding or Content-Length; none means empty. */
    private byte[] readBody(InputStream in) throws IOException {
        String encoding = header("Transfer-Encoding");
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedBody(in);
        }
        String length = header("Content-Length");
        return length == null ? new byte[0] : in.readNBytes(contentLength(length));
    }

    /** The Content-Length as a number within the body limit. */
    private static int contentLength(String header) throws BadRequestException {
        long size;
        try {
            size = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException(400, "Malformed Content-Length");
        }
        if (size < 0 || size > MAX_BODY_BYTES) {
            throw new BadRequestException(413, "Request body is too large");
        }
        return (int) size;
    }

    /** Reads chunks until the zero-size one, then skips the trailers. */
    private static byte[] readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int size = chunkSize(readLine(in, false)); size > 0; size = chunkSize(readLine(in, false))) {
            if (body.size() + size > MAX_BODY_BYTES) {
                throw new BadRequestException(413, "Request body is too large");
            }
            body.write(in.readNBytes(size));
            readLine(in, false);
        }
        while (!readLine(in, false).isEmpty()) {
            // skip trailer headers
        }
        return body.toByteArray();
    }

    /** The size from a chunk header line, ignoring chunk extensions. */
    private static int chunkSize(String line) throws BadRequestException {
        int semicolon = line.indexOf(';');
        try {
            int size = Integer.parseInt((semicolon >= 0 ? line.substring(0, semicolon) : line).trim(), 16);
            if (size < 0) {
                throw new NumberFormatException();
            }
            return size;
        } catch (NumberFormatException e) {
            throw new BadRequestException(400, "Malformed chunk size");
        }
    }

    /**
     * Reads a line without its CR LF. At the end of the stream it returns null
     * when that is allowed (between requests) and fails otherwise.
     */
    private static String readLine(InputStream in, boolean endAllowed) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next = in.read();
        while (next != -1 && next != '\n') {
            if (line.size() >= MAX_HEADER_BYTES) {
                throw new BadRequestException(431, "Request line or header is too long");
            }
            line.write(next);
            next = in.read();
        }
        if (next == -1 && line.size() == 0) {
            if (endAllowed) {
                return null;
            }
            throw new IOException("Connection closed in the middle of a request");
        }
        String text = line.toString(StandardCharsets.ISO_8859_1);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }
}
