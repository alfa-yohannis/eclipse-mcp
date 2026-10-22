package io.github.alfayohannis.eclipse.mcp.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/**
 * Who may talk to the server. Only requests with the bearer token get in, and
 * requests from web pages (the Origin header) or for other host names (DNS
 * rebinding) are refused even before that, as the MCP specification asks.
 */
final class AccessPolicy {

    private static final Set<String> LOCAL_NAMES = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final byte[] token;

    /** A policy that accepts this token. */
    AccessPolicy(String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether the Authorization header carries the token; the comparison takes the same time for any guess. */
    boolean hasToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        byte[] given = authorization.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(token, given);
    }

    /** Whether an Origin header, if any, names a page on this computer. */
    boolean allowsOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && isLocalName(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Whether the Host header, if any, names this computer. */
    boolean allowsHost(String host) {
        return host == null || isLocalName(hostName(host.trim()));
    }

    /** The host name of a Host header without its port; IPv6 literals keep their brackets. */
    private static String hostName(String host) {
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    /** Whether the name is a loopback name. */
    private static boolean isLocalName(String name) {
        return name != null && LOCAL_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }
}
