package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Pointer input through SWT's Display.post, for platforms without XTest. It
 * works for native widgets such as buttons; GEF canvases on GTK may ignore it.
 */
final class SwtPointer implements PointerDriver {

    private final Display display;

    /** Input for the given display. */
    SwtPointer(Display display) {
        this.display = display;
    }

    /** Posts a mouse move. */
    @Override
    public void move(Point at) {
        Event event = new Event();
        event.type = SWT.MouseMove;
        event.x = at.x;
        event.y = at.y;
        post(event);
    }

    /** Posts a button press. */
    @Override
    public void press(int button) {
        post(buttonEvent(SWT.MouseDown, button));
    }

    /** Posts a button release. */
    @Override
    public void release(int button) {
        post(buttonEvent(SWT.MouseUp, button));
    }

    /** Posts a modifier key press. */
    @Override
    public void pressModifier(int modifier) {
        post(SyntheticInput.keyEvent(SWT.KeyDown, modifier, (char) 0));
    }

    /** Posts a modifier key release. */
    @Override
    public void releaseModifier(int modifier) {
        post(SyntheticInput.keyEvent(SWT.KeyUp, modifier, (char) 0));
    }

    /** A button event of the given type. */
    private static Event buttonEvent(int type, int button) {
        Event event = new Event();
        event.type = type;
        event.button = button;
        return event;
    }

    /** Posts the event; SWT refuses events it cannot produce on this platform. */
    private void post(Event event) {
        if (!display.post(event)) {
            throw new ToolException("SWT refused the synthetic mouse event (type " + event.type + "); Display.post"
                    + " needs an X11 session");
        }
    }
}
