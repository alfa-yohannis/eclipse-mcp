package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.swt.graphics.Point;

/** Moves the mouse pointer and presses its buttons and the modifier keys held with them. */
interface PointerDriver {

    /** Moves the pointer to the screen point. */
    void move(Point at);

    /** Presses a mouse button: 1 left, 2 middle, 3 right. */
    void press(int button);

    /** Releases a mouse button. */
    void release(int button);

    /** Presses a modifier key given as an SWT constant such as SWT.CTRL. */
    void pressModifier(int modifier);

    /** Releases a modifier key. */
    void releaseModifier(int modifier);
}
