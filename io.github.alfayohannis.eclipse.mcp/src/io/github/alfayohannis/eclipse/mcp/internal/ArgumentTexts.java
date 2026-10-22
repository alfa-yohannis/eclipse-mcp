package io.github.alfayohannis.eclipse.mcp.internal;

/** Descriptions of arguments that several tools share, so they read the same everywhere. */
public final class ArgumentTexts {

    /** The shell argument of tools that act on a shell. */
    public static final String SHELL = "Shell index or title; the innermost modal dialog, else the active window,"
            + " when omitted";

    /** The widget argument of tools that act on one widget. */
    public static final String WIDGET = "Widget index from list_shells with widgets=true";

    /** The wait_ms argument of tools that may open a modal dialog. */
    public static final String WAIT_MS = "How long to wait for the action before reporting, in milliseconds";

    /** Not instantiable: the class only holds constants. */
    private ArgumentTexts() {
    }
}
