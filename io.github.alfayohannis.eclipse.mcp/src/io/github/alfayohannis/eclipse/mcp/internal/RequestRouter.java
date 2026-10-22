package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.function.Supplier;

import com.google.gson.JsonObject;

/**
 * Decides what each request gets: the access checks first, then the MCP
 * endpoint at /mcp or the REST API under /api. Both front ends call the same
 * tools.
 */
final class RequestRouter {

    private static final String CORS_HEADERS = "Authorization, Content-Type, Accept, Mcp-Session-Id,"
            + " MCP-Protocol-Version, Last-Event-ID";

    private final McpProtocol protocol;
    private final RestApi restApi;
    private final AccessPolicy accessPolicy;

    /** A router over the two front ends with the given access rules. */
    RequestRouter(McpProtocol protocol, RestApi restApi, AccessPolicy accessPolicy) {
        this.protocol = protocol;
        this.restApi = restApi;
        this.accessPolicy = accessPolicy;
    }

    /** Lets the REST status endpoint describe the server it runs in. */
    void setServerInfo(Supplier<JsonObject> serverInfo) {
        restApi.setServerInfo(serverInfo);
    }

    /** The response to one request; a bug in a front end becomes a 500 instead of a dropped connection. */
    HttpResponse route(HttpRequest request) {
        try {
            return routeChecked(request);
        } catch (RuntimeException e) {
            Log.error("The MCP server failed on " + request.method() + " " + request.target(), e);
            return HttpResponse.json(500, RestApi.errorJson("Internal error: " + e));
        }
    }

    /** Applies the path, origin, host and token checks in that order, then dispatches. */
    private HttpResponse routeChecked(HttpRequest request) {
        String path = request.path();
        boolean mcp = "/mcp".equals(path);
        if (!mcp && !"/api".equals(path) && !path.startsWith("/api/")) {
            return HttpResponse.json(404, RestApi.errorJson("Not found: " + path + "; use /mcp or /api"));
        }
        String origin = request.header("Origin");
        if (!accessPolicy.allowsOrigin(origin)) {
            return HttpResponse.json(403, RestApi.errorJson("Origin not allowed: " + origin));
        }
        if (!accessPolicy.allowsHost(request.header("Host"))) {
            return HttpResponse.json(403, RestApi.errorJson("Host not allowed: " + request.header("Host")));
        }
        HttpResponse response = dispatch(request, mcp);
        return origin == null ? response : withCors(response, origin);
    }

    /** Answers CORS preflights without a token, everything else only with it. */
    private HttpResponse dispatch(HttpRequest request, boolean mcp) {
        if ("OPTIONS".equals(request.method())) {
            return HttpResponse.empty(204).header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", CORS_HEADERS).header("Access-Control-Max-Age", "600");
        }
        if (!accessPolicy.hasToken(request.header("Authorization"))) {
            return HttpResponse.json(401, RestApi.errorJson("Missing or wrong bearer token; send"
                    + " Authorization: Bearer <token>")).header("WWW-Authenticate", "Bearer");
        }
        return mcp ? answerMcp(request) : restApi.handle(request);
    }

    /**
     * The Streamable HTTP rules: POST carries JSON-RPC, DELETE ends a session
     * (there are none, so it just succeeds), and GET would open an SSE stream,
     * which this server does not offer.
     */
    private HttpResponse answerMcp(HttpRequest request) {
        if ("DELETE".equals(request.method())) {
            return HttpResponse.empty(200);
        }
        if (!"POST".equals(request.method())) {
            return HttpResponse.json(405, RestApi.errorJson("The MCP endpoint takes POST; it offers no SSE stream"))
                    .header("Allow", "POST, DELETE");
        }
        String version = request.header("MCP-Protocol-Version");
        if (version != null && !McpProtocol.PROTOCOL_VERSIONS.contains(version.trim())) {
            return HttpResponse.json(400, RestApi.errorJson("Unsupported MCP-Protocol-Version: " + version));
        }
        String reply = protocol.handle(request.bodyText());
        return reply == null ? HttpResponse.empty(202) : HttpResponse.json(200, reply);
    }

    /** Lets a local web page, such as the MCP Inspector, read the response. */
    private static HttpResponse withCors(HttpResponse response, String origin) {
        return response.header("Access-Control-Allow-Origin", origin).header("Vary", "Origin")
                .header("Access-Control-Expose-Headers", "Mcp-Session-Id");
    }
}
