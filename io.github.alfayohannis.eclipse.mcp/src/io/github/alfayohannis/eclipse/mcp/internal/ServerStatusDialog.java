package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * MCP &gt; Server Status...: whether the server runs, its addresses, port,
 * token and instance files, and client setups ready to paste, with a button
 * that copies them.
 */
final class ServerStatusDialog extends Dialog {

    private static final int COPY_ID = IDialogConstants.CLIENT_ID + 1;

    private Text setupText;

    /** A dialog over the given shell. */
    ServerStatusDialog(Shell parent) {
        super(parent);
    }

    /** Sets the window title. */
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MCP Server Status");
    }

    /** Lets the user widen the dialog for long commands. */
    @Override
    protected boolean isResizable() {
        return true;
    }

    /** The facts and the setup text. */
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        McpHttpServer server = ServerControl.server();
        int port = server != null ? server.port() : Config.port();
        String baseUrl = "http://127.0.0.1:" + port;
        createFacts(area, server != null, baseUrl, port);
        Label heading = new Label(area, SWT.NONE);
        heading.setText("Client setup, ready to paste:");
        GridDataFactory.fillDefaults().indent(0, 10).applyTo(heading);
        setupText = new Text(area, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
        setupText.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
        setupText.setText(ClientSetupText.forServer(baseUrl));
        GridDataFactory.fillDefaults().grab(true, true).hint(760, 220).applyTo(setupText);
        return area;
    }

    /** Copy Setup and Close. */
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, COPY_ID, "Copy Setup", false);
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
    }

    /** Copies the setup text, or closes the dialog. */
    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == COPY_ID) {
            copySetup();
        } else if (buttonId == IDialogConstants.CLOSE_ID) {
            close();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    /** The state, addresses, port, token source and instance directory, as selectable text. */
    private static void createFacts(Composite area, boolean running, String baseUrl, int port) {
        Composite facts = new Composite(area, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).spacing(12, 6).applyTo(facts);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(facts);
        addFact(facts, "State:", running ? "Running" : "Stopped (MCP > Start Server starts it)");
        addFact(facts, "MCP endpoint:", baseUrl + "/mcp");
        addFact(facts, "REST API:", baseUrl + "/api");
        addFact(facts, "Port:", running ? Integer.toString(port) : port + " (when started)");
        addFact(facts, "Token:", TokenFile.source());
        addFact(facts, "Instance files:", Config.instancesDirectory().toString());
    }

    /** One labelled fact; the value is a read-only field so it can be selected and copied. */
    private static void addFact(Composite parent, String name, String value) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(name);
        Text text = new Text(parent, SWT.READ_ONLY);
        text.setText(value);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(text);
    }

    /** Puts the setup text on the clipboard. */
    private void copySetup() {
        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        try {
            clipboard.setContents(new Object[] { setupText.getText() }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            clipboard.dispose();
        }
    }
}
