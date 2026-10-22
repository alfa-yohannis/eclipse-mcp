package io.github.alfayohannis.eclipse.mcp.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the JSON Schema of a tool's arguments. MCP requires an object schema,
 * so this builder only offers properties of an object.
 */
public final class Schema {

    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    /** Use {@link #object()}. */
    private Schema() {
    }

    /** Starts an object schema without properties. */
    public static Schema object() {
        return new Schema();
    }

    /** Adds a string property. */
    public Schema string(String name, String description) {
        return add(name, property("string", description));
    }

    /** Adds a whole-number property. */
    public Schema integer(String name, String description) {
        return add(name, property("integer", description));
    }

    /** Adds a number property that may have a fraction. */
    public Schema number(String name, String description) {
        return add(name, property("number", description));
    }

    /** Adds a true/false property. */
    public Schema bool(String name, String description) {
        return add(name, property("boolean", description));
    }

    /** Adds a string property limited to the given values, which clients can offer as a choice. */
    public Schema choice(String name, String description, String... values) {
        JsonObject property = property("string", description);
        JsonArray allowed = new JsonArray();
        for (String value : values) {
            allowed.add(value);
        }
        property.add("enum", allowed);
        return add(name, property);
    }

    /** Adds an object property whose values are strings, such as command parameters by id. */
    public Schema stringMap(String name, String description) {
        JsonObject property = property("object", description);
        property.add("additionalProperties", property("string", "A value"));
        return add(name, property);
    }

    /** Adds a list-of-strings property. */
    public Schema stringList(String name, String description) {
        JsonObject property = property("array", description);
        property.add("items", property("string", "An item"));
        return add(name, property);
    }

    /** Marks properties as required, so clients ask for them. */
    public Schema required(String... names) {
        for (String name : names) {
            required.add(name);
        }
        return this;
    }

    /** The schema as JSON, a fresh copy on each call. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "object");
        json.add("properties", properties.deepCopy());
        if (required.size() > 0) {
            json.add("required", required.deepCopy());
        }
        return json;
    }

    /** Stores a property under its name and keeps the builder chain going. */
    private Schema add(String name, JsonObject property) {
        properties.add(name, property);
        return this;
    }

    /** A property of the given JSON type with its description. */
    private static JsonObject property(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }
}
