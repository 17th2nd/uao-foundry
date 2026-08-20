package org.seventeenthsecond.uaofoundry.registry;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives a deterministic digest for one stable UAO's meaning-bearing canonical projection.
 * Occurrence provenance, Foundry source references and alias provenance are deliberately excluded
 * so that one semantic variant may be supported by different immutable package occurrences drawn
 * from different sources.
 */
public final class SemanticVariants {
    public static final String SINGLE_VARIANT = "SINGLE_VARIANT";
    public static final String MULTIPLE_UNRECONCILED_VARIANTS = "MULTIPLE_UNRECONCILED_VARIANTS";

    private SemanticVariants() {}

    public static String digest(Object rawUao) {
        Map<String,Object> uao = object(rawUao, "canonical UAO");
        Map<String,Object> projection = new LinkedHashMap<>();
        projection.put("uid", required(uao, "uid"));
        projection.put("lifecycle_status", required(uao, "lifecycle_status"));
        if (uao.containsKey("successor_identity_ref")) {
            projection.put("successor_identity_ref", deepCopy(uao.get("successor_identity_ref")));
        }

        Map<String,Object> internal = object(deepCopy(required(uao, "internal_state")), "canonical UAO internal_state");
        Map<String,Object> foundryIdentity = object(internal.get("foundry_identity"), "canonical UAO foundry_identity");
        // Both removals are provenance, not meaning. source_refs and alias_provenance record how
        // the identity was evidenced; the names themselves stay, in canonical_label and aliases.
        // Leaving alias_provenance in would make the digest sensitive to which sources happened to
        // supply a name, so the same identity acquired from different sources would be flagged as
        // an unreconciled variant and refused for reuse -- defeating the point of persistent
        // identity, which is that one identity may be evidenced repeatedly from different places.
        foundryIdentity.remove("source_refs");
        foundryIdentity.remove("alias_provenance");
        if (foundryIdentity.get("aliases") instanceof List<?> aliases) {
            List<Object> sortedAliases = new ArrayList<>();
            for (Object alias : aliases) sortedAliases.add(alias);
            sortedAliases.sort(Comparator.comparing(Json::canonical));
            foundryIdentity.put("aliases", sortedAliases);
        }
        projection.put("internal_state", internal);
        projection.put("assertions", canonicalSortedList(required(uao, "assertions"), "canonical UAO assertions"));
        projection.put("relationship_references", canonicalSortedList(
                required(uao, "relationship_references"), "canonical UAO relationship_references"));
        projection.put("disclaimer", required(uao, "disclaimer"));
        return Hashes.canonicalJson(projection);
    }

    private static List<Object> canonicalSortedList(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be an array.");
        List<Object> out = new ArrayList<>();
        for (Object item : list) out.add(deepCopy(item));
        out.sort(Comparator.comparing(Json::canonical));
        return out;
    }

    private static Object required(Map<String,Object> map, String field) {
        if (!map.containsKey(field)) throw new IllegalArgumentException("canonical UAO is missing " + field + ".");
        return map.get(field);
    }

    private static Object deepCopy(Object value) { return Json.parse(Json.canonical(value)); }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value, String label) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException(label + " must be an object.");
        return (Map<String,Object>) map;
    }
}
