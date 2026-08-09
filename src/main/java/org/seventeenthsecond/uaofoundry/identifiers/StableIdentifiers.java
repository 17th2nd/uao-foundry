package org.seventeenthsecond.uaofoundry.identifiers;

import org.seventeenthsecond.uaofoundry.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StableIdentifiers {
    private StableIdentifiers() {}

    public static String forJson(String prefix, int hexLength, Object value) {
        return prefix + "-" + sha256Hex(Json.canonical(value)).substring(0, hexLength);
    }

    public static String forText(String prefix, int hexLength, String value) {
        return prefix + "-" + sha256Hex(value).substring(0, hexLength);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
