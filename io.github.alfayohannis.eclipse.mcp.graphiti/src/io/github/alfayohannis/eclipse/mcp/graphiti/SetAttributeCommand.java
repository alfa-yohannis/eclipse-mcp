package io.github.alfayohannis.eclipse.mcp.graphiti;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Sets one attribute of a business object inside a transaction of the
 * editor's editing domain, which is the only way to change its model; the
 * change lands on the editor's undo history.
 */
final class SetAttributeCommand extends RecordingCommand {

    private final EObject target;
    private final EAttribute attribute;
    private final Object value;

    /** A command that sets the attribute of the target to the value. */
    SetAttributeCommand(TransactionalEditingDomain domain, EObject target, EAttribute attribute, Object value) {
        super(domain, "Set " + attribute.getName());
        this.target = target;
        this.attribute = attribute;
        this.value = value;
    }

    /** Called by the command stack inside the transaction. */
    @Override
    protected void doExecute() {
        target.eSet(attribute, value);
    }
}
