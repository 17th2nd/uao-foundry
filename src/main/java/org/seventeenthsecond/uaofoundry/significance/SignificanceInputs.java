package org.seventeenthsecond.uaofoundry.significance;

import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the durable inputs a significance engine needs, and nothing else.
 *
 * <h2>What this is</h2>
 *
 * The current ASA research direction computes {@code 𝓡_v(x,q,e) = μQ.F_v(Q; A_x, R_x, C_q, e)} and
 * projects it to {@code S_v}. Of those four inputs the Foundry owns exactly two:
 *
 * <ul>
 *   <li>{@code A_x} — durable local attributes of the object: identity, semantic type, names,
 *       external identifiers, lifecycle, state versions, assertions, provenance.</li>
 *   <li>{@code R_x} — governed relationships the object participates in.</li>
 * </ul>
 *
 * {@code C_q} (objective, context, observer, environment, resources) and {@code e} (epoch) are
 * runtime-owned and deliberately absent. {@code 𝓡_v}, {@code S_v}, the projection components
 * {@code ⟨G, C↑, C↓, U, E⁺, E⁻, X, V⟩}, {@code Plan} and {@code Schedule} are engine-owned and are
 * never computed here.
 *
 * <p>The export is therefore a <em>supply</em> surface, not a computation. It answers "what do you
 * durably know about x?", not "how significant is x?" — and the boundary is stated in the payload
 * itself so a consumer cannot mistake one for the other.
 *
 * <h2>Versioned research interface</h2>
 *
 * The significance formulation is a <b>research candidate</b>, not ratified ASA authority. The
 * export therefore carries both an interface version and an explicit ratification status, so a
 * consumer binding to it knows it is binding to a moving target.
 *
 * <h2>Fail-closed cases</h2>
 *
 * Two states refuse export rather than supplying misleading inputs:
 *
 * <ul>
 *   <li><b>Unreconciled semantic variants.</b> {@code A_x} would have to be drawn from several
 *       mutually inconsistent accounts of the object, and choosing or unioning them is exactly what
 *       the variant policy forbids.</li>
 *   <li><b>A non-active lifecycle state.</b> Supplying durable attributes for a retired or merged
 *       identity invites a significance computation over an object the registry has recorded as no
 *       longer standing on its own.</li>
 * </ul>
 *
 * <h2>{@code R_x} is structurally incomplete today</h2>
 *
 * Canonical URO publication is fail-closed pending {@code 17th2nd/ASA#29}, so {@code R_x} is always
 * empty and its {@code complete} flag is always false. This is reported prominently rather than
 * quietly: a significance architecture that depends on relationships is being handed an empty
 * relationship set, and any result computed from it is a result about an object considered in
 * isolation.
 */
public final class SignificanceInputs {

    /** Version of this supply interface. Independent of the significance formulation's own version. */
    public static final String INTERFACE_VERSION = "0.1.0";

    /** The formulation these inputs are shaped for. Research candidate; not ASA-ratified. */
    public static final String FORMULATION_REFERENCE = "R_v(x,q,e) = muQ.F_v(Q; A_x, R_x, C_q, e)";

    private SignificanceInputs() {}

    /**
     * @param identity a verified registry identity record
     * @param assertions the canonical assertions of the identity's single agreed semantic variant
     */
    public static Map<String,Object> export(Map<String,Object> identity, List<Object> assertions) {
        String uid = String.valueOf(identity.get("uid"));
        String variantStatus = String.valueOf(identity.get("semanticVariantStatus"));
        if (SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(variantStatus)) {
            throw new IllegalArgumentException("MULTIPLE_UNRECONCILED_VARIANTS: significance inputs refused for uid " + uid
                    + "; A_x cannot be drawn from mutually inconsistent accounts of one object.");
        }
        Object lifecycle = identity.get("lifecycleState");
        if (lifecycle != null && !IdentityOperation.ACTIVE.equals(lifecycle)) {
            throw new IllegalArgumentException("IDENTITY_LIFECYCLE_NOT_ACTIVE: significance inputs refused for uid " + uid
                    + "; recorded lifecycle state is " + lifecycle + ".");
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("significanceInterfaceVersion", INTERFACE_VERSION);
        out.put("formulationReference", FORMULATION_REFERENCE);
        out.put("formulationStatus", "RESEARCH_CANDIDATE_NOT_RATIFIED_BY_ASA");
        out.put("uid", uid);
        out.put("A_x", durableAttributes(identity, assertions));
        out.put("R_x", relationships(identity));
        out.put("notSupplied", notSupplied());
        return out;
    }

    /** {@code A_x} — what the Foundry durably knows about the object itself. */
    private static Map<String,Object> durableAttributes(Map<String,Object> identity, List<Object> assertions) {
        Map<String,Object> identityBlock = new LinkedHashMap<>();
        identityBlock.put("uid", identity.get("uid"));
        identityBlock.put("resolutionKey", identity.get("resolutionKey"));
        identityBlock.put("semanticType", identity.get("semanticType"));
        identityBlock.put("canonicalLabels", deepCopy(identity.get("canonicalLabels")));
        identityBlock.put("aliases", deepCopy(identity.get("aliases")));
        identityBlock.put("externalIdentifiers", deepCopy(identity.get("externalIdentifiers")));

        Map<String,Object> stateBlock = new LinkedHashMap<>();
        stateBlock.put("lifecycleState", identity.get("lifecycleState"));
        stateBlock.put("stateVersions", deepCopy(identity.get("stateVersions")));
        stateBlock.put("semanticVariantStatus", identity.get("semanticVariantStatus"));

        Map<String,Object> provenanceBlock = new LinkedHashMap<>();
        provenanceBlock.put("occurrences", deepCopy(identity.get("occurrences")));
        provenanceBlock.put("identityDecisionHistory", deepCopy(identity.get("decisionHistory")));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("identity", identityBlock);
        out.put("state", stateBlock);
        // Assertions carry epistemic_class DEFERRED_ON_RECORD. They are recorded statements, not
        // established truths, and a consumer weighing them must not read them as verified fact.
        out.put("assertions", deepCopy(assertions));
        out.put("assertionEpistemicStatus", "DEFERRED_ON_RECORD");
        out.put("provenance", provenanceBlock);
        return out;
    }

    /** {@code R_x} — governed relationships, currently and structurally empty. */
    private static Map<String,Object> relationships(Map<String,Object> identity) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("canonicalRelationships", List.of());
        out.put("complete", Boolean.FALSE);
        out.put("authorityStatus", "URO_TYPE_AUTHORITY_UNAVAILABLE");
        out.put("blockedBy", "17th2nd/ASA#29");
        out.put("consequence", "R_x is empty because no governed Relationship Type role authority exists. "
                + "Any significance computed from these inputs considers the object in isolation.");
        // Retained, identity-bound relationship candidates are exposed as evidence of what R_x
        // would contain, explicitly separated from the empty governed set so the two cannot merge.
        out.put("unpublishedRelationshipCandidates", deepCopy(identity.get("relationshipBindings")));
        return out;
    }

    /** The half of the significance input space the Foundry does not own, named explicitly. */
    private static Map<String,Object> notSupplied() {
        Map<String,Object> runtime = new LinkedHashMap<>();
        runtime.put("q", "objective — runtime-owned");
        runtime.put("C_q", "context, observer, perspective, environment, resource conditions — runtime-owned");
        runtime.put("e", "epoch / current time — runtime-owned");

        Map<String,Object> engine = new LinkedHashMap<>();
        engine.put("R_v", "reason closure — significance-engine-owned");
        engine.put("S_v", "projection <G, C_up, C_down, U, E_plus, E_minus, X, V> — significance-engine-owned");
        engine.put("Plan", "significance-engine-owned");
        engine.put("Schedule", "significance-engine-owned");

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("runtimeOwned", runtime);
        out.put("significanceEngineOwned", engine);
        out.put("rule", "UAO identity state supplies durable inputs to significance and never stores significance. "
                + "A significance result may produce new evidence, state, relationships or validity updates, which become "
                + "durable through ordinary manufacture and lifecycle paths; the result itself never does.");
        return out;
    }

    private static Object deepCopy(Object value) {
        return value == null ? new ArrayList<>() : Json.parse(Json.canonical(value));
    }
}
