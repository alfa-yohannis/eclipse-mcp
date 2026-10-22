package io.github.alfayohannis.eclipse.mcp.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.swt.SWT;

import io.github.alfayohannis.eclipse.mcp.api.ToolException;

/**
 * A key with its modifiers, such as ctrl+shift+s, parsed from the names people
 * write. Named keys without a character (arrows, function keys) use an SWT key
 * code; the others use a character.
 */
public final class KeyCombo {

    /** Characters that need Shift on a US keyboard layout. */
    private static final String SHIFTED = "~!@#$%^&*()_+{}|:\"<>?";

    private static final Map<String, Integer> KEY_CODES = Map.ofEntries(Map.entry("up", SWT.ARROW_UP),
            Map.entry("down", SWT.ARROW_DOWN), Map.entry("left", SWT.ARROW_LEFT), Map.entry("right", SWT.ARROW_RIGHT),
            Map.entry("home", SWT.HOME), Map.entry("end", SWT.END), Map.entry("pageup", SWT.PAGE_UP),
            Map.entry("pagedown", SWT.PAGE_DOWN), Map.entry("insert", SWT.INSERT), Map.entry("f1", SWT.F1),
            Map.entry("f2", SWT.F2), Map.entry("f3", SWT.F3), Map.entry("f4", SWT.F4), Map.entry("f5", SWT.F5),
            Map.entry("f6", SWT.F6), Map.entry("f7", SWT.F7), Map.entry("f8", SWT.F8), Map.entry("f9", SWT.F9),
            Map.entry("f10", SWT.F10), Map.entry("f11", SWT.F11), Map.entry("f12", SWT.F12));

    /**
     * Control characters go out with their key code as well: SWT on GTK maps
     * Enter, Tab, Esc, Backspace and Delete to keys reliably only that way.
     */
    private static final Map<String, Character> CONTROL_KEYS = Map.ofEntries(Map.entry("enter", SWT.CR),
            Map.entry("return", SWT.CR), Map.entry("tab", SWT.TAB), Map.entry("esc", SWT.ESC),
            Map.entry("escape", SWT.ESC), Map.entry("backspace", SWT.BS), Map.entry("delete", SWT.DEL),
            Map.entry("del", SWT.DEL));

    private static final Map<String, Character> NAMED_CHARACTERS = Map.ofEntries(Map.entry("space", ' '),
            Map.entry("plus", '+'), Map.entry("minus", '-'));

    private static final Map<String, Integer> MODIFIERS = Map.ofEntries(Map.entry("ctrl", SWT.CTRL),
            Map.entry("control", SWT.CTRL), Map.entry("shift", SWT.SHIFT), Map.entry("alt", SWT.ALT),
            Map.entry("cmd", SWT.COMMAND), Map.entry("command", SWT.COMMAND), Map.entry("meta", SWT.COMMAND),
            Map.entry("super", SWT.COMMAND));

    private final int modifiers;
    private final int keyCode;
    private final char character;

    /** A key given by key code or character, with the modifier bits held while it is pressed. */
    private KeyCombo(int modifiers, int keyCode, char character) {
        this.modifiers = modifiers;
        this.keyCode = keyCode;
        this.character = character;
    }

    /** Parses "ctrl+s", "shift+tab", "enter", "f5", "ctrl++" or a single character. */
    public static KeyCombo parse(String combo) {
        List<String> parts = splitOnPlus(combo.trim());
        int modifiers = 0;
        for (String part : parts.subList(0, parts.size() - 1)) {
            modifiers |= modifier(part);
        }
        return key(parts.get(parts.size() - 1), modifiers, combo);
    }

    /** The combo for one character of typed text, with Shift where a US layout needs it. */
    public static KeyCombo forCharacter(char character) {
        if (character == '\n' || character == '\r') {
            return controlKey(0, SWT.CR);
        }
        if (character == '\t') {
            return controlKey(0, SWT.TAB);
        }
        boolean shift = Character.isUpperCase(character) || SHIFTED.indexOf(character) >= 0;
        return new KeyCombo(shift ? SWT.SHIFT : 0, 0, Character.toLowerCase(character));
    }

    /** Parses modifier names joined by '+', such as "ctrl+shift"; null or blank means none. */
    public static int modifiers(String text) {
        int modifiers = 0;
        if (text != null && !text.isBlank()) {
            for (String part : text.split("\\+")) {
                modifiers |= modifier(part);
            }
        }
        return modifiers;
    }

    /** The modifier bits (SWT.CTRL and so on). */
    public int modifiers() {
        return modifiers;
    }

    /** The SWT key code of a key without a character, else 0. */
    public int keyCode() {
        return keyCode;
    }

    /** The character of the key, else 0. */
    public char character() {
        return character;
    }

    /** Splits at '+' separators; a '+' at the start or right after a separator is the plus key itself. */
    private static List<String> splitOnPlus(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '+' && i > start) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** The key part of a combo: a named key, a named character or one character. */
    private static KeyCombo key(String key, int modifiers, String combo) {
        String name = key.toLowerCase(Locale.ROOT);
        if (KEY_CODES.containsKey(name)) {
            return new KeyCombo(modifiers, KEY_CODES.get(name), (char) 0);
        }
        if (CONTROL_KEYS.containsKey(name)) {
            return controlKey(modifiers, CONTROL_KEYS.get(name));
        }
        if (NAMED_CHARACTERS.containsKey(name)) {
            return new KeyCombo(modifiers, 0, NAMED_CHARACTERS.get(name));
        }
        if (key.length() == 1) {
            KeyCombo typed = forCharacter(key.charAt(0));
            return new KeyCombo(modifiers | typed.modifiers, 0, typed.character);
        }
        throw new ToolException("Unknown key \"" + key + "\" in \"" + combo + "\"; use names such as enter, tab, esc,"
                + " backspace, delete, space, up, down, left, right, home, end, pageup, pagedown, f1-f12,"
                + " or a single character, with ctrl+, shift+, alt+ or cmd+ in front");
    }

    /** A control key such as Enter, with its character also as the key code. */
    private static KeyCombo controlKey(int modifiers, char character) {
        return new KeyCombo(modifiers, character, character);
    }

    /** The bit of one modifier name. */
    private static int modifier(String name) {
        Integer bit = MODIFIERS.get(name.trim().toLowerCase(Locale.ROOT));
        if (bit == null) {
            throw new ToolException("Unknown modifier \"" + name + "\"; use ctrl, shift, alt or cmd");
        }
        return bit;
    }
}
