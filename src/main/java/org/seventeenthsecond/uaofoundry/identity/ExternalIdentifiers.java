package org.seventeenthsecond.uaofoundry.identity;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Foundry-owned syntactic discipline for durable external identifiers carried by a candidate
 * identity.
 *
 * <p>An external identifier is <em>evidence</em> about identity, never identity itself. Two
 * references sharing an external identifier are strong evidence of sameness, but the decision is
 * made by {@link IdentityResolver} against registered state, not by this class. This class only
 * guarantees that an identifier is canonical, comparable and non-ephemeral.
 *
 * <p>The scheme grammar deliberately matches the {@code ext:} scheme segment accepted by
 * {@link org.seventeenthsecond.uaofoundry.identifiers.ResolutionKeys}, so that an external
 * identifier and an {@code ext:} resolution key are mutually checkable.
 */
public final class ExternalIdentifiers {
    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9._-]*$");

    private ExternalIdentifiers() {}

    /**
     * Canonicalises a raw external identifier map, rejecting anything that cannot serve as durable
     * evidence. Returns a deterministically ordered map.
     */
    public static Map<String,String> requireCanonical(Object raw, String label) {
        Map<String,String> out = new TreeMap<>();
        if (raw == null) return out;
        if (!(raw instanceof Map<?,?> map)) throw new IllegalArgumentException(label + " must be an object.");
        for (Map.Entry<?,?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String scheme)) throw new IllegalArgumentException(label + " scheme must be a string.");
            if (!(entry.getValue() instanceof String value)) throw new IllegalArgumentException(label + " identifier must be a string: " + scheme);
            String canonicalScheme = scheme.toLowerCase(Locale.ROOT);
            if (!SCHEME.matcher(canonicalScheme).matches()) {
                throw new IllegalArgumentException(label + " scheme must match ^[a-z][a-z0-9._-]*$: " + scheme);
            }
            if (!scheme.equals(canonicalScheme)) {
                throw new IllegalArgumentException(label + " scheme must already be lower-case: " + scheme);
            }
            if (value.isBlank()) throw new IllegalArgumentException(label + " identifier must be non-blank: " + scheme);
            String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
            if (!value.equals(nfkc) || !value.equals(value.strip())) {
                throw new IllegalArgumentException(label + " identifier must already be NFKC-normalized and untrimmed-clean: " + scheme);
            }
            if (value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(label + " identifier must not contain whitespace: " + scheme);
            }
            if (out.put(canonicalScheme, value) != null) {
                throw new IllegalArgumentException(label + " declares scheme more than once: " + scheme);
            }
        }
        return out;
    }

    /**
     * Fail-closed consistency guard between an {@code ext:} resolution key and the declared
     * external identifiers. A candidate that names itself {@code ext:isbn:A} while declaring
     * {@code isbn = B} is internally contradictory and must not be manufactured.
     */
    public static void requireConsistentWithResolutionKey(String resolutionKey, Map<String,String> identifiers) {
        if (resolutionKey == null || !resolutionKey.startsWith("ext:")) return;
        int second = resolutionKey.indexOf(':', 4);
        if (second < 0) return;
        String scheme = resolutionKey.substring(4, second);
        String identifier = resolutionKey.substring(second + 1);
        String declared = identifiers.get(scheme);
        if (declared != null && !declared.equals(identifier)) {
            throw new IllegalArgumentException("EXTERNAL_IDENTIFIER_CONTRADICTION: resolutionKey " + resolutionKey
                    + " asserts " + scheme + "=" + identifier + " but externalIdentifiers declares " + scheme + "=" + declared + ".");
        }
    }

    /** Deterministic {@code scheme:identifier} lookup token used by registry indexes. */
    public static String token(String scheme, String identifier) { return scheme + ":" + identifier; }

    /** Deterministic ordered copy for canonical emission. */
    public static Map<String,Object> toCanonicalMap(Map<String,String> identifiers) {
        Map<String,Object> out = new LinkedHashMap<>();
        new TreeMap<>(identifiers).forEach(out::put);
        return out;
    }
}
