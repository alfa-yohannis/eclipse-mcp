package io.github.alfayohannis.eclipse.mcp.internal;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

/** The defaults of the MCP Server preferences: start with Eclipse, on port 8765. */
public class McpPreferenceInitializer extends AbstractPreferenceInitializer {

    /** Called by the preference service the first time the bundle's preferences are read. */
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(Config.BUNDLE_ID);
        defaults.putBoolean(Config.PREF_AUTO_START, true);
        defaults.putInt(Config.PREF_PORT, Config.DEFAULT_PORT);
    }
}
