package io.github.alfayohannis.eclipse.mcp.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;

/** A screenshot from the screenshot tool: the PNG and what it shows where on the screen. */
public final class Screenshot {

    private final byte[] png;
    private final JsonObject details;

    /** The image and the tool's description of it. */
    public Screenshot(byte[] png, JsonObject details) {
        this.png = png.clone();
        this.details = details;
    }

    /** The PNG bytes. */
    public byte[] png() {
        return png.clone();
    }

    /** What was captured, its screen bounds and the scale. */
    public JsonObject details() {
        return details.deepCopy();
    }

    /** Writes the PNG to the file, creating its directory. */
    public Path save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        if (absolute.getParent() != null) {
            Files.createDirectories(absolute.getParent());
        }
        return Files.write(absolute, png);
    }
}
