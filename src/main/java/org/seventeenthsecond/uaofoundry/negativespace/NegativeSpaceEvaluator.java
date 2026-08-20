package org.seventeenthsecond.uaofoundry.negativespace;

import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolution;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolver;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether an expected relationship is observed, absent, or simply unknown.
 *
 * <h2>The three distinctions this exists to preserve</h2>
 *
 * <pre>
 * MISSING  ≠ FALSE      not finding it is not evidence it is untrue
 * UNKNOWN  ≠ ABSENT     not having looked properly is not the same as having looked and found nothing
 * EXPECTED ≠ OBSERVED   expecting it does not make it so, and never manufactures it
 * </pre>
 *
 * Absence is only informative when two conditions hold: the endpoints are <b>certainly
 * identified</b>, and the observation scope is <b>bounded and complete enough for the absence to
 * mean something</b>. Either failing yields {@code UNKNOWN}, never {@code ABSENT}.
 *
 * <h2>The finding: over certified relationships, absence is currently vacuous</h2>
 *
 * Canonical URO publication is fail-closed pending {@code 17th2nd/ASA#29}, so the certified
 * relationship set is empty <em>by authority</em> rather than <em>by observation</em>. Every
 * expectation evaluated against it would come back "absent" — and would be right, and would mean
 * nothing, because a universe in which nothing can exist reports every absence identically.
 *
 * <p>Reporting {@code ABSENT_WITHIN_SCOPE} there would be the most dangerous possible output of
 * this component: technically true, trivially derived, and readable as though the Foundry had
 * looked. So the certified universe returns {@link Evaluation#SCOPE_VACUOUS} instead, and negative
 * space over certified relationships becomes genuinely usable only when ASA#29 closes.
 *
 * <h2>The candidate universe</h2>
 *
 * Retained, identity-bound relationship candidates <em>are</em> a real observation universe, so
 * they can be evaluated meaningfully today. Every such result is marked {@code certifying: false}:
 * observing a candidate is evidence that someone asserted the relationship, not that ASA governs
 * it. This is what lets the machinery be exercised and trusted before the authority arrives.
 *
 * <p>Research-level. Nothing here participates in manufacture, publication or registry admission.
 */
public final class NegativeSpaceEvaluator {

    public enum Universe {
        /** Canonical, ASA-governed UROs. Empty by authority today. */
        CERTIFIED,
        /** Retained, identity-bound relationship candidates. Evidence only; never certification. */
        CANDIDATE
    }

    public enum Evaluation {
        /** Found within the stated scope. */
        OBSERVED,
        /** Not found, and the scope was bounded and meaningful enough for that to be informative. */
        ABSENT_WITHIN_SCOPE,
        /** Cannot say. Endpoints not certainly identified, or the scope cannot support a conclusion. */
        UNKNOWN,
        /** The observation universe is empty by authority, so absence within it carries no information. */
        SCOPE_VACUOUS
    }

    public static final String IDENTITY_NOT_CERTAIN = "IDENTITY_NOT_CERTAIN";
    public static final String EMPTY_OBSERVATION_SCOPE = "EMPTY_OBSERVATION_SCOPE";
    public static final String URO_TYPE_AUTHORITY_UNAVAILABLE = "URO_TYPE_AUTHORITY_UNAVAILABLE";
    public static final String CANDIDATE_OBSERVED = "CANDIDATE_OBSERVED";
    public static final String NOT_FOUND_IN_BOUNDED_SCOPE = "NOT_FOUND_IN_BOUNDED_SCOPE";

    private final Map<String,Object> registryIndex;
    private final IdentityResolver resolver;

    public NegativeSpaceEvaluator(Map<String,Object> registryIndex) {
        this.registryIndex = registryIndex;
        this.resolver = new IdentityResolver(registryIndex);
    }

    public Map<String,Object> evaluate(ExpectedRelationship expectation, Universe universe) {
        List<String> reasonCodes = new ArrayList<>();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("negativeSpaceVersion", "0.1.0");
        out.put("status", "RESEARCH_LEVEL_NOT_AUTHORITATIVE");
        out.put("expectation", expectation.toMap());
        out.put("universe", universe.name());
        out.put("observationScope", observationScope());

        // Identity certainty first. An absence between two things you cannot pin down says nothing
        // about the world; it says you did not know what you were looking for.
        IdentityResolution subject = resolver.resolve(IdentityReference.uid(expectation.subjectUid()));
        IdentityResolution object = resolver.resolve(IdentityReference.uid(expectation.objectUid()));
        Map<String,Object> endpoints = new LinkedHashMap<>();
        endpoints.put("subject", subject.toMap());
        endpoints.put("object", object.toMap());
        out.put("endpointResolution", endpoints);

        if (!subject.isSame() || !object.isSame()) {
            reasonCodes.add(IDENTITY_NOT_CERTAIN);
            return finish(out, Evaluation.UNKNOWN, reasonCodes, false);
        }

        List<Object> packages = list(registryIndex.get("packages"));
        if (packages.isEmpty()) {
            // Nothing was observed because nothing was there to observe.
            reasonCodes.add(EMPTY_OBSERVATION_SCOPE);
            return finish(out, Evaluation.UNKNOWN, reasonCodes, false);
        }

        if (universe == Universe.CERTIFIED) {
            reasonCodes.add(URO_TYPE_AUTHORITY_UNAVAILABLE);
            out.put("explanation", "The certified relationship set is empty by authority rather than by observation, "
                    + "so every expectation would evaluate as absent and no absence would carry information. "
                    + "Negative space over certified relationships becomes meaningful when 17th2nd/ASA#29 closes.");
            return finish(out, Evaluation.SCOPE_VACUOUS, reasonCodes, false);
        }

        boolean observed = candidateObserved(expectation);
        reasonCodes.add(observed ? CANDIDATE_OBSERVED : NOT_FOUND_IN_BOUNDED_SCOPE);
        return finish(out, observed ? Evaluation.OBSERVED : Evaluation.ABSENT_WITHIN_SCOPE, reasonCodes, false);
    }

    /**
     * Looks for the expectation among retained relationship candidates, matched on both endpoints,
     * their roles and the declared type version. A match on the subject alone would let an
     * unrelated relationship satisfy the expectation.
     */
    private boolean candidateObserved(ExpectedRelationship expectation) {
        boolean subjectSide = false;
        boolean objectSide = false;
        for (Object raw : list(registryIndex.get("identities"))) {
            Map<String,Object> identity = map(raw);
            String uid = String.valueOf(identity.get("uid"));
            for (Object rawBinding : list(identity.get("relationshipBindings"))) {
                Map<String,Object> binding = map(rawBinding);
                if (!expectation.typeVersion().equals(binding.get("typeVersion"))) continue;
                if (uid.equals(expectation.subjectUid()) && expectation.subjectRole().equals(binding.get("role"))) subjectSide = true;
                if (uid.equals(expectation.objectUid()) && expectation.objectRole().equals(binding.get("role"))) objectSide = true;
            }
        }
        return subjectSide && objectSide;
    }

    /**
     * States exactly what was searched, so that an absence can be judged. An absence reported
     * without its scope is uninterpretable — the reader cannot tell a thorough search from none.
     */
    private Map<String,Object> observationScope() {
        List<Object> packages = list(registryIndex.get("packages"));
        List<Object> packageIds = new ArrayList<>();
        for (Object raw : packages) packageIds.add(map(raw).get("packageId"));
        Map<String,Object> scope = new LinkedHashMap<>();
        scope.put("registryIndexHash", Hashes.canonicalJson(registryIndex));
        scope.put("packageCount", java.math.BigDecimal.valueOf(packages.size()));
        scope.put("packageIds", packageIds);
        scope.put("bounded", Boolean.TRUE);
        scope.put("caveat", "Scope is the verified registry only. A relationship stated outside this registry "
                + "is not observed here and its absence within this scope is not evidence of its non-existence.");
        return scope;
    }

    private static Map<String,Object> finish(Map<String,Object> out, Evaluation evaluation, List<String> reasonCodes, boolean certifying) {
        out.put("evaluation", evaluation.name());
        out.put("reasonCodes", new ArrayList<>(reasonCodes));
        out.put("certifying", certifying);
        out.put("rule", "MISSING is not FALSE; UNKNOWN is not ABSENT; EXPECTED is not OBSERVED. "
                + "Evaluating an expectation never manufactures the relationship it describes.");
        return out;
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) {
        if (!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("Expected an object.");
        return (Map<String,Object>) m;
    }
    private static List<Object> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> l)) throw new IllegalArgumentException("Expected an array.");
        return Json.array(l, "list");
    }
}
