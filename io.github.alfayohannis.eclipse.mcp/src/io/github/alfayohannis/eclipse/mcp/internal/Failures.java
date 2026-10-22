package io.github.alfayohannis.eclipse.mcp.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.commands.common.CommandException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.swt.SWTException;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;
import io.github.alfayohannis.eclipse.mcp.api.ToolResult;

/**
 * Turns exceptions from tools into error results. Failures the caller can act
 * on keep their message; anything else is a bug, so it is logged with its stack
 * trace and reported as an internal error.
 */
public final class Failures {

    /** Not instantiable: the class only has static members. */
    private Failures() {
    }

    /** The error result for a failure of the named tool. */
    public static ToolResult toResult(String tool, Throwable failure) {
        Throwable cause = unwrap(failure);
        if (isExpected(cause)) {
            return ToolResult.error(message(cause));
        }
        Log.error("The MCP tool " + tool + " failed", cause);
        return ToolResult.internalError("Internal error in " + tool + ": " + cause);
    }

    /** The real failure inside reflection and future wrappers. */
    static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (isWrapper(cause) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** The message to show; a CoreException's message sits in its status. */
    static String message(Throwable failure) {
        if (failure instanceof CoreException) {
            return ((CoreException) failure).getStatus().getMessage();
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /** Whether the exception only wraps the real failure. */
    private static boolean isWrapper(Throwable failure) {
        return failure instanceof InvocationTargetException || failure instanceof ExecutionException
                || failure instanceof CompletionException;
    }

    /** Whether the failure is one a caller can understand and act on, as opposed to a bug. */
    private static boolean isExpected(Throwable failure) {
        return failure instanceof ToolException || failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException || failure instanceof UnsupportedOperationException
                || failure instanceof CoreException || failure instanceof CommandException
                || failure instanceof SWTException || failure instanceof TimeoutException
                || failure instanceof OperationCanceledException;
    }
}
