package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/** Writes to the workspace's Error Log, where Eclipse users look for plug-in problems. */
public final class Log {

    /** Not instantiable: the class only has static members. */
    private Log() {
    }

    /** Records an event worth knowing about, such as the server's address. */
    public static void info(String message) {
        log(IStatus.INFO, message, null);
    }

    /** Records a failure with its stack trace. */
    public static void error(String message, Throwable cause) {
        log(IStatus.ERROR, message, cause);
    }

    /** Writes one entry under this bundle's id. */
    private static void log(int severity, String message, Throwable cause) {
        Platform.getLog(Log.class).log(new Status(severity, Config.BUNDLE_ID, message, cause));
    }
}
