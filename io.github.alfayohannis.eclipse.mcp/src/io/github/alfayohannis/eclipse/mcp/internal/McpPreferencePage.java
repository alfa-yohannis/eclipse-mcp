package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * Window &gt; Preferences &gt; MCP Server: whether the server starts with
 * Eclipse and on which port. The values are stored per workspace, so a test
 * workspace can use another port than the daily one.
 */
public class McpPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    /** A page over this bundle's workspace preferences. */
    public McpPreferencePage() {
        super(GRID);
        setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, Config.BUNDLE_ID));
        setDescription("The MCP server lets AI assistants and other programs on this computer control Eclipse over"
                + " HTTP on 127.0.0.1. A new port takes effect when the server restarts (MCP > Restart Server). The"
                + " system property eclipse.mcp.port and the environment variable ECLIPSE_MCP_PORT override the port.");
    }

    /** The start-up check box and the port field, limited to unprivileged ports. */
    @Override
    protected void createFieldEditors() {
        BooleanFieldEditor autoStart = new BooleanFieldEditor(Config.PREF_AUTO_START,
                "&Start the server when Eclipse starts", getFieldEditorParent());
        addField(autoStart);
        fillRow(autoStart.getDescriptionControl(getFieldEditorParent()));
        IntegerFieldEditor port = new IntegerFieldEditor(Config.PREF_PORT, "&Port:", getFieldEditorParent());
        port.setValidRange(1024, 65535);
        addField(port);
    }

    /**
     * Lets a control take the whole width of its grid row. On GTK the preferred
     * width of a check box comes out a few pixels short of its label, so a check
     * box left at that width cuts off the last letters.
     *
     * @param control the control to widen
     */
    private static void fillRow(Control control) {
        GridData layoutData = (GridData) control.getLayoutData();
        layoutData.horizontalAlignment = SWT.FILL;
        layoutData.grabExcessHorizontalSpace = true;
    }

    /** Nothing to prepare; the store is set in the constructor. */
    @Override
    public void init(IWorkbench workbench) {
        // the preference store is set in the constructor
    }
}
