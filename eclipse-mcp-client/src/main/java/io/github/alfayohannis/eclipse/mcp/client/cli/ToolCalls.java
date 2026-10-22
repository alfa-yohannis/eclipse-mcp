package io.github.alfayohannis.eclipse.mcp.client.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.github.alfayohannis.eclipse.mcp.client.Arguments;
import io.github.alfayohannis.eclipse.mcp.client.ToolResult;

/** Calls a tool for a command and prints the result, the shared core of most subcommands. */
final class ToolCalls {

    /** Not instantiable: the class only has static members. */
    private ToolCalls() {
    }

    /** Calls the tool and prints its structured result as pretty JSON, or its text. */
    static int run(CliContext context, String tool, Arguments arguments) {
        return run(context, tool, arguments.toJson(), Formats::json);
    }

    /** Calls the tool and prints the result with the formatter; --json prints the plug-in's JSON as is. */
    static int run(CliContext context, String tool, JsonObject arguments, ResultFormatter formatter) {
        ToolResult result = context.client().call(tool, arguments);
        Output output = context.output();
        if (output.isJson() || result.structured() == null) {
            output.result(result);
        } else {
            formatter.print(result.structured(), output);
        }
        return 0;
    }

    /**
     * Arguments from "key=value" words or one JSON object. Values that parse
     * as JSON (numbers, true, false, arrays, objects) keep their type; the
     * rest are strings.
     */
    static JsonObject parseArguments(java.util.List<String> words) {
        if (words.size() == 1 && words.get(0).trim().startsWith("{")) {
            return JsonParser.parseString(words.get(0)).getAsJsonObject();
        }
        JsonObject arguments = new JsonObject();
        for (String word : words) {
            int equals = word.indexOf('=');
            if (equals <= 0) {
                throw new UsageException("Arguments are key=value or one JSON object, not \"" + word + "\"");
            }
            arguments.add(word.substring(0, equals), value(word.substring(equals + 1)));
        }
        return arguments;
    }

    /** A value as JSON when it is a JSON literal, else as a string. */
    private static JsonElement value(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonNull() ? new JsonPrimitive(text) : parsed;
        } catch (JsonParseException e) {
            return new JsonPrimitive(text);
        }
    }
}
