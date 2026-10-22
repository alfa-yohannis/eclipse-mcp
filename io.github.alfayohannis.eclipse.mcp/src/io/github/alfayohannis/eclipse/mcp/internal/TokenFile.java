package io.github.alfayohannis.eclipse.mcp.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;

/**
 * The bearer token that every request must carry. It lives in a file only the
 * user can read, so local clients can find it while other users and web pages
 * cannot; it is created on first use.
 */
final class TokenFile {

    private static final int TOKEN_BYTES = 24;

    /** Not instantiable: the class only has static members. */
    private TokenFile() {
    }

    /** The token: the override if set, else the file's content, creating the file when it is missing. */
    static String token() throws IOException {
        String override = Config.tokenOverride();
        if (override != null) {
            return override.trim();
        }
        Path file = path();
        if (Files.exists(file)) {
            return Files.readString(file).trim();
        }
        return create(file);
    }

    /** The token file in the configuration directory. */
    static Path path() {
        return Config.directory().resolve("token");
    }

    /** Where the token comes from, in words for the status dialog. */
    static String source() {
        if (System.getProperty("eclipse.mcp.token") != null) {
            return "the system property eclipse.mcp.token";
        }
        if (System.getenv("ECLIPSE_MCP_TOKEN") != null) {
            return "the environment variable ECLIPSE_MCP_TOKEN";
        }
        return path().toString();
    }

    /** Writes a new random token readable only by the user and returns it. */
    private static String create(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String token = randomHex();
        Files.writeString(file, token + "\n");
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // not a POSIX file system; the directory's own protection applies
        }
        return token;
    }

    /** 24 random bytes as hex, enough that guessing is hopeless. */
    private static String randomHex() {
        byte[] random = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(random);
        StringBuilder hex = new StringBuilder();
        for (byte value : random) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
