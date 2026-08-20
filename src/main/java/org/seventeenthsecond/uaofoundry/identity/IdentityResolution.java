package org.seventeenthsecond.uaofoundry.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of resolving one {@link IdentityReference} against registered identity.
 *
 * <p>Carries the evidence, not merely the verdict. The question a later reader must be able to
 * answer is <em>why do we think these references are the same object?</em> — so the reason codes
 * and the matched identities are part of the record, and a decision without a reason code is
 * rejected at construction.
 *
 * <p>There is deliberately no confidence score. A numeric confidence would invite ordering,
 * thresholding and eventual silent auto-merge, which is precisely the behaviour the semantic
 * variant policy exists to prevent.
 */
public record IdentityResolution(
        IdentityReference reference,
        IdentityDecision decision,
        String uid,
        String resolutionKey,
        List<String> reasonCodes,
        List<String> candidateUids) {

    // Decision reason codes. Stable strings: they are written into package evidence.
    public static final String EXACT_UID_MATCH = "EXACT_UID_MATCH";
    public static final String EXACT_RESOLUTION_KEY_MATCH = "EXACT_RESOLUTION_KEY_MATCH";
    public static final String EXTERNAL_IDENTIFIER_CONTINUITY = "EXTERNAL_IDENTIFIER_CONTINUITY";
    public static final String EXTERNAL_IDENTIFIER_AMBIGUOUS = "EXTERNAL_IDENTIFIER_AMBIGUOUS";
    public static final String EXTERNAL_IDENTIFIER_CONTRADICTION = "EXTERNAL_IDENTIFIER_CONTRADICTION";
    public static final String ALIAS_MATCH_INSUFFICIENT = "ALIAS_MATCH_INSUFFICIENT";
    public static final String NO_REGISTERED_MATCH = "NO_REGISTERED_MATCH";
    public static final String SEMANTIC_VARIANTS_UNRECONCILED = "SEMANTIC_VARIANTS_UNRECONCILED";
    public static final String EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH = "EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH";
    public static final String REGISTRY_UNAVAILABLE = "REGISTRY_UNAVAILABLE";

    public IdentityResolution {
        if (reference == null) throw new IllegalArgumentException("Identity resolution requires a reference.");
        if (decision == null) throw new IllegalArgumentException("Identity resolution requires a decision.");
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Identity resolution requires at least one reason code.");
        }
        reasonCodes = List.copyOf(reasonCodes);
        candidateUids = candidateUids == null ? List.of() : List.copyOf(candidateUids);
        if (decision == IdentityDecision.SAME && (uid == null || uid.isBlank())) {
            throw new IllegalArgumentException("A SAME decision must name the resolved uid.");
        }
        if (decision != IdentityDecision.SAME && uid != null) {
            throw new IllegalArgumentException("Only a SAME decision may bind a uid; use candidateUids otherwise.");
        }
    }

    public static IdentityResolution same(IdentityReference reference, String uid, String resolutionKey, String reasonCode) {
        return new IdentityResolution(reference, IdentityDecision.SAME, uid, resolutionKey, List.of(reasonCode), List.of(uid));
    }

    public static IdentityResolution unresolved(IdentityReference reference, String reasonCode, List<String> candidateUids) {
        return new IdentityResolution(reference, IdentityDecision.UNRESOLVED, null, null, List.of(reasonCode), candidateUids);
    }

    public static IdentityResolution different(IdentityReference reference, String reasonCode, List<String> candidateUids) {
        return new IdentityResolution(reference, IdentityDecision.DIFFERENT, null, null, List.of(reasonCode), candidateUids);
    }

    public boolean isSame() { return decision == IdentityDecision.SAME; }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("reference", reference.toMap());
        out.put("decision", decision.name());
        if (uid != null) out.put("uid", uid);
        if (resolutionKey != null) out.put("resolutionKey", resolutionKey);
        out.put("reasonCodes", new ArrayList<>(reasonCodes));
        out.put("candidateUids", new ArrayList<>(candidateUids));
        return out;
    }
}
