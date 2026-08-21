package org.seventeenthsecond.uaofoundry.identifiers;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Foundry-owned syntactic discipline for stable provider-supplied semantic identity keys. */
public final class ResolutionKeys {
    private static final Pattern FOUNDRY = Pattern.compile("^foundry:v0\\.1:[a-z0-9._-]+:[a-z0-9._-]+$");
    private static final Pattern FIXTURE = Pattern.compile("^fixture:[a-z0-9._-]+:[a-z0-9._-]+$");
    private static final Pattern EXT = Pattern.compile("^ext:[a-z][a-z0-9._-]*:[^\\s]+$");
    private ResolutionKeys() {}

    public static String requireCanonical(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("resolutionKey must be non-blank.");
        String nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        if (!raw.equals(nfkc) || !raw.equals(raw.strip())) {
            throw new IllegalArgumentException("resolutionKey must already be NFKC-normalized and contain no leading/trailing whitespace: " + raw);
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("resolutionKey must not contain whitespace: " + raw);
        }
        String canonical;
        if (raw.startsWith("foundry:") || raw.startsWith("fixture:")) canonical = raw.toLowerCase(Locale.ROOT);
        else if (raw.startsWith("ext:")) {
            int second = raw.indexOf(':', 4);
            if (second < 0) throw new IllegalArgumentException("ext resolutionKey must be ext:<scheme>:<identifier>: " + raw);
            canonical = "ext:" + raw.substring(4, second).toLowerCase(Locale.ROOT) + raw.substring(second);
        } else throw new IllegalArgumentException("resolutionKey must use foundry:v0.1:*, fixture:* or ext:* namespace: " + raw);
        if (!raw.equals(canonical)) throw new IllegalArgumentException("resolutionKey is not canonical; expected " + canonical + " but received " + raw);
        if (!(FOUNDRY.matcher(raw).matches() || FIXTURE.matcher(raw).matches() || EXT.matcher(raw).matches())) {
            throw new IllegalArgumentException("resolutionKey does not match the canonical Foundry key grammar: " + raw);
        }
        return raw;
    }

    /**
     * Extracts the semantic type already encoded in the canonical key grammar, if the namespace
     * carries one.
     *
     * <p>{@code foundry:v0.1:<semantic-type>:<label>} and {@code fixture:<semantic-type>:<label>}
     * both declare a semantic type by construction. {@code ext:<scheme>:<identifier>} does not:
     * an external registry identifier says which thing is meant without saying what kind of thing
     * it is. In that case the type is genuinely unknown to the Foundry and {@code null} is
     * returned rather than a guess inferred from the scheme.
     *
     * @param canonicalKey a key already accepted by {@link #requireCanonical(String)}
     * @return the declared semantic type, or {@code null} when the namespace declares none
     */
    public static String semanticType(String canonicalKey) {
        if (canonicalKey == null) return null;
        String[] parts = canonicalKey.split(":");
        if (canonicalKey.startsWith("foundry:") && parts.length == 4) return parts[2];
        if (canonicalKey.startsWith("fixture:") && parts.length == 3) return parts[1];
        return null;
    }
}
