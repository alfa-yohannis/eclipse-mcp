package io.github.alfayohannis.eclipse.mcp.internal;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;

/**
 * Pointer input through the X server's XTest extension, by way of
 * java.awt.Robot. On GTK, SWT posts mouse buttons as synthetic GDK events,
 * which GEF palettes and canvases ignore; XTest events are indistinguishable
 * from a real mouse, so diagram editors react to them.
 */
final class XTestPointer implements PointerDriver {

    private static final Map<Integer, Integer> BUTTON_MASKS = Map.of(1, InputEvent.BUTTON1_DOWN_MASK, 2,
            InputEvent.BUTTON2_DOWN_MASK, 3, InputEvent.BUTTON3_DOWN_MASK);
    private static final Map<Integer, Integer> MODIFIER_KEYS = Map.of(SWT.CTRL, KeyEvent.VK_CONTROL, SWT.SHIFT,
            KeyEvent.VK_SHIFT, SWT.ALT, KeyEvent.VK_ALT, SWT.COMMAND, KeyEvent.VK_META);

    private final Robot robot;

    /** A driver over the robot. */
    private XTestPointer(Robot robot) {
        this.robot = robot;
    }

    /** An XTest driver when this is an X11 session with a usable AWT robot, else null. */
    static XTestPointer createIfAvailable() {
        if (!"gtk".equals(SWT.getPlatform()) || System.getenv("DISPLAY") == null || GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            return new XTestPointer(new Robot());
        } catch (AWTException | RuntimeException | LinkageError e) {
            Log.error("XTest input is not available; falling back to SWT's Display.post", e);
            return null;
        }
    }

    /** Warps the pointer with an XTest motion event. */
    @Override
    public void move(Point at) {
        robot.mouseMove(at.x, at.y);
    }

    /** Presses the button with an XTest button event. */
    @Override
    public void press(int button) {
        robot.mousePress(buttonMask(button));
    }

    /** Releases the button with an XTest button event. */
    @Override
    public void release(int button) {
        robot.mouseRelease(buttonMask(button));
    }

    /** Presses the modifier key with an XTest key event, so the X server applies it to the clicks. */
    @Override
    public void pressModifier(int modifier) {
        robot.keyPress(modifierKey(modifier));
    }

    /** Releases the modifier key. */
    @Override
    public void releaseModifier(int modifier) {
        robot.keyRelease(modifierKey(modifier));
    }

    /** The AWT mask of an SWT button number. */
    private static int buttonMask(int button) {
        Integer mask = BUTTON_MASKS.get(button);
        if (mask == null) {
            throw new IllegalArgumentException("No mouse button " + button);
        }
        return mask;
    }

    /** The AWT key code of an SWT modifier. */
    private static int modifierKey(int modifier) {
        Integer key = MODIFIER_KEYS.get(modifier);
        if (key == null) {
            throw new IllegalArgumentException("No modifier key " + modifier);
        }
        return key;
    }
}
