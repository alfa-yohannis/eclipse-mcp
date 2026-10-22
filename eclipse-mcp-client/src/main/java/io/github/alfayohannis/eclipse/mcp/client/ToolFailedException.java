package io.github.alfayohannis.eclipse.mcp.client;

/**
 * A tool reported an error, such as a dialog that is not open (HTTP 422) or a
 * bug in the tool (HTTP 500). The tool's own result stays available.
 */
public class ToolFailedException extends EclipseMcpException {

    private static final long serialVersionUID = 1L;

    private final transient ToolResult result;
    private final int status;

    /** The failure with the tool's error result and the HTTP status. */
    public ToolFailedException(String tool, int status, ToolResult result) {
        super(tool + " failed: " + result.text());
        this.result = result;
        this.status = status;
    }

    /** The tool's error result, with its message in the text content. */
    public ToolResult result() {
        return result;
    }

    /** 422 for a failure the caller can fix, 500 for a bug in the tool. */
    public int status() {
        return status;
    }
}
