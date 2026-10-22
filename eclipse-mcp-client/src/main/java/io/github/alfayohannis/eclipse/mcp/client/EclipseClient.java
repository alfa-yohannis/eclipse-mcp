package io.github.alfayohannis.eclipse.mcp.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A client for the REST API of the Eclipse MCP plug-in. It finds a running
 * Eclipse through the instance files, or uses an explicit address, and offers
 * one typed method per tool plus {@link #call(String, JsonObject)} for any
 * tool, including ones that other plug-ins contribute.
 *
 * <pre>
 * EclipseClient eclipse = EclipseClient.connect();
 * System.out.println(eclipse.listProjects());
 * eclipse.screenshot("window", null, null, null).save(Path.of("window.png"));
 * </pre>
 */
public final class EclipseClient {

    private final HttpTransport transport;

    /** A client over a transport; use {@link #connect()} or {@link #connect(ClientSettings)}. */
    private EclipseClient(HttpTransport transport) {
        this.transport = transport;
    }

    /** Connects to the newest running Eclipse, or the one the environment names. */
    public static EclipseClient connect() {
        return connect(ClientSettings.builder().build());
    }

    /** Connects with explicit settings, such as a port or a token. */
    public static EclipseClient connect(ClientSettings settings) {
        return new EclipseClient(new HttpTransport(settings.resolveBaseUrl(), settings.resolveToken(), settings.timeout()));
    }

    /** The server address, such as http://127.0.0.1:8765. */
    public URI baseUrl() {
        return transport.baseUrl();
    }

    /** GET /api/status: the server's description plus eclipse_status. */
    public JsonObject serverStatus() {
        return expectObject(transport.get("/api/status"), "status");
    }

    /** GET /api/tools: the tools, including contributed ones. */
    public List<ToolInfo> tools() {
        JsonObject json = expectObject(transport.get("/api/tools"), "tools");
        List<ToolInfo> tools = new ArrayList<>();
        for (JsonElement tool : json.getAsJsonArray("tools")) {
            tools.add(new ToolInfo(tool.getAsJsonObject()));
        }
        return tools;
    }

    /**
     * Calls any tool with the arguments.
     *
     * @throws ToolFailedException when the tool reports an error
     */
    public ToolResult call(String tool, JsonObject arguments) {
        String path = "/api/tools/" + URI.create(encodePathSegment(tool)).toASCIIString();
        HttpTransport.Reply reply = transport.post(path, arguments == null ? "{}" : arguments.toString());
        if (reply.status == 404) {
            throw new EclipseMcpException(errorText(reply, "Unknown tool " + tool));
        }
        if (reply.status == 400) {
            throw new EclipseMcpException(errorText(reply, "Bad request"));
        }
        ToolResult result = new ToolResult(expectObject(reply, tool));
        if (reply.status >= 400 || result.isError()) {
            throw new ToolFailedException(tool, reply.status, result);
        }
        return result;
    }

    /** eclipse_status: version, workspace, window, editors, dialogs and running jobs. */
    public JsonObject eclipseStatus() {
        return structured("eclipse_status", Arguments.create());
    }

    /** list_projects: the projects with location, natures and problem counts. */
    public JsonObject listProjects() {
        return structured("list_projects", Arguments.create());
    }

    /** get_problems: problem markers of at least the severity (error, warning, info), optionally of one project. */
    public JsonObject getProblems(String severity, String project, Integer limit) {
        return structured("get_problems",
                Arguments.create().with("severity", severity).with("project", project).with("limit", limit));
    }

    /** refresh: refreshes a workspace path, or the whole workspace when null. */
    public String refresh(String path) {
        return text("refresh", Arguments.create().with("path", path));
    }

    /** build: builds a project or the workspace (kind incremental, full or clean) and waits for it. */
    public JsonObject build(String project, String kind, Integer timeoutSeconds) {
        return structured("build",
                Arguments.create().with("project", project).with("kind", kind).with("timeout_s", timeoutSeconds));
    }

    /** open_file: opens a workspace or file system path, optionally at a line or in a given editor. */
    public JsonObject openFile(String path, Integer line, String editorId) {
        return structured("open_file", Arguments.create().with("path", path).with("line", line).with("editor_id", editorId));
    }

    /** select_resource: selects a workspace resource in the Project Explorer. */
    public String selectResource(String path) {
        return text("select_resource", Arguments.create().with("path", path));
    }

    /** list_commands: commands whose id or name contains the filter. */
    public JsonObject listCommands(String filter, Integer limit) {
        return structured("list_commands", Arguments.create().with("filter", filter).with("limit", limit));
    }

    /** run_command: runs a command by id with optional parameters. */
    public JsonObject runCommand(String id, Map<String, String> parameters, Integer waitMs) {
        return structured("run_command",
                Arguments.create().with("id", id).with("parameters", parameters).with("wait_ms", waitMs));
    }

    /** list_menu: the menu bar, or the items under a path such as "File &gt; New". */
    public JsonObject listMenu(String path, Integer depth) {
        return structured("list_menu", Arguments.create().with("path", path).with("depth", depth));
    }

    /** click_menu: clicks a menu item by its label path. */
    public JsonObject clickMenu(String path, Integer waitMs) {
        return structured("click_menu", Arguments.create().with("path", path).with("wait_ms", waitMs));
    }

    /** list_shells: the open shells, and the widgets of one when asked. */
    public JsonObject listShells(String shell, boolean widgets) {
        return structured("list_shells", Arguments.create().with("shell", shell).with("widgets", widgets));
    }

    /** press_button: presses a button by label in a shell (the active dialog when null). */
    public JsonObject pressButton(String label, String shell) {
        return structured("press_button", Arguments.create().with("label", label).with("shell", shell));
    }

    /** set_text: sets an input field, the index-th one of the shell (from 0). */
    public JsonObject setText(String value, String shell, Integer index) {
        return structured("set_text", Arguments.create().with("value", value).with("shell", shell).with("index", index));
    }

    /** select_tree_item: selects an item by label path in a shell's tree or a view, and opens it when asked. */
    public JsonObject selectTreeItem(String path, String shell, String view, boolean open) {
        return structured("select_tree_item",
                Arguments.create().with("path", path).with("shell", shell).with("view", view).with("open", open));
    }

    /** list_tree_items: the items of a tree, under a path, to a depth. */
    public JsonObject listTreeItems(String path, String shell, String view, Integer depth) {
        return structured("list_tree_items",
                Arguments.create().with("path", path).with("shell", shell).with("view", view).with("depth", depth));
    }

    /** screenshot: a PNG of a shell, the window, a widget or the screen. */
    public Screenshot screenshot(String target, String shell, Integer widget, Double scale) {
        ToolResult result = call("screenshot",
                Arguments.create().with("target", target).with("shell", shell).with("widget", widget).with("scale", scale)
                        .toJson());
        List<byte[]> images = result.images();
        if (images.isEmpty()) {
            throw new EclipseMcpException("The screenshot tool returned no image");
        }
        return new Screenshot(images.get(0), result.structured() == null ? new JsonObject() : result.structured());
    }

    /** mouse_click: clicks at x,y relative to a shell or widget. */
    public JsonObject mouseClick(int x, int y, String shell, Integer widget, String button, Integer count) {
        return structured("mouse_click", Arguments.create().with("x", x).with("y", y).with("shell", shell)
                .with("widget", widget).with("button", button).with("count", count));
    }

    /** mouse_drag: drags from one point to another, relative to a shell or widget. */
    public JsonObject mouseDrag(int fromX, int fromY, int toX, int toY, String shell, Integer widget) {
        return structured("mouse_drag", Arguments.create().with("from_x", fromX).with("from_y", fromY).with("to_x", toX)
                .with("to_y", toY).with("shell", shell).with("widget", widget));
    }

    /** type_text: types text into the focused widget (method auto, insert or keys). */
    public JsonObject typeText(String text, String shell, Integer widget, String method) {
        return structured("type_text",
                Arguments.create().with("text", text).with("shell", shell).with("widget", widget).with("method", method));
    }

    /** press_keys: presses key combinations such as "ctrl+s". */
    public JsonObject pressKeys(String keys, String shell, Integer widget) {
        return structured("press_keys", Arguments.create().with("keys", keys).with("shell", shell).with("widget", widget));
    }

    /** list_launch_configurations: the configurations and the launches. */
    public JsonObject listLaunchConfigurations() {
        return structured("list_launch_configurations", Arguments.create());
    }

    /** launch: launches a configuration in run or debug mode. */
    public JsonObject launch(String name, String mode) {
        return structured("launch", Arguments.create().with("name", name).with("mode", mode));
    }

    /** terminate_launch: terminates the running launches of a configuration. */
    public String terminateLaunch(String name) {
        return text("terminate_launch", Arguments.create().with("name", name));
    }

    /** diagram_info: the open diagram's palette, shapes and connections (needs the Graphiti bundle). */
    public JsonObject diagramInfo(String editor) {
        return structured("diagram_info", Arguments.create().with("editor", editor));
    }

    /** diagram_create: creates a shape with a palette tool at x,y, optionally named, in feature or mouse mode. */
    public JsonObject diagramCreate(String tool, int x, int y, String name, String container, String mode) {
        return structured("diagram_create", Arguments.create().with("tool", tool).with("x", x).with("y", y)
                .with("name", name).with("container", container).with("mode", mode));
    }

    /** diagram_connect: connects two elements, with Sequence Flow when the tool is null. */
    public JsonObject diagramConnect(String source, String target, String tool, String mode) {
        return structured("diagram_connect",
                Arguments.create().with("source", source).with("target", target).with("tool", tool).with("mode", mode));
    }

    /** diagram_move: moves a shape's top-left corner to x,y. */
    public JsonObject diagramMove(String id, int x, int y, String container, String mode) {
        return structured("diagram_move", Arguments.create().with("id", id).with("x", x).with("y", y)
                .with("container", container).with("mode", mode));
    }

    /** diagram_resize: resizes a shape. */
    public JsonObject diagramResize(String id, int width, int height) {
        return structured("diagram_resize", Arguments.create().with("id", id).with("width", width).with("height", height));
    }

    /** diagram_delete: deletes an element without a confirmation dialog. */
    public JsonObject diagramDelete(String id) {
        return structured("diagram_delete", Arguments.create().with("id", id));
    }

    /** diagram_rename: renames an element. */
    public JsonObject diagramRename(String id, String name) {
        return structured("diagram_rename", Arguments.create().with("id", id).with("name", name));
    }

    /** diagram_set_property: sets an attribute of an element's business object. */
    public JsonObject diagramSetProperty(String id, String attribute, String value) {
        return structured("diagram_set_property",
                Arguments.create().with("id", id).with("attribute", attribute).with("value", value));
    }

    /** diagram_select: selects elements in the editor; an empty list clears the selection. */
    public String diagramSelect(List<String> ids) {
        return text("diagram_select", Arguments.create().with("ids", ids));
    }

    /** Calls a tool and returns its structured result, or an empty object for text-only results. */
    private JsonObject structured(String tool, Arguments arguments) {
        JsonObject structured = call(tool, arguments.toJson()).structured();
        return structured == null ? new JsonObject() : structured;
    }

    /** Calls a tool and returns its text. */
    private String text(String tool, Arguments arguments) {
        return call(tool, arguments.toJson()).text();
    }

    /** The reply body as an object; anything else means the server is not the plug-in. */
    private static JsonObject expectObject(HttpTransport.Reply reply, String what) {
        if (!reply.body.isJsonObject()) {
            throw new EclipseMcpException("Unexpected answer for " + what + " (HTTP " + reply.status + "): " + reply.body);
        }
        return reply.body.getAsJsonObject();
    }

    /** The "error" field of an error reply, or the fallback. */
    private static String errorText(HttpTransport.Reply reply, String fallback) {
        if (reply.body.isJsonObject() && reply.body.getAsJsonObject().has("error")) {
            return reply.body.getAsJsonObject().get("error").getAsString();
        }
        return fallback + " (HTTP " + reply.status + ")";
    }

    /** Percent-encodes a tool name for use as a path segment; tool names are plain ASCII words. */
    private static String encodePathSegment(String name) {
        StringBuilder encoded = new StringBuilder();
        for (byte value : name.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (value & 0xff);
            boolean safe = Character.isLetterOrDigit(character) && character < 128 || character == '_' || character == '-';
            encoded.append(safe ? String.valueOf(character) : String.format("%%%02X", value & 0xff));
        }
        return encoded.toString();
    }
}
