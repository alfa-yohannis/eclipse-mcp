package io.github.alfayohannis.eclipse.mcp.client.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The arguments of one command: positional values and --options. An option
 * takes the next argument as its value unless that one starts with "--" or
 * there is none, which makes it a flag.
 */
public final class CommandLine {

    private final List<String> positional = new ArrayList<>();
    private final Map<String, String> options = new LinkedHashMap<>();

    /** Parses the arguments after the command name. */
    public CommandLine(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            i = consume(arguments, i);
        }
    }

    /** The positional value at the index, or null. */
    public String positional(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    /** The positional value at the index; fails with the usage when it is missing. */
    public String required(int index, String name) {
        String value = positional(index);
        if (value == null) {
            throw new UsageException("Missing " + name);
        }
        return value;
    }

    /** All positional values from the index on. */
    public List<String> rest(int fromIndex) {
        return fromIndex < positional.size() ? positional.subList(fromIndex, positional.size()) : List.of();
    }

    /** The option's value, or null. */
    public String option(String name) {
        return options.get(name);
    }

    /** Whether the flag or option is present. */
    public boolean flag(String name) {
        return options.containsKey(name);
    }

    /** The option as a whole number, or null. */
    public Integer intOption(String name) {
        String value = option(name);
        return value == null ? null : parseInt(name, value);
    }

    /** The option as a number, or null. */
    public Double doubleOption(String name) {
        String value = option(name);
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new UsageException("--" + name + " takes a number");
        }
    }

    /** The positional value at the index as a whole number. */
    public int requiredInt(int index, String name) {
        return parseInt(name, required(index, name));
    }

    /** Takes the argument at the index, and its value for an option; returns the last index used. */
    private int consume(List<String> arguments, int index) {
        String argument = arguments.get(index);
        if (!argument.startsWith("--") || argument.length() == 2) {
            positional.add(argument);
            return index;
        }
        String name = argument.substring(2);
        boolean hasValue = index + 1 < arguments.size() && !arguments.get(index + 1).startsWith("--");
        options.put(name, hasValue ? arguments.get(index + 1) : "true");
        return hasValue ? index + 1 : index;
    }

    /** A whole number, or a usage error naming the argument. */
    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new UsageException(name + " must be a whole number, not \"" + value + "\"");
        }
    }
}
