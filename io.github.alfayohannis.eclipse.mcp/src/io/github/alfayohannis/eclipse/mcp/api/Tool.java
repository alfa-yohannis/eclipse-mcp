package io.github.alfayohannis.eclipse.mcp.api;

import java.util.Objects;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

/**
 * A tool that MCP clients, REST clients and other plug-ins can call. Build one
 * with {@link #builder(String)}, then add it with
 * {@link ToolRegistry#register(Tool)} or through a {@link ToolProvider} on the
 * {@code io.github.alfayohannis.eclipse.mcp.tools} extension point.
 */
public final class Tool {

    /** The code behind a tool. Calls arrive on a server thread, never on the UI thread. */
    @FunctionalInterface
    public interface Handler {

        /**
         * Runs one call of the tool.
         *
         * @param args the caller's arguments, already checked against nothing but JSON syntax
         * @return what the caller receives
         * @throws Exception any failure; {@link ToolRegistry#call} turns it into an error result
         */
        ToolResult call(Args args) throws Exception;
    }

    /** Tool names that MCP clients such as Claude accept. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final String name;
    private final String title;
    private final String description;
    private final JsonObject inputSchema;
    private final boolean readOnly;
    private final Handler handler;

    /** Copies the builder's parts; the builder has already checked them. */
    private Tool(Builder builder) {
        this.name = builder.name;
        this.title = builder.title == null ? builder.name : builder.title;
        this.description = builder.description;
        this.inputSchema = builder.schema.toJson();
        this.readOnly = builder.readOnly;
        this.handler = builder.handler;
    }

    /**
     * Starts a tool definition.
     *
     * @param name 1 to 64 letters, digits, '_' or '-', unique among the tools
     * @return a builder that needs at least a handler
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** The name clients call the tool by. */
    public String name() {
        return name;
    }

    /** A short human-readable title, shown by clients that support it. */
    public String title() {
        return title;
    }

    /** What the tool does, written for the AI model that decides when to call it. */
    public String description() {
        return description;
    }

    /** A copy of the JSON Schema of the arguments, so callers cannot change the tool. */
    public JsonObject inputSchema() {
        return inputSchema.deepCopy();
    }

    /** Whether the tool only reads state, which lets clients allow it without asking the user. */
    public boolean readOnly() {
        return readOnly;
    }

    /**
     * Runs the handler without the error handling of
     * {@link ToolRegistry#call(String, JsonObject)}, for callers that want the
     * raw exception.
     */
    public ToolResult call(Args args) throws Exception {
        return handler.call(args);
    }

    /** The tool as MCP tools/list and GET /api/tools describe it, including MCP annotations. */
    public JsonObject describe() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("title", title);
        json.addProperty("description", description);
        json.add("inputSchema", inputSchema.deepCopy());
        JsonObject annotations = new JsonObject();
        annotations.addProperty("title", title);
        annotations.addProperty("readOnlyHint", readOnly);
        annotations.addProperty("openWorldHint", false);
        json.add("annotations", annotations);
        return json;
    }

    /** Collects the parts of a {@link Tool} so that a finished tool never changes. */
    public static final class Builder {
        private final String name;
        private String title;
        private String description = "";
        private Schema schema = Schema.object();
        private boolean readOnly;
        private Handler handler;

        /** Checks the name early, so a bad name fails where the tool is defined. */
        private Builder(String name) {
            if (name == null || !VALID_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Tool names use 1 to 64 letters, digits, '_' or '-': " + name);
            }
            this.name = name;
        }

        /** Sets the human-readable title; the name is used when none is set. */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /** Sets the description the AI model reads to decide when and how to call the tool. */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /** Sets the schema of the arguments; a tool without arguments can skip this. */
        public Builder input(Schema schema) {
            this.schema = Objects.requireNonNull(schema, "schema");
            return this;
        }

        /** Marks the tool as one that only reads state. */
        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        /** Sets the code that runs the tool. */
        public Builder handler(Handler handler) {
            this.handler = handler;
            return this;
        }

        /** Creates the tool; fails when no handler was given. */
        public Tool build() {
            Objects.requireNonNull(handler, "handler");
            return new Tool(this);
        }
    }
}
