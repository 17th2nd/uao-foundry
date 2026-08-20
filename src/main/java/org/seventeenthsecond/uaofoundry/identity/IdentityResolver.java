package org.seventeenthsecond.uaofoundry.identity;

import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic identity resolution over a verified registry index.
 *
 * <p>This is the layer the Foundry did not previously have. Identity was <em>derived</em> —
 * {@code uid = sha256(resolutionKey)} — but never <em>resolved</em>: nothing consulted aliases or
 * external identifiers to decide whether an inbound reference denoted an already-registered
 * object, and nothing recorded why. This resolver adds that layer strictly above the existing
 * derivation, which is unchanged.
 *
 * <p>Three rules make the layer safe:
 *
 * <ol>
 *   <li><b>No fuzzy matching.</b> Every comparison is exact after NFKC/case normalisation. The
 *       registry's substring token search remains a discovery-ranking aid and is never consulted
 *       here.</li>
 *   <li><b>Aliases never decide.</b> A matching label or alias yields {@code UNRESOLVED} with the
 *       candidates named, never {@code SAME}. Two things are routinely called the same word.</li>
 *   <li><b>Unreconciled variants stay sticky.</b> An identity carrying
 *       {@code MULTIPLE_UNRECONCILED_VARIANTS} resolves to {@code UNRESOLVED} regardless of how
 *       strong the addressing evidence is, preserving the existing fail-closed policy. Unrelated
 *       identities are unaffected.</li>
 * </ol>
 *
 * <p>The resolver is read-only. It never mutates the registry and never manufactures an identity.
 */
public final class IdentityResolver {
    private final Map<String,Map<String,Object>> byUid = new LinkedHashMap<>();
    private final Map<String,String> byResolutionKey = new TreeMap<>();
    private final Map<String,Set<String>> byExternalIdentifier = new TreeMap<>();
    private final Map<String,Set<String>> byNormalizedName = new TreeMap<>();

