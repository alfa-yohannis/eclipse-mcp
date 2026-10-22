package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Real mouse and keyboard input, which reaches canvases such as diagram
 * editors that widget calls cannot. The pointer goes through XTest where
 * available (see {@link XTestPointer}), keys through SWT's Display.post to the
 * focused window. Call it from a server thread: the UI thread must stay free
 * to process the events.
 */
public final class SyntheticInput {

    /** Order in which modifiers go down; they come up in reverse. */
    private static final int[] MODIFIER_ORDER = { SWT.CTRL, SWT.ALT, SWT.SHIFT, SWT.COMMAND };
    /** Pause after each key event so GTK sees them as separate, like a person's typing. */
    private static final int KEY_GAP_MS = 6;
    /** GTK and draw2d must see the pointer arrive (enter and hover) before a press counts as a click there. */
    private static final int SETTLE_AFTER_MOVE_MS = 250;
    /** How long a button stays down in a click; GEF palette entries ignore presses that are too short. */
    private static final int BUTTON_HOLD_MS = 60;

    private static PointerDriver sharedPointer;

    private final Display display;
    private final PointerDriver pointer;

    /** Input for the given display. */
    public SyntheticInput(Display display) {
        this.display = display;
        this.pointer = pointerFor(display);
    }

    /** Moves the pointer to the screen point. */
    public void move(Point at) {
        pointer.move(at);
    }

    /** Moves to the point and clicks count times with the button (1 left, 2 middle, 3 right). */
    public void click(Point at, int button, int count, int modifiers) {
        pointer.move(at);
        Ui.pause(SETTLE_AFTER_MOVE_MS);
        pressModifiers(modifiers);
        for (int i = 0; i < count; i++) {
            pointer.press(button);
            Ui.pause(BUTTON_HOLD_MS);
            pointer.release(button);
            Ui.pause(80);
        }
        releaseModifiers(modifiers);
    }

    /** Presses at one point, moves in steps to the other and releases there, as a drag and drop does. */
    public void drag(Point from, Point to, int button, int steps, int modifiers) {
        pointer.move(from);
        Ui.pause(SETTLE_AFTER_MOVE_MS);
        pressModifiers(modifiers);
        pointer.press(button);
        Ui.pause(150);
        int count = Math.max(1, steps);
        for (int step = 1; step <= count; step++) {
            pointer.move(new Point(from.x + (to.x - from.x) * step / count, from.y + (to.y - from.y) * step / count));
            Ui.pause(30);
        }
        Ui.pause(SETTLE_AFTER_MOVE_MS);
        pointer.release(button);
        releaseModifiers(modifiers);
    }

    /** Presses and releases the key with its modifiers, sent to the focused window. */
    public void press(KeyCombo key) {
        postKeyModifiers(key.modifiers(), true);
        postKey(keyEvent(SWT.KeyDown, key.keyCode(), key.character()));
        postKey(keyEvent(SWT.KeyUp, key.keyCode(), key.character()));
        postKeyModifiers(key.modifiers(), false);
    }

    /** Types the text key by key, assuming a US keyboard layout for symbols. */
    public void type(String text) {
        for (char character : text.toCharArray()) {
            press(KeyCombo.forCharacter(character));
        }
    }

    /** The SWT button number of a button name; null or blank means left. */
    public static int button(String name) {
        if (name == null || name.isBlank() || "left".equalsIgnoreCase(name)) {
            return 1;
        }
        if ("middle".equalsIgnoreCase(name)) {
            return 2;
        }
        if ("right".equalsIgnoreCase(name)) {
            return 3;
        }
        throw new ToolException("button must be left, middle or right");
    }

    /** A key event with either a key code or a character. */
    static Event keyEvent(int type, int keyCode, char character) {
        Event event = new Event();
        event.type = type;
        event.keyCode = keyCode;
        event.character = character;
        return event;
    }

    /** The pointer driver, created once: XTest when available, else Display.post. */
    private static synchronized PointerDriver pointerFor(Display display) {
        if (sharedPointer == null) {
            XTestPointer xtest = XTestPointer.createIfAvailable();
            sharedPointer = xtest != null ? xtest : new SwtPointer(display);
        }
        return sharedPointer;
    }

    /** Holds the modifier keys for pointer gestures, in a fixed order. */
    private void pressModifiers(int modifiers) {
        for (int modifier : MODIFIER_ORDER) {
            if ((modifiers & modifier) != 0) {
                pointer.pressModifier(modifier);
            }
        }
    }

    /** Releases the pointer gesture's modifier keys in reverse order. */
    private void releaseModifiers(int modifiers) {
        for (int i = MODIFIER_ORDER.length - 1; i >= 0; i--) {
            if ((modifiers & MODIFIER_ORDER[i]) != 0) {
                pointer.releaseModifier(MODIFIER_ORDER[i]);
            }
        }
    }

    /** Presses or releases a key combination's modifiers as key events to the focused window. */
    private void postKeyModifiers(int modifiers, boolean down) {
        for (int i = 0; i < MODIFIER_ORDER.length; i++) {
            int modifier = down ? MODIFIER_ORDER[i] : MODIFIER_ORDER[MODIFIER_ORDER.length - 1 - i];
            if ((modifiers & modifier) != 0) {
                postKey(keyEvent(down ? SWT.KeyDown : SWT.KeyUp, modifier, (char) 0));
            }
        }
    }

    /** Posts a key event; SWT refuses keys the keyboard layout cannot produce. */
    private void postKey(Event event) {
        if (!display.post(event)) {
            throw new ToolException("SWT refused the synthetic key event; the key must exist on the keyboard layout,"
                    + " and for text set_text or type_text with method=insert avoids the keyboard");
        }
        Ui.pause(KEY_GAP_MS);
    }
}
