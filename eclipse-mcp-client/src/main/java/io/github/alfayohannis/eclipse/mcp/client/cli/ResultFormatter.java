package io.github.alfayohannis.eclipse.mcp.client.cli;

import com.google.gson.JsonObject;

/** Turns a tool's structured result into readable lines for the terminal. */
@FunctionalInterface
public interface ResultFormatter {

    /** Prints the structured result. */
    void print(JsonObject structured, Output output);
}