    /**
     * @param registryIndex a registry index already verified by
     *        {@link org.seventeenthsecond.uaofoundry.registry.FoundryRegistry#index()}. Passing an
     *        unverified index would let tampered material influence resolution.
     */
    public IdentityResolver(Map<String,Object> registryIndex) {
        if (registryIndex == null) throw new IllegalArgumentException("Registry index is required.");
        for (Object raw : array(registryIndex.get("identities"))) {
            Map<String,Object> identity = object(raw);
            String uid = string(identity.get("uid"), "uid");
            byUid.put(uid, identity);
            byResolutionKey.put(string(identity.get("resolutionKey"), "resolutionKey"), uid);
            Object externals = identity.get("externalIdentifiers");
            if (externals instanceof Map<?,?> map) {
                for (Map.Entry<?,?> entry : map.entrySet()) {
                    String token = ExternalIdentifiers.token(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    byExternalIdentifier.computeIfAbsent(token, ignored -> new LinkedHashSet<>()).add(uid);
                }
            }
            for (String name : names(identity)) {
                byNormalizedName.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(uid);
            }
        }
    }

    /** Resolves a single inbound reference. */
    public IdentityResolution resolve(IdentityReference reference) {
        return switch (reference.kind()) {
            case UID -> direct(reference, byUid.containsKey(reference.value()) ? reference.value() : null,
                    IdentityResolution.EXACT_UID_MATCH);
            case RESOLUTION_KEY -> direct(reference, byResolutionKey.get(reference.value()),
                    IdentityResolution.EXACT_RESOLUTION_KEY_MATCH);
            case EXTERNAL_IDENTIFIER -> external(reference);
            case ALIAS -> alias(reference);
        };
    }

    /**
     * Resolves a whole candidate identity — its proposed resolution key together with its declared
     * external identifiers — which is what manufacture actually needs.
     *
     * <p>This surfaces the two conflict shapes that a key-only lookup cannot see:
     *
     * <ul>
     *   <li><b>Same address, contradicting evidence.</b> The key matches a registered identity but
     *       a shared external scheme names a different identifier. That is positive evidence of
     *       difference and yields {@code DIFFERENT}, which admission must treat as fail-closed
     *       rather than as a split.</li>
     *   <li><b>Different address, same evidence.</b> An external identifier matches a registered
     *       identity holding a different key. That is evidence of sameness across two addresses —
     *       a merge candidate. It yields {@code UNRESOLVED}, because merging is a governed
     *       append-preserving operation and must never happen implicitly during manufacture.</li>
     * </ul>
     */
    public IdentityResolution resolveCandidate(String resolutionKey, Map<String,String> externalIdentifiers) {
        IdentityReference reference = IdentityReference.resolutionKey(resolutionKey);
        Map<String,String> declared = externalIdentifiers == null ? Map.of() : externalIdentifiers;
        String uid = byResolutionKey.get(resolutionKey);

        if (uid != null) {
            Map<String,Object> registered = byUid.get(uid);
            String contradiction = contradictingScheme(registered, declared);
            if (contradiction != null) {
                return IdentityResolution.different(reference,
                        IdentityResolution.EXTERNAL_IDENTIFIER_CONTRADICTION, List.of(uid));
            }
            String lifecycle = lifecycleRefusal(registered);
            if (lifecycle != null) {
                return IdentityResolution.unresolved(reference, lifecycle, lifecycleCandidates(uid, registered));
            }
            if (unreconciled(registered)) {
                return IdentityResolution.unresolved(reference,
                        IdentityResolution.SEMANTIC_VARIANTS_UNRECONCILED, List.of(uid));
            }
            return IdentityResolution.same(reference, uid,
                    string(registered.get("resolutionKey"), "resolutionKey"), IdentityResolution.EXACT_RESOLUTION_KEY_MATCH);
        }

        Set<String> crossKey = new LinkedHashSet<>();
        for (Map.Entry<String,String> entry : declared.entrySet()) {
            Set<String> hits = byExternalIdentifier.get(ExternalIdentifiers.token(entry.getKey(), entry.getValue()));
            if (hits != null) crossKey.addAll(hits);
        }
        if (!crossKey.isEmpty()) {
            return IdentityResolution.unresolved(reference,
                    IdentityResolution.EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH, new ArrayList<>(crossKey));
        }
        return IdentityResolution.unresolved(reference, IdentityResolution.NO_REGISTERED_MATCH, List.of());
    }

    /** Registered identities, if any, whose declared external identifiers contradict the candidate. */
    private String contradictingScheme(Map<String,Object> registered, Map<String,String> declared) {
        Object externals = registered.get("externalIdentifiers");
        if (!(externals instanceof Map<?,?> map)) return null;
        for (Map.Entry<String,String> entry : declared.entrySet()) {
            Object existing = map.get(entry.getKey());
            if (existing instanceof String value && !value.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    private IdentityResolution direct(IdentityReference reference, String uid, String reasonCode) {
        if (uid == null) return IdentityResolution.unresolved(reference, IdentityResolution.NO_REGISTERED_MATCH, List.of());
        Map<String,Object> identity = byUid.get(uid);
        String lifecycle = lifecycleRefusal(identity);
        if (lifecycle != null) {
            return IdentityResolution.unresolved(reference, lifecycle, lifecycleCandidates(uid, identity));
        }
        if (unreconciled(identity)) {
            return IdentityResolution.unresolved(reference, IdentityResolution.SEMANTIC_VARIANTS_UNRECONCILED, List.of(uid));
        }
        return IdentityResolution.same(reference, uid, string(identity.get("resolutionKey"), "resolutionKey"), reasonCode);
    }

    private IdentityResolution external(IdentityReference reference) {
        Set<String> hits = byExternalIdentifier.get(ExternalIdentifiers.token(reference.scheme(), reference.value()));
        if (hits == null || hits.isEmpty()) {
            return IdentityResolution.unresolved(reference, IdentityResolution.NO_REGISTERED_MATCH, List.of());
        }
        if (hits.size() > 1) {
            return IdentityResolution.unresolved(reference, IdentityResolution.EXTERNAL_IDENTIFIER_AMBIGUOUS, new ArrayList<>(hits));
        }
        String uid = hits.iterator().next();
        Map<String,Object> identity = byUid.get(uid);
        String lifecycle = lifecycleRefusal(identity);
        if (lifecycle != null) {
            return IdentityResolution.unresolved(reference, lifecycle, lifecycleCandidates(uid, identity));
        }
        if (unreconciled(identity)) {
            return IdentityResolution.unresolved(reference, IdentityResolution.SEMANTIC_VARIANTS_UNRECONCILED, List.of(uid));
        }
        return IdentityResolution.same(reference, uid, string(identity.get("resolutionKey"), "resolutionKey"),
                IdentityResolution.EXTERNAL_IDENTIFIER_CONTINUITY);
    }

    /** A name match is a hint. It is reported with its candidates and never decides. */
    private IdentityResolution alias(IdentityReference reference) {
        Set<String> hits = byNormalizedName.get(normalize(reference.value()));
        if (hits == null || hits.isEmpty()) {
            return IdentityResolution.unresolved(reference, IdentityResolution.NO_REGISTERED_MATCH, List.of());
        }
        return IdentityResolution.unresolved(reference, IdentityResolution.ALIAS_MATCH_INSUFFICIENT, new ArrayList<>(hits));
    }

    private static boolean unreconciled(Map<String,Object> identity) {
        return SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(identity.get("semanticVariantStatus"));
    }

    /**
     * A recorded lifecycle operation stops an identity resolving, and names what it became.
     *
     * <p>Resolution is deliberately not redirected to the successor. Silently returning B when
     * asked for A would change what a later manufacture produces without anyone requesting the
     * change — the destructive rewrite the append-preserving design exists to prevent. The caller
     * is told the identity was superseded, retired, merged or split, and which identities resulted,
     * and decides for itself.
     *
     * @return a refusal reason code, or {@code null} when the identity is still active
     */
    private static String lifecycleRefusal(Map<String,Object> identity) {
        Object state = identity.get("lifecycleState");
        if (state == null || IdentityOperation.ACTIVE.equals(state)) return null;
        return switch (String.valueOf(state)) {
            case IdentityOperation.SUPERSEDED -> IdentityResolution.IDENTITY_SUPERSEDED;
            case IdentityOperation.RETIRED -> IdentityResolution.IDENTITY_RETIRED;
            case IdentityOperation.MERGED -> IdentityResolution.IDENTITY_MERGED;
            case IdentityOperation.SPLIT_STATE -> IdentityResolution.IDENTITY_SPLIT;
            default -> throw new IllegalArgumentException("Unknown identity lifecycle state: " + state);
        };
    }

    /** Identities a refused lifecycle points at, so the caller can follow the history deliberately. */
    private static List<String> lifecycleCandidates(String uid, Map<String,Object> identity) {
        List<String> out = new ArrayList<>();
        out.add(uid);
        if (identity.get("successorUids") instanceof List<?> values) {
            for (Object value : values) out.add(String.valueOf(value));
        }
        return out;
    }

    private static Set<String> names(Map<String,Object> identity) {
        Set<String> out = new LinkedHashSet<>();
        for (String field : List.of("canonicalLabels", "aliases")) {
            if (identity.get(field) instanceof List<?> values) {
                for (Object value : values) out.add(normalize(String.valueOf(value)));
            }
        }
        return out;
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("Registry identity must be an object.");
        return (Map<String,Object>) map;
    }
    private static List<?> array(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Registry identities must be an array.");
        return list;
    }
    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException("Registry identity " + label + " must be a non-blank string.");
    }
}
