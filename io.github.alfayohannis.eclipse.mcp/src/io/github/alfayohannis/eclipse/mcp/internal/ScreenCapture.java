package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.ByteArrayOutputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Captures a screen area as PNG through SWT, so it sees exactly what Eclipse
 * draws. UI thread only; it needs X11, since Wayland hides other windows'
 * pixels.
 */
final class ScreenCapture {

    /** Not instantiable: the class only has static members. */
    private ScreenCapture() {
    }

    /** The area as PNG, scaled by the factor; pending paints finish first so the image is current. */
    static byte[] png(Rectangle area, double scale) {
        Display display = Ui.display();
        Rectangle clipped = area.intersection(display.getBounds());
        if (clipped.width <= 0 || clipped.height <= 0) {
            throw new ToolException("The area " + area + " is outside the screen " + display.getBounds());
        }
        display.update();
        Image shot = copyScreen(display, clipped);
        try {
            return scale == 1.0 ? encode(shot) : encodeScaled(display, shot, scale);
        } finally {
            shot.dispose();
        }
    }

    /** Copies the screen pixels of the area into a new image. */
    private static Image copyScreen(Display display, Rectangle area) {
        Image shot = new Image(display, area.width, area.height);
        GC gc = new GC(display);
        try {
            gc.copyArea(shot, area.x, area.y);
        } finally {
            gc.dispose();
        }
        return shot;
    }

    /** Scales with high-quality interpolation, which keeps text readable, then encodes. */
    private static byte[] encodeScaled(Display display, Image source, double scale) {
        Rectangle size = source.getBounds();
        int width = Math.max(1, (int) Math.round(size.width * scale));
        int height = Math.max(1, (int) Math.round(size.height * scale));
        Image target = new Image(display, width, height);
        GC gc = new GC(target);
        try {
            gc.setAntialias(SWT.ON);
            gc.setInterpolation(SWT.HIGH);
            gc.drawImage(source, 0, 0, size.width, size.height, 0, 0, width, height);
            return encode(target);
        } finally {
            gc.dispose();
            target.dispose();
        }
    }

    /** The image as PNG bytes. */
    private static byte[] encode(Image image) {
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        loader.save(bytes, SWT.IMAGE_PNG);
        return bytes.toByteArray();
    }
}
