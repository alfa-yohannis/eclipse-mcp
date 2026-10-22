package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * Runs work on the UI thread for the server threads. SWT allows widget access
 * only there, and a busy or blocked UI thread must not hang a client forever,
 * so every wait has a time limit.
 */
public final class Ui {

    /** How long a call waits for the UI thread before giving up. */
    public static final int TIMEOUT_MS = 30_000;

    /** Not instantiable: the class only has static members. */
    private Ui() {
    }

    /** The workbench display; fails when the workbench is gone, for example while Eclipse exits. */
    public static Display display() {
        if (!PlatformUI.isWorkbenchRunning()) {
            throw new ToolException("The workbench is not running");
        }
        Display display = PlatformUI.getWorkbench().getDisplay();
        if (display == null || display.isDisposed()) {
            throw new ToolException("The workbench display is gone");
        }
        return display;
    }

    /**
     * Runs the task on the UI thread and returns its result. A modal dialog's
     * event loop runs such tasks too, so this works while a dialog is open.
     */
    public static <T> T call(Callable<T> task) throws Exception {
        Display display = display();
        if (display.getThread() == Thread.currentThread()) {
            return task.call();
        }
        CompletableFuture<T> result = start(task);
        try {
            return result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ToolException("The UI thread did not respond within " + TIMEOUT_MS / 1000 + " s");
        } catch (ExecutionException e) {
            throw asException(e.getCause());
        }
    }

    /** Runs the action on the UI thread and waits for it. */
    public static void run(Runnable action) throws Exception {
        call(Executors.callable(action));
    }

    /**
     * Queues the task on the UI thread and returns at once, so a modal dialog
     * that the task opens cannot block the caller.
     */
    public static <T> CompletableFuture<T> start(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        display().asyncExec(() -> complete(result, task));
        return result;
    }

    /** Waits up to waitMs; returns whether the task finished, and rethrows its failure if it failed. */
    public static boolean await(CompletableFuture<?> future, int waitMs) throws Exception {
        try {
            future.get(Math.max(0, waitMs), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            throw asException(e.getCause());
        }
    }

    /** Sleeps on a server thread, keeping the interrupt flag. */
    public static void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The active workbench window, or the first one while a dialog has the focus. UI thread only. */
    public static IWorkbenchWindow window() {
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        if (window == null && workbench.getWorkbenchWindowCount() > 0) {
            window = workbench.getWorkbenchWindows()[0];
        }
        if (window == null) {
            throw new ToolException("No workbench window is open");
        }
        return window;
    }

    /** The active page of the workbench window. UI thread only. */
    public static IWorkbenchPage page() {
        IWorkbenchPage page = window().getActivePage();
        if (page == null) {
            throw new ToolException("The workbench window has no active page");
        }
        return page;
    }

    /** Runs the task and records its result or failure in the future; called on the UI thread. */
    private static <T> void complete(CompletableFuture<T> result, Callable<T> task) {
        try {
            result.complete(task.call());
        } catch (Throwable e) {
            result.completeExceptionally(e);
        }
    }

    /** The failure as an exception the callers can throw; errors are thrown right away. */
    private static Exception asException(Throwable failure) {
        if (failure instanceof Exception) {
            return (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        return new IllegalStateException(failure);
    }
}
