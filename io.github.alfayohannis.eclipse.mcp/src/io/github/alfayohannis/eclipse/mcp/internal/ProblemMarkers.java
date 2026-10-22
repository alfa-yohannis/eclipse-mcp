package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** The problem markers (errors, warnings, infos) of a resource and everything below it. */
final class ProblemMarkers {

    /** Most severe first, then by path and line, the order of the Problems view. */
    private static final Comparator<IMarker> ORDER = Comparator.comparingInt(ProblemMarkers::severityRank)
            .thenComparing(ProblemMarkers::path).thenComparingInt(ProblemMarkers::line);

    private final IMarker[] markers;

    /** The markers of the resource and its descendants. */
    ProblemMarkers(IResource scope) throws CoreException {
        this.markers = scope.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
    }

    /** The IMarker severity constant for a name: error, warning or info. */
    static int severity(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
        case "error":
            return IMarker.SEVERITY_ERROR;
        case "warning":
            return IMarker.SEVERITY_WARNING;
        case "info":
            return IMarker.SEVERITY_INFO;
        default:
            throw new IllegalArgumentException("severity must be error, warning or info");
        }
    }

    /** How many markers have the severity. */
    int count(int severity) {
        int count = 0;
        for (IMarker marker : markers) {
            if (severityOf(marker) == severity) {
                count++;
            }
        }
        return count;
    }

    /** Adds the error and warning counts, the summary most callers want. */
    void addCounts(JsonObject json) {
        json.addProperty("errors", count(IMarker.SEVERITY_ERROR));
        json.addProperty("warnings", count(IMarker.SEVERITY_WARNING));
    }

    /** The markers of at least the minimum severity as JSON, most severe first, at most limit of them. */
    JsonArray describe(int minimumSeverity, int limit) {
        List<IMarker> selected = new ArrayList<>();
        for (IMarker marker : markers) {
            if (severityOf(marker) >= minimumSeverity) {
                selected.add(marker);
            }
        }
        selected.sort(ORDER);
        JsonArray array = new JsonArray();
        for (IMarker marker : selected.subList(0, Math.min(limit, selected.size()))) {
            array.add(describe(marker));
        }
        return array;
    }

    /** One marker: severity, message, path, line and marker type. */
    private static JsonObject describe(IMarker marker) {
        JsonObject json = new JsonObject();
        json.addProperty("severity", severityName(severityOf(marker)));
        json.addProperty("message", marker.getAttribute(IMarker.MESSAGE, ""));
        json.addProperty("path", path(marker));
        if (line(marker) > 0) {
            json.addProperty("line", line(marker));
        }
        try {
            json.addProperty("type", marker.getType());
        } catch (CoreException e) {
            // the marker was deleted meanwhile; its other attributes were cached
        }
        return json;
    }

    /** The marker's severity, info when it has none. */
    private static int severityOf(IMarker marker) {
        return marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
    }

    /** Sort key that puts errors first. */
    private static int severityRank(IMarker marker) {
        return -severityOf(marker);
    }

    /** The workspace path of the marker's resource. */
    private static String path(IMarker marker) {
        return marker.getResource().getFullPath().toString();
    }

    /** The marker's line, or 0 when it has none. */
    private static int line(IMarker marker) {
        return marker.getAttribute(IMarker.LINE_NUMBER, 0);
    }

    /** The name of a severity constant. */
    private static String severityName(int severity) {
        if (severity == IMarker.SEVERITY_ERROR) {
            return "error";
        }
        return severity == IMarker.SEVERITY_WARNING ? "warning" : "info";
    }
}
