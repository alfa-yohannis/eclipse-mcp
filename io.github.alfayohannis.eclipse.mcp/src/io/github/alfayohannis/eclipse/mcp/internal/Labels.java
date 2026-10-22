package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.Collection;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Matches widget and menu labels the way a person reads them: without
 * mnemonics, shortcut text, a trailing ellipsis or case, so "Open BPMN2 Project
 * from IPFS" finds "Open BPMN2 &amp;Project from IPFS...\tCtrl+P".
 */
public final class Labels {

    /** Not instantiable: the class only has static members. */
    private Labels() {
    }

    /** The label without mnemonic markers ('&amp;') and without the shortcut text after a tab. */
    public static String plain(String label) {
        if (label == null) {
            return "";
        }
        int tab = label.indexOf('\t');
        String text = tab >= 0 ? label.substring(0, tab) : label;
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            i = appendPlainCharacter(text, i, plain);
        }
        return plain.toString().trim();
    }

    /** The shortcut text after the tab, such as "Ctrl+S", or null. */
    public static String accelerator(String label) {
        int tab = label == null ? -1 : label.indexOf('\t');
        return tab >= 0 ? label.substring(tab + 1).trim() : null;
    }

    /** The plain label in lower case without a trailing ellipsis, for comparisons. */
    public static String key(String label) {
        String text = plain(label);
        if (text.endsWith("...")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("…")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The candidate whose label equals the wanted one; else the first whose
     * label starts with it; else the first that contains it; else null.
     */
    public static <T> T best(Collection<T> candidates, Function<T, String> label, String wanted) {
        String wantedKey = key(wanted);
        if (wantedKey.isEmpty()) {
            return null;
        }
        T prefixMatch = null;
        T substringMatch = null;
        for (T candidate : candidates) {
            String candidateKey = key(label.apply(candidate));
            if (candidateKey.equals(wantedKey)) {
                return candidate;
            }
            if (prefixMatch == null && candidateKey.startsWith(wantedKey)) {
                prefixMatch = candidate;
            }
            if (substringMatch == null && candidateKey.contains(wantedKey)) {
                substringMatch = candidate;
            }
        }
        return prefixMatch != null ? prefixMatch : substringMatch;
    }

    /** The plain labels, quoted and joined, so error messages can list what exists. */
    public static <T> String list(Collection<T> items, Function<T, String> label) {
        StringJoiner joined = new StringJoiner(", ");
        for (T item : items) {
            joined.add("\"" + plain(label.apply(item)) + "\"");
        }
        return joined.length() == 0 ? "(none)" : joined.toString();
    }

    /** The text cut to the maximum length, marked with "..." when cut, to keep tool output small. */
    public static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /**
     * Appends the character at the index unless it is a mnemonic marker; "&amp;&amp;"
     * stands for a literal ampersand. Returns the index of the last character used.
     */
    private static int appendPlainCharacter(String text, int index, StringBuilder plain) {
        char character = text.charAt(index);
        if (character != '&') {
            plain.append(character);
            return index;
        }
        boolean escaped = index + 1 < text.length() && text.charAt(index + 1) == '&';
        if (escaped) {
            plain.append('&');
            return index + 1;
        }
        return index;
    }
}
