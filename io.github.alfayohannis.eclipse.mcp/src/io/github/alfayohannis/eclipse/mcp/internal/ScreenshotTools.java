package io.github.alfayohannis.eclipse.mcp.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.google.gson.JsonObject;

import io.github.alfayohannis.eclipse.mcp.api.Args;
import io.github.alfayohannis.eclipse.mcp.api.Schema;
import io.github.alfayohannis.eclipse.mcp.api.Tool;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/** The screenshot tool, which lets a client see what a person sees, and documents results in images. */
final class ScreenshotTools {

    /** Wider captures are scaled down to this width by default, to keep results small. */
    private static final int DEFAULT_MAX_WIDTH = 1600;

    /** Not instantiable: the class only has static members. */
    private ScreenshotTools() {
    }

    /** The tools this class defines, for the registry to collect. */
    static List<Tool> all() {
        return List.of(Tool.builder("screenshot").title("Screenshot")
                .description("Takes a PNG screenshot of a shell (default: the default shell), the workbench window,"
                        + " a widget or the whole screen. At scale 1 its pixels match the coordinates of mouse_click"
                        + " relative to the same shell or widget.")
                .input(Schema.object()
                        .choice("target", "What to capture (default shell)", "shell", "window", "widget", "screen")
                        .string("shell", ArgumentTexts.SHELL).integer("widget", ArgumentTexts.WIDGET + ", for target=widget")
                        .number("scale", "Scale factor; by default 1, or smaller to keep the width at 1600 px")
                        .string("file", "Also save the PNG to this path")
                        .bool("inline", "Return the image in the result (default true)"))
                .readOnly(true).handler(ScreenshotTools::screenshot).build());
    }

    /** Captures the target, saves it when asked and returns it with its screen bounds. */
    private static ToolResult screenshot(Args args) throws Exception {
        String target = args.string("target", "shell").toLowerCase(Locale.ROOT);
        String selector = args.string("shell");
        Integer widget = args.integer("widget");
        Double requestedScale = args.number("scale");
        Capture capture = Ui.call(() -> capture(target, selector, widget, requestedScale));
        String file = args.string("file");
        JsonObject json = new JsonObject();
        json.addProperty("captured", capture.description);
        json.add("screenBounds", Widgets.bounds(capture.area));
        json.addProperty("scale", capture.scale);
        json.addProperty("bytes", capture.png.length);
        if (file != null) {
            json.addProperty("file", save(file, capture.png).toString());
        }
        ToolResult result = ToolResult.json(json);
        if (args.bool("inline", true)) {
            result.addImage(capture.png, "image/png");
        }
        return result;
    }

    /** Captures the target area. UI thread only. */
    private static Capture capture(String target, String selector, Integer widget, Double requestedScale) {
        Capture capture = area(target, selector, widget);
        double scale = requestedScale != null ? requestedScale
                : capture.area.width > DEFAULT_MAX_WIDTH ? (double) DEFAULT_MAX_WIDTH / capture.area.width : 1.0;
        if (scale <= 0 || scale > 4) {
            throw new IllegalArgumentException("scale must be above 0 and at most 4");
        }
        return capture.withImage(ScreenCapture.png(capture.area, scale), scale);
    }

    /** The screen area and a description of the target. UI thread only. */
    private static Capture area(String target, String selector, Integer widget) {
        switch (target) {
        case "screen":
            return new Capture(Ui.display().getBounds(), "the screen");
        case "window":
            Shell window = Ui.window().getShell();
            return new Capture(window.getBounds(), "the window \"" + window.getText() + "\"");
        case "widget":
            return widgetArea(Shells.find(selector), widget);
        case "shell":
            Shell shell = Shells.find(selector);
            return new Capture(shell.getBounds(), "the shell \"" + shell.getText() + "\"");
        default:
            throw new IllegalArgumentException("target must be shell, window, widget or screen");
        }
    }

    /** The screen area of one widget. */
    private static Capture widgetArea(Shell shell, Integer widget) {
        if (widget == null) {
            throw new IllegalArgumentException("target=widget needs widget");
        }
        Control control = Widgets.widget(shell, widget);
        Point origin = control.toDisplay(0, 0);
        Point size = control.getSize();
        return new Capture(new Rectangle(origin.x, origin.y, size.x, size.y),
                control.getClass().getSimpleName() + " #" + widget + " of \"" + shell.getText() + "\"");
    }

    /** Writes the PNG, creating the directory; returns the absolute path. */
    private static Path save(String file, byte[] png) throws java.io.IOException {
        Path path = Paths.get(file).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, png);
        return path;
    }

    /** A captured area: where it is, what it shows, and the image once taken. */
    private static final class Capture {
        final Rectangle area;
        final String description;
        final byte[] png;
        final double scale;

        /** An area to capture, before the image is taken. */
        Capture(Rectangle area, String description) {
            this(area, description, new byte[0], 1.0);
        }

        /** An area with its image. */
        private Capture(Rectangle area, String description, byte[] png, double scale) {
            this.area = area;
            this.description = description;
            this.png = png;
            this.scale = scale;
        }

        /** The same area with the image taken at this scale. */
        Capture withImage(byte[] image, double imageScale) {
            return new Capture(area, description, image, imageScale);
        }
    }
}
