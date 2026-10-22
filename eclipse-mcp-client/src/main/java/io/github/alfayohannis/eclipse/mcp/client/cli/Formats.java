package io.github.alfayohannis.eclipse.mcp.client.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Readable terminal layouts for the results people look at most; everything else prints as JSON. */
final class Formats {

    /** Not instantiable: the class only has static members. */
    private Formats() {
    }

    /** Pretty JSON, the fallback layout. */
    static void json(JsonObject structured, Output output) {
        output.json(structured);
    }

    /** One line per project: name, open state, problem counts and location. */
    static void projects(JsonObject structured, Output output) {
        for (JsonElement element : structured.getAsJsonArray("projects")) {
            JsonObject project = element.getAsJsonObject();
            String counts = project.has("errors") ? project.get("errors").getAsInt() + " errors, "
                    + project.get("warnings").getAsInt() + " warnings" : "closed";
            output.line(text(project, "name") + "  (" + counts + ")  " + text(project, "location"));
        }
    }

    /** The counts, then one line per problem: severity, path, line and message. */
    static void problems(JsonObject structured, Output output) {
        output.line(structured.get("errors").getAsInt() + " errors, " + structured.get("warnings").getAsInt()
                + " warnings");
        for (JsonElement element : structured.getAsJsonArray("problems")) {
            JsonObject problem = element.getAsJsonObject();
            String line = problem.has("line") ? ":" + problem.get("line").getAsInt() : "";
            output.line(text(problem, "severity") + "  " + text(problem, "path") + line + "  " + text(problem, "message"));
        }
    }

    /** One line per command: id and name. */
    static void commands(JsonObject structured, Output output) {
        for (JsonElement element : structured.getAsJsonArray("commands")) {
            JsonObject command = element.getAsJsonObject();
            output.line(text(command, "id") + "  " + text(command, "name"));
        }
        output.line("(" + structured.get("matches").getAsInt() + " matches)");
    }

    /** The menu as an indented tree with shortcuts and disabled items marked. */
    static void menu(JsonObject structured, Output output) {
        printMenuItems(structured.getAsJsonArray("items"), "", output);
    }

    /** One line per shell, then the widgets when listed. */
    static void shells(JsonObject structured, Output output) {
        for (JsonElement element : structured.getAsJsonArray("shells")) {
            JsonObject shell = element.getAsJsonObject();
            String flags = (shell.has("default") ? " [default]" : "") + (shell.has("modal") ? " [modal]" : "");
            output.line("#" + shell.get("index").getAsInt() + " " + text(shell, "title") + flags);
        }
        if (structured.has("widgets")) {
            for (JsonElement element : structured.getAsJsonArray("widgets")) {
                JsonObject widget = element.getAsJsonObject();
                output.line("  widget #" + widget.get("index").getAsInt() + " " + text(widget, "type") + " "
                        + text(widget, "text") + (widget.has("enabled") ? " (disabled)" : ""));
            }
        }
    }

    /**
     * The diagram's shapes and connections, one per line, then the palette tools when the result holds
     * them, which it does for {@code diagram --palette}.
     */
    static void diagram(JsonObject structured, Output output) {
        output.line(text(structured, "editor") + "  " + text(structured, "file")
                + (structured.get("dirty").getAsBoolean() ? "  (unsaved changes)" : ""));
        for (JsonElement element : structured.getAsJsonArray("shapes")) {
            JsonObject shape = element.getAsJsonObject();
            JsonObject bounds = shape.getAsJsonObject("bounds");
            output.line("  " + text(shape, "id") + "  " + text(shape, "type") + "  \"" + text(shape, "name") + "\"  at "
                    + bounds.get("x") + "," + bounds.get("y") + " size " + bounds.get("width") + "x" + bounds.get("height"));
        }
        for (JsonElement element : structured.getAsJsonArray("connections")) {
            JsonObject connection = element.getAsJsonObject();
            output.line("  " + text(connection, "id") + "  " + text(connection, "type") + "  " + text(connection, "source")
                    + " -> " + text(connection, "target"));
        }
        if (structured.has("palette")) {
            printPalette(structured.getAsJsonArray("palette"), output);
        }
    }

    /**
     * A heading, then one line per palette tool: the label that diagram-create and diagram-connect take,
     * whether the tool makes a shape or a connection, and the palette drawer that holds it.
     */
    private static void printPalette(JsonArray palette, Output output) {
        output.line("palette:");
        for (JsonElement element : palette) {
            JsonObject tool = element.getAsJsonObject();
            output.line("  " + text(tool, "label") + "  " + text(tool, "kind") + "  (" + text(tool, "drawer") + ")");
        }
    }

    /** The items of a menu level, recursing into submenus that were listed. */
    private static void printMenuItems(JsonArray items, String indent, Output output) {
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            String suffix = (item.has("accelerator") ? "  " + text(item, "accelerator") : "")
                    + (item.has("enabled") ? "  (disabled)" : "") + (item.has("submenu") ? "  >" : "");
            output.line(indent + text(item, "label") + suffix);
            if (item.has("items")) {
                printMenuItems(item.getAsJsonArray("items"), indent + "  ", output);
            }
        }
    }

    /** A string field, or "" when absent. */
    private static String text(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
