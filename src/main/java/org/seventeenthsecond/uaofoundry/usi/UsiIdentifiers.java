package org.seventeenthsecond.uaofoundry.usi;

import java.util.regex.Pattern;

/**
 * The reserved {@code uao-} ⟷ {@code usi-} identifier mapping, per ADR-0005 §4.
 *
 * <h2>This is called by nothing</h2>
 *
 * Not by manufacture, not by the registry, not by verification, not by the application API. It
 * exists so that Option C — a clean {@code usi-<12 hex>} namespace — is a switch to throw rather
 * than a design to invent, on the day ASA changes the CSS primitive shape.
 *
 * <p>It is not used today because the prefix is <b>ASA-pinned</b>, not a Foundry choice:
 * {@code schemas/asa/uao.schema.json} is a non-authoritative projection of ASA CSS 2026.1 and
 * ADR-0002 §2 requires the {@code uao-<12 hex>} shape to be preserved. Emitting {@code usi-} in a
 * canonical {@code uid} would be the Foundry reinterpreting an ASA primitive.
 *
 * <p>The mapping is a pure re-prefix over identical hex, so it is total, reversible and
 * information-preserving — which is also precisely why minting a second live identifier today
 * would add no information while doubling the forms in circulation. See ADR-0005.
 */
public final class UsiIdentifiers {
    /** The scheme name carried alongside an identifier so a consumer knows what it is holding. */
    public static final String LEGACY_SCHEME = "legacy-uao";
    /** The scheme name a future governed migration would use. Not emitted today. */
    public static final String USI_SCHEME = "usi";

    private static final Pattern LEGACY = Pattern.compile("^uao-([a-f0-9]{12})$");
    private static final Pattern USI = Pattern.compile("^usi-([a-f0-9]{12})$");

    private UsiIdentifiers() {}

    public static boolean isLegacy(String identifier) {
        return identifier != null && LEGACY.matcher(identifier).matches();
    }

    public static boolean isUsi(String identifier) {
        return identifier != null && USI.matcher(identifier).matches();
    }

    /** {@code uao-X → usi-X}. Reserved for a governed migration; not emitted. */
    public static String toUsi(String legacyIdentifier) {
        var matcher = LEGACY.matcher(require(legacyIdentifier));
        if (!matcher.matches()) throw new IllegalArgumentException("Not a legacy uao identifier: " + legacyIdentifier);
        return "usi-" + matcher.group(1);
    }

    /** {@code usi-X → uao-X}. The inverse, so a migration is reversible. */
    public static String toLegacy(String usiIdentifier) {
        var matcher = USI.matcher(require(usiIdentifier));
        if (!matcher.matches()) throw new IllegalArgumentException("Not a usi identifier: " + usiIdentifier);
        return "uao-" + matcher.group(1);
    }

    /**
     * The scheme an identifier is expressed in. Carried in the application API beside the value so
     * that {@code usiId} names the role while {@code identifierScheme} names the format.
     */
    public static String schemeOf(String identifier) {
        if (isLegacy(identifier)) return LEGACY_SCHEME;
        if (isUsi(identifier)) return USI_SCHEME;
        throw new IllegalArgumentException("Unrecognised identifier scheme: " + identifier);
    }

    private static String require(String identifier) {
        if (identifier == null || identifier.isBlank()) throw new IllegalArgumentException("Identifier must be non-blank.");
        return identifier;
    }
}
