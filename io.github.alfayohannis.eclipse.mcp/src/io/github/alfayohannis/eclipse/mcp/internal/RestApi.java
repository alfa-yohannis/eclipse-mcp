package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolRegistry;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * The REST API under /api, for programs that do not speak MCP. It calls the
 * same tools as the MCP endpoint: GET /api/status, GET /api/tools,
 * GET /api/tools/{name} and POST /api/tools/{name} with the arguments as the
 * JSON body. Tool errors are HTTP 422, bugs 500.
 */
final class RestApi {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String TOOLS_PREFIX = "tools/";

    private final ToolRegistry registry;
    private Supplier<JsonObject> serverInfo = JsonObject::new;

    /** An API over the tools of this registry. */
    RestApi(ToolRegistry registry) {
        this.registry = registry;
    }

    /** Lets GET /api/status describe the server it runs in. */
    void setServerInfo(Supplier<JsonObject> serverInfo) {
        this.serverInfo = serverInfo;
    }

    /** {"error": message} as JSON text, the body of every error response. */
    static String errorJson(String message) {
        return GSON.toJson(errorObject(message));
    }

    /** Answers a request whose path starts with /api. */
    HttpResponse handle(HttpRequest request) {
        String path = request.path();
        String rest = path.length() > "/api".length() ? path.substring("/api/".length()) : "";
        String method = request.method();
        if (rest.isEmpty() || "status".equals(rest)) {
            return "GET".equals(method) ? status() : notAllowed("GET");
        }
        if ("tools".equals(rest)) {
            return "GET".equals(method) ? listTools() : notAllowed("GET");
        }
        if (rest.startsWith(TOOLS_PREFIX) && rest.indexOf('/', TOOLS_PREFIX.length()) < 0) {
            return handleTool(method, rest.substring(TOOLS_PREFIX.length()), request);
        }
        return HttpResponse.json(404, errorJson("Not found: " + path));
    }

    /** GET describes the tool, POST calls it. */
    private HttpResponse handleTool(String method, String name, HttpRequest request) {
        if ("GET".equals(method)) {
            return describeTool(name);
        }
        if ("POST".equals(method)) {
            return callTool(name, request.bodyText());
        }
        return notAllowed("GET, POST");
    }

    /** The server description plus eclipse_status, so one request shows whether everything works. */
    private HttpResponse status() {
        JsonObject json = serverInfo.get();
        json.addProperty("tools", registry.tools().size());
        ToolResult eclipse = registry.call("eclipse_status", new JsonObject());
        if (eclipse.isError()) {
            json.addProperty("eclipseError", eclipse.text());
        } else {
            json.add("eclipse", eclipse.structuredContent());
        }
        return HttpResponse.json(200, GSON.toJson(json));
    }

    /** The same descriptions as MCP tools/list. */
    private HttpResponse listTools() {
        JsonArray tools = new JsonArray();
        for (Tool tool : registry.tools()) {
            tools.add(tool.describe());
        }
        JsonObject json = new JsonObject();
        json.add("tools", tools);
        return HttpResponse.json(200, GSON.toJson(json));
    }

    /** One tool's description, or 404. */
    private HttpResponse describeTool(String name) {
        Optional<Tool> tool = registry.find(name);
        return tool.isPresent() ? HttpResponse.json(200, GSON.toJson(tool.get().describe())) : unknownTool(name);
    }

    /** Calls a tool with the body as arguments and maps the outcome to a status code. */
    private HttpResponse callTool(String name, String body) {
        JsonObject arguments;
        try {
            arguments = parseArguments(body);
        } catch (IllegalArgumentException e) {
            return HttpResponse.json(400, errorJson(e.getMessage()));
        }
        ToolResult result;
        try {
            result = registry.call(name, arguments);
        } catch (NoSuchElementException e) {
            return unknownTool(name);
        }
        return resultResponse(result);
    }

    /** The arguments object from the body; an empty body means no arguments. */
    private static JsonObject parseArguments(String body) {
        if (body.isBlank()) {
            return new JsonObject();
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("The body is not valid JSON: " + e.getMessage());
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("The body must be a JSON object of arguments");
        }
        return parsed.getAsJsonObject();
    }

    /** 200 with the result; an error result keeps its JSON and gets an "error" field and 422 or 500. */
    private static HttpResponse resultResponse(ToolResult result) {
        JsonObject json = result.toJson();
        if (!result.isError()) {
            return HttpResponse.json(200, GSON.toJson(json));
        }
        json.addProperty("error", result.text());
        return HttpResponse.json(result.isInternalError() ? 500 : 422, GSON.toJson(json));
    }

    /** 404 for a tool name nobody registered. */
    private static HttpResponse unknownTool(String name) {
        return HttpResponse.json(404, errorJson("Unknown tool: " + name + "; GET /api/tools lists them"));
    }

    /** 405 with the methods the path takes. */
    private static HttpResponse notAllowed(String allow) {
        return HttpResponse.json(405, errorJson("Method not allowed; use " + allow)).header("Allow", allow);
    }

    /** {"error": message}. */
    private static JsonObject errorObject(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return json;
    }
}
