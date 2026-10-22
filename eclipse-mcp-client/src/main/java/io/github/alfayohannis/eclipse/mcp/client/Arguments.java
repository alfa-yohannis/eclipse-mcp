package io.github.alfayohannis.eclipse.mcp.client;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the arguments object of a tool call. Null values are left out, so the
 * tool applies its default, which keeps the typed methods short.
 */
public final class Arguments {

    private final JsonObject json = new JsonObject();

    /** Use {@link #create()}. */
    private Arguments() {
    }

    /** Starts an empty arguments object. */
    public static Arguments create() {
        return new Arguments();
    }

    /** Adds a string, unless it is null. */
    public Arguments with(String name, String value) {
        if (value != null) {
            json.addProperty(name, value);
        }
        return this;
    }

    /** Adds a number, unless it is null. */
    public Arguments with(String name, Number value) {
        if (value != null) {
            json.addProperty(name, value);
        }
        return this;
    }

    /** Adds a boolean, unless it is null. */
    public Arguments with(String name, Boolean value) {
        if (value != null) {
            json.addProperty(name, value);
        }
        return this;
    }

    /** Adds a list of strings, unless it is null. */
    public Arguments with(String name, List<String> values) {
        if (values != null) {
            JsonArray array = new JsonArray();
            for (String value : values) {
                array.add(value);
            }
            json.add(name, array);
        }
        return this;
    }

    /** Adds an object of strings, unless it is null or empty. */
    public Arguments with(String name, Map<String, String> values) {
        if (values != null && !values.isEmpty()) {
            JsonObject object = new JsonObject();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                object.addProperty(entry.getKey(), entry.getValue());
            }
            json.add(name, object);
        }
        return this;
    }

    /** The arguments as JSON. */
    public JsonObject toJson() {
        return json.deepCopy();
    }
}
