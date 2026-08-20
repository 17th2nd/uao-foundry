package org.seventeenthsecond.uaofoundry.negativespace;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A relationship someone expects to exist, recorded so that its <em>absence</em> can be evaluated.
 *
 * <p>An expectation is not a claim that the relationship exists, and evaluating one never creates
 * it. That distinction is the whole point: the Foundry must be able to say "we looked for this and
 * did not find it" without that ever shading into "so we made it".
 *
 * <p>Both endpoints are named by persistent uid rather than by label, because
 * {@link NegativeSpaceEvaluator} refuses to evaluate an expectation whose endpoints are not
 * certainly identified — an absence between two things you cannot pin down is not evidence of
 * anything.
 */
public record ExpectedRelationship(
        String expectationId,
        String subjectUid,
        String typeVersion,
        String subjectRole,
        String objectUid,
        String objectRole,
        String rationale) {

    public static ExpectedRelationship create(String subjectUid, String typeVersion, String subjectRole,
                                              String objectUid, String objectRole, String rationale) {
        requireNonBlank(subjectUid, "subjectUid");
        requireNonBlank(typeVersion, "typeVersion");
        requireNonBlank(subjectRole, "subjectRole");
        requireNonBlank(objectUid, "objectUid");
        requireNonBlank(objectRole, "objectRole");
        requireNonBlank(rationale, "rationale");
        if (subjectUid.equals(objectUid)) {
            throw new IllegalArgumentException("An expected relationship must relate two distinct identities.");
        }
        Map<String,Object> projection = new LinkedHashMap<>();
        projection.put("subjectUid", subjectUid);
        projection.put("typeVersion", typeVersion);
        projection.put("subjectRole", subjectRole);
        projection.put("objectUid", objectUid);
        projection.put("objectRole", objectRole);
        String id = StableIdentifiers.forJson("exp", 16, projection);
        return new ExpectedRelationship(id, subjectUid, typeVersion, subjectRole, objectUid, objectRole, rationale);
    }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("expectationId", expectationId);
        out.put("subjectUid", subjectUid);
        out.put("typeVersion", typeVersion);
        out.put("subjectRole", subjectRole);
        out.put("objectUid", objectUid);
        out.put("objectRole", objectRole);
        out.put("rationale", rationale);
        return out;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Expected relationship " + label + " must be non-blank.");
    }
}
