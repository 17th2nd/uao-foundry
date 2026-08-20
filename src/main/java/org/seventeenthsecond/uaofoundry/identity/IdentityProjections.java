package org.seventeenthsecond.uaofoundry.identity;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the two deterministic digests that materialise the identity/state separation.
 *
 * <p>The programme models a persistent identity as {@code identity(u)} carrying a sequence of
 * states {@code state(u,t0), state(u,t1), ...}. A change of state must not imply a change of
 * identity. Today the Foundry already guarantees the strong half of that — {@code uid} is a pure
 * function of {@code resolution_key}, so no state change can alter a uid. What was missing was the
 * observable half: a way to <em>see</em> that two occurrences are the same identity carrying
 * different state.
 *
 * <p>These two digests supply it:
 *
 * <ul>
 *   <li>{@code identity_digest} covers only identity-bearing material. It is stable across state
 *       change and changes only when what the identity <em>is</em> changes.</li>
 *   <li>{@code state_version} covers only state-bearing material. It changes whenever what the
 *       identity currently <em>asserts</em> changes.</li>
 * </ul>
 *
 * <p>Both are derived, never authored, so an independent verifier re-derives and compares them
 * exactly as it already does for uid and content digest. Neither may be supplied by a provider.
 *
 * <p>Neither digest is a significance, confidence, ranking or priority value. They are content
 * addresses over disjoint projections and carry no ordering meaning.
 */
public final class IdentityProjections {
    private IdentityProjections() {}

    /**
     * Identity-bearing projection. Deliberately excludes {@code source_refs} (which is provenance
     * about how the identity was evidenced, not what it is) and all assertion/state material.
     */
    public static String identityDigest(String uid, Map<String,Object> foundryIdentity) {
        Map<String,Object> projection = new LinkedHashMap<>();
        projection.put("uid", uid);
        projection.put("resolution_key", foundryIdentity.get("resolution_key"));
        projection.put("semantic_type", foundryIdentity.get("semantic_type"));
        projection.put("canonical_label", foundryIdentity.get("canonical_label"));
        projection.put("aliases", canonicalSorted(foundryIdentity.get("aliases")));
        projection.put("external_identifiers", deepCopy(foundryIdentity.get("external_identifiers")));
        return Hashes.canonicalJson(projection);
    }

    /**
     * State-bearing projection. Covers the ASA-governed lifecycle and everything the identity
     * currently asserts or references.
     */
    public static String stateVersion(Object lifecycleStatus, Object successorIdentityRef,
                                      Object assertions, Object relationshipReferences) {
        Map<String,Object> projection = new LinkedHashMap<>();
        projection.put("lifecycle_status", lifecycleStatus);
        if (successorIdentityRef != null) projection.put("successor_identity_ref", deepCopy(successorIdentityRef));
        projection.put("assertions", canonicalSorted(assertions));
        projection.put("relationship_references", canonicalSorted(relationshipReferences));
        return Hashes.canonicalJson(projection);
    }

    /** Convenience overload deriving the state version directly from a built canonical UAO. */
    public static String stateVersion(Map<String,Object> uao) {
        return stateVersion(uao.get("lifecycle_status"), uao.get("successor_identity_ref"),
                uao.get("assertions"), uao.get("relationship_references"));
    }

    private static List<Object> canonicalSorted(Object value) {
        List<Object> out = new ArrayList<>();
        if (value instanceof List<?> list) for (Object item : list) out.add(deepCopy(item));
        out.sort(Comparator.comparing(Json::canonical));
        return out;
    }

    private static Object deepCopy(Object value) { return value == null ? null : Json.parse(Json.canonical(value)); }
}
