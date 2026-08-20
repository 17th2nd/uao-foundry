package org.seventeenthsecond.uaofoundry.identity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An inbound reference to be resolved against registered identity.
 *
 * <p>A reference is what one observer called something. It is not an identity, and the kind of
 * reference materially changes how much it can prove: a uid or resolution key is a direct address,
 * an external identifier is durable third-party evidence, and a human label or alias is only a
 * hint.
 */
public record IdentityReference(Kind kind, String scheme, String value) {

    public enum Kind {
        /** A canonical {@code uao-<12 hex>} address. Direct. */
        UID,
        /** A canonical Foundry resolution key. Direct. */
        RESOLUTION_KEY,
        /** A durable third-party identifier. Strong evidence, still evidence. */
        EXTERNAL_IDENTIFIER,
        /** A human-readable name. Weak evidence; never sufficient alone. */
        ALIAS
    }

    public IdentityReference {
        if (kind == null) throw new IllegalArgumentException("Identity reference kind is required.");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identity reference value must be non-blank.");
        if (kind == Kind.EXTERNAL_IDENTIFIER) {
            if (scheme == null || scheme.isBlank()) throw new IllegalArgumentException("External identifier reference requires a scheme.");
        } else if (scheme != null) {
            throw new IllegalArgumentException("Only an external identifier reference carries a scheme.");
        }
    }

    public static IdentityReference uid(String value) { return new IdentityReference(Kind.UID, null, value); }
    public static IdentityReference resolutionKey(String value) { return new IdentityReference(Kind.RESOLUTION_KEY, null, value); }
    public static IdentityReference externalIdentifier(String scheme, String value) { return new IdentityReference(Kind.EXTERNAL_IDENTIFIER, scheme, value); }
    public static IdentityReference alias(String value) { return new IdentityReference(Kind.ALIAS, null, value); }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("kind", kind.name());
        if (scheme != null) out.put("scheme", scheme);
        out.put("value", value);
        return out;
    }
}
