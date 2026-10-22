package io.github.alfayohannis.eclipse.mcp.graphiti;

import java.util.Locale;

import io.github.alfayohannis.eclipse.mcp.api.Args;

/** Argument descriptions and parsing that the diagram tools share. */
final class DiagramArguments {

    /** The editor argument. */
    static final String EDITOR = "Title or workspace path of the open diagram editor; the active diagram editor when"
            + " omitted";

    /** The mode argument. */
    static final String MODE = "feature (default) runs the editor's feature through its command stack; mouse clicks"
            + " the palette and the canvas or drags with real input, visible in screenshots";

    /** The wait_ms argument. */
    static final String WAIT_MS = "How long to wait for the editor before reporting, in milliseconds (default 3000)";

    /** Not instantiable: the class only has constants and static helpers. */
    private DiagramArguments() {
    }

    /** Whether the caller asked for mouse mode; anything but feature or mouse is rejected. */
    static boolean mouseMode(Args args) {
        String mode = args.string("mode", "feature").toLowerCase(Locale.ROOT);
        if (!"feature".equals(mode) && !"mouse".equals(mode)) {
            throw new IllegalArgumentException("mode must be feature or mouse");
        }
        return "mouse".equals(mode);
    }

    /** The wait for feature runs. */
    static int waitMs(Args args) {
        return args.integer("wait_ms", 3000);
    }
}
