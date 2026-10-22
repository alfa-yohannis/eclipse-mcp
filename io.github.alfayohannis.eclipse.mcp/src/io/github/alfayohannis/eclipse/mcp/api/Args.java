package io.github.alfayohannis.eclipse.mcp.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The arguments of one tool call with typed accessors. MCP clients sometimes
 * send numbers and booleans as strings, so the accessors convert them. A value
 * of the wrong type ends the call with an {@link IllegalArgumentException},
 * which the caller sees as the tool's error message.
 */
public final class Args {

    private final JsonObject values;

    /** Wraps a copy of the arguments; null means no arguments. */
    public Args(JsonObject values) {
        this.values = values == null ? new JsonObject() : values.deepCopy();
    }

    /** A copy of the raw arguments. */
    public JsonObject toJson() {
        return values.deepCopy();
    }

    /** Whether the argument is present and not null. */
    public boolean has(String name) {
        JsonElement value = values.get(name);
        return value != null && !value.isJsonNull();
    }

    /** The argument as a string, or null when absent; numbers and booleans are converted. */
    public String string(String name) {
        return has(name) ? primitive(name, "a string").getAsString() : null;
    }

    /** The argument as a string, or the fallback when absent. */
    public String string(String name, String fallback) {
        String value = string(name);
        return value == null ? fallback : value;
    }

    /** The argument as a non-empty string; fails with a clear message when missing. */
    public String requiredString(String name) {
        String value = string(name);
        if (value == null || value.isEmpty()) {
            throw missing(name);
        }
        return value;
    }

    /** The argument as a whole number, or null when absent. */
    public Integer integer(String name) {
        Double number = number(name);
        if (number == null) {
            return null;
        }
        if (number != Math.rint(number) || Math.abs(number) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Argument \"" + name + "\" must be a whole number");
        }
        return number.intValue();
    }

    /** The argument as a whole number, or the fallback when absent. */
    public int integer(String name, int fallback) {
        Integer value = integer(name);
        return value == null ? fallback : value;
    }

    /** The argument as a whole number; fails with a clear message when missing. */
    public int requiredInteger(String name) {
        Integer value = integer(name);
        if (value == null) {
            throw missing(name);
        }
        return value;
    }

    /** The argument as a number, or null when absent. */
    public Double number(String name) {
        if (!has(name)) {
            return null;
        }
        JsonPrimitive value = primitive(name, "a number");
        try {
            return value.isNumber() ? value.getAsDouble() : Double.parseDouble(value.getAsString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Argument \"" + name + "\" must be a number");
        }
    }

    /** The argument as a number, or the fallback when absent. */
    public double number(String name, double fallback) {
        Double value = number(name);
        return value == null ? fallback : value;
    }

    /** The argument as true or false, or the fallback when absent. */
    public boolean bool(String name, boolean fallback) {
        if (!has(name)) {
            return fallback;
        }
        JsonPrimitive value = primitive(name, "true or false");
        if (value.isBoolean()) {
            return value.getAsBoolean();
        }
        return parseBoolean(name, value.getAsString().trim());
    }

    /** An object of string values, or an empty map when absent; null values are skipped. */
    public Map<String, String> stringMap(String name) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!has(name)) {
            return map;
        }
        JsonElement value = values.get(name);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Argument \"" + name + "\" must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            putStringValue(map, name, entry.getKey(), entry.getValue());
        }
        return map;
    }

    /** A list of strings; a single string counts as a list of one, absence as an empty list. */
    public List<String> strings(String name) {
        List<String> list = new ArrayList<>();
        if (!has(name)) {
            return list;
        }
        JsonElement value = values.get(name);
        if (!value.isJsonArray()) {
            list.add(primitive(name, "a string or a list of strings").getAsString());
            return list;
        }
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive()) {
                throw new IllegalArgumentException("Argument \"" + name + "\" must be a list of strings");
            }
            list.add(item.getAsString());
        }
        return list;
    }

    /**
     * A label path such as "File &gt; New &gt; Project...", given as one string
     * with '&gt;' between the labels or as a list of labels.
     */
    public List<String> path(String name) {
        List<String> path = new ArrayList<>();
        for (String part : strings(name)) {
            addLabels(path, part);
        }
        return path;
    }

    /** The arguments as compact JSON, for log messages. */
    @Override
    public String toString() {
        return values.toString();
    }

    /** Adds the non-empty labels of one '&gt;'-separated string to the path. */
    private static void addLabels(List<String> path, String part) {
        for (String label : part.split(">")) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty()) {
                path.add(trimmed);
            }
        }
    }

    /** Copies one entry of an object argument into the map, rejecting nested objects and arrays. */
    private static void putStringValue(Map<String, String> map, String name, String key, JsonElement item) {
        if (item.isJsonNull()) {
            return;
        }
        if (!item.isJsonPrimitive()) {
            throw new IllegalArgumentException("Argument \"" + name + "." + key + "\" must be a string");
        }
        map.put(key, item.getAsString());
    }

    /** Reads "true" or "false" in any case, the way clients send booleans as strings. */
    private static boolean parseBoolean(String name, String text) {
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("Argument \"" + name + "\" must be true or false");
    }

    /** The argument as a JSON primitive; objects and arrays are rejected with what was expected. */
    private JsonPrimitive primitive(String name, String expected) {
        JsonElement value = values.get(name);
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Argument \"" + name + "\" must be " + expected);
        }
        return value.getAsJsonPrimitive();
    }

    /** The error for a required argument that the caller left out. */
    private static IllegalArgumentException missing(String name) {
        return new IllegalArgumentException("Missing argument \"" + name + "\"");
    }
}
