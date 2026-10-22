package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.io.PrintStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import io.github.alfayohannis.eclipse.mcp.client.ToolResult;

/**
 * Prints command results: readable text by default, the plug-in's JSON with
 * --json, so scripts can pipe the output into jq.
 */
public final class Output {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final PrintStream out;
    private final boolean json;

    /** Output to the stream, as JSON when asked. */
    public Output(PrintStream out, boolean json) {
        this.out = out;
        this.json = json;
    }

    /** Whether --json was given. */
    public boolean isJson() {
        return json;
    }

    /** Prints a line. */
    public void line(String text) {
        out.println(text);
    }

    /** Prints JSON, pretty. */
    public void json(JsonElement value) {
        out.println(PRETTY.toJson(value));
    }

    /** Prints a tool result: its whole JSON with --json, else the structured part, else the text. */
    public void result(ToolResult result) {
        if (json) {
            json(result.json());
        } else if (result.structured() != null) {
            json(result.structured());
        } else {
            line(result.text());
        }
    }
}
