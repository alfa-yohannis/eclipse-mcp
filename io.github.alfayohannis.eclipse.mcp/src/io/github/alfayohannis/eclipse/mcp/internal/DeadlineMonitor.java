package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * A progress monitor that reports itself canceled after a deadline, so a long
 * build or wait gives up instead of blocking the client forever.
 */
final class DeadlineMonitor extends NullProgressMonitor {

    private final long deadline;

    /** A monitor that cancels after the given number of seconds. */
    DeadlineMonitor(int seconds) {
        this.deadline = System.currentTimeMillis() + seconds * 1000L;
    }

    /** Canceled when asked to be or when the deadline has passed. */
    @Override
    public boolean isCanceled() {
        return super.isCanceled() || System.currentTimeMillis() > deadline;
    }
}
