package org.seventeenthsecond.uaofoundry.negativespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7 regression tests for negative-space evaluation.
 *
 * <p>Every test here defends one of three distinctions: {@code MISSING ≠ FALSE},
 * {@code UNKNOWN ≠ ABSENT}, {@code EXPECTED ≠ OBSERVED}. The failure mode being guarded against is
 * a component that confidently reports absence it has not earned.
 */
class NegativeSpaceTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");

    @TempDir Path temp;

    @Test
    void absenceOverCertifiedRelationshipsIsVacuousWhileAsa29IsOpen() {
        FoundryRegistry registry = registryWith(manufacture("vacuous", fixture -> {}));
        Map<String,Object> result = new NegativeSpaceEvaluator(registry.index())
                .evaluate(expectation(registry), NegativeSpaceEvaluator.Universe.CERTIFIED);

        assertEquals(NegativeSpaceEvaluator.Evaluation.SCOPE_VACUOUS.name(), result.get("evaluation"),
                "the certified set is empty by authority, so every absence in it would look identical");
        assertEquals(List.of(NegativeSpaceEvaluator.URO_TYPE_AUTHORITY_UNAVAILABLE), result.get("reasonCodes"));
        assertNotEquals(NegativeSpaceEvaluator.Evaluation.ABSENT_WITHIN_SCOPE.name(), result.get("evaluation"),
                "reporting absence here would be technically true, trivially derived, and misleading");
        assertTrue(String.valueOf(result.get("explanation")).contains("ASA#29"));
    }

    @Test
    void anUncertainEndpointYieldsUnknownRatherThanAbsent() {
        FoundryRegistry registry = registryWith(manufacture("uncertain", fixture -> {}));
        ExpectedRelationship expectation = ExpectedRelationship.create(
                rootUid("uncertain"), "asa.core/verified_by@1", "subject",
                "uao-000000000000", "object", "An identity that was never registered.");

        Map<String,Object> result = new NegativeSpaceEvaluator(registry.index())
                .evaluate(expectation, NegativeSpaceEvaluator.Universe.CANDIDATE);

        assertEquals(NegativeSpaceEvaluator.Evaluation.UNKNOWN.name(), result.get("evaluation"),
                "an absence between things you cannot pin down is not evidence about the world");
        assertEquals(List.of(NegativeSpaceEvaluator.IDENTITY_NOT_CERTAIN), result.get("reasonCodes"));
    }

    @Test
    void anIdentityWhoseMeaningIsInDisputeCannotAnchorAnAbsenceClaim() {
        PipelineResult t0 = manufacture("disp-t0", fixture -> {});
        PipelineResult t1 = manufacture("disp-t1", fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: divergent."));
        PipelineResult other = manufacture("disp-other", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        });
        FoundryRegistry registry = registryWith(t0, t1, other);

        Map<String,Object> result = new NegativeSpaceEvaluator(registry.index()).evaluate(
                ExpectedRelationship.create(rootUid("disp-t0"), "asa.core/verified_by@1", "subject",
                        rootUid("disp-other"), "object", "Endpoint has unreconciled variants."),
                NegativeSpaceEvaluator.Universe.CANDIDATE);

        assertEquals(NegativeSpaceEvaluator.Evaluation.UNKNOWN.name(), result.get("evaluation"));
        assertEquals(List.of(NegativeSpaceEvaluator.IDENTITY_NOT_CERTAIN), result.get("reasonCodes"));
    }

    @Test
    void anEmptyRegistryYieldsUnknownBecauseNothingWasThereToObserve() {
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("empty"), SCHEMAS);
        Map<String,Object> result = new NegativeSpaceEvaluator(registry.index()).evaluate(
                ExpectedRelationship.create("uao-000000000000", "asa.core/verified_by@1", "subject",
                        "uao-111111111111", "object", "Nothing has been observed at all."),
                NegativeSpaceEvaluator.Universe.CANDIDATE);

        assertEquals(NegativeSpaceEvaluator.Evaluation.UNKNOWN.name(), result.get("evaluation"),
                "not having looked must never be reported as having looked and found nothing");
    }

    @Test
    void aBoundedAbsenceStatesExactlyWhatWasSearched() {
        FoundryRegistry registry = registryWith(manufacture("scope-a", fixture -> {}),
                manufacture("scope-b", fixture -> {
                    Map<String,Object> root = identity(fixture, "cid-root");
                    root.put("resolutionKey", "fixture:material:granite");
                    root.put("label", "granite");
                    root.put("aliases", List.of("granite rock"));
                }));

        Map<String,Object> result = new NegativeSpaceEvaluator(registry.index()).evaluate(
                ExpectedRelationship.create(rootUid("scope-a"), "asa.core/verified_by@1", "subject",
                        rootUid("scope-b"), "object", "No such relationship was ever asserted."),
                NegativeSpaceEvaluator.Universe.CANDIDATE);

        assertEquals(NegativeSpaceEvaluator.Evaluation.ABSENT_WITHIN_SCOPE.name(), result.get("evaluation"));
        assertEquals(List.of(NegativeSpaceEvaluator.NOT_FOUND_IN_BOUNDED_SCOPE), result.get("reasonCodes"));

        Map<String,Object> scope = object(result.get("observationScope"));
        assertEquals(Boolean.TRUE, scope.get("bounded"));
        assertEquals(2, array(scope.get("packageIds")).size(),
                "an absence reported without its scope cannot be told from no search at all");
        assertTrue(String.valueOf(scope.get("registryIndexHash")).matches("[a-f0-9]{64}"));
        assertTrue(String.valueOf(scope.get("caveat")).contains("not evidence of its non-existence"));
    }

    @Test
    void anObservedCandidateIsEvidenceAndExplicitlyNotCertification() {
        // Both endpoints of the fixture's relationship resolve, so it is observable in the
        // candidate universe -- but observing a candidate says someone asserted the relationship,
        // not that ASA governs it.
        Map<String,Object> index = candidateBearingIndex();
        Map<String,Object> result = new NegativeSpaceEvaluator(index).evaluate(
                ExpectedRelationship.create(uidOf(index, "fixture:biology:adult-female-cattle"),
                        "asa.core/contains@1", "container",
                        uidOf(index, "fixture:biology:domestic-cattle"), "member",
                        "The fixture asserts this containment."),
                NegativeSpaceEvaluator.Universe.CANDIDATE);

        assertEquals(NegativeSpaceEvaluator.Evaluation.OBSERVED.name(), result.get("evaluation"));
        assertEquals(List.of(NegativeSpaceEvaluator.CANDIDATE_OBSERVED), result.get("reasonCodes"));
        assertEquals(Boolean.FALSE, result.get("certifying"),
                "an observed candidate must never read as a certified relationship");
    }

    @Test
    void everyResultIsMarkedNonCertifyingAndResearchLevel() {
        FoundryRegistry registry = registryWith(manufacture("labels", fixture -> {}));
        for (NegativeSpaceEvaluator.Universe universe : NegativeSpaceEvaluator.Universe.values()) {
            Map<String,Object> result = new NegativeSpaceEvaluator(registry.index())
                    .evaluate(expectation(registry), universe);
            assertEquals(Boolean.FALSE, result.get("certifying"));
            assertEquals("RESEARCH_LEVEL_NOT_AUTHORITATIVE", result.get("status"));
            assertTrue(String.valueOf(result.get("rule")).contains("never manufactures"));
        }
    }

    @Test
    void evaluatingAnExpectationManufacturesNothing() {
        FoundryRegistry registry = registryWith(manufacture("inert", fixture -> {}));
        String before = FileOps.treeHash(registryRoot);

        for (NegativeSpaceEvaluator.Universe universe : NegativeSpaceEvaluator.Universe.values()) {
            new NegativeSpaceEvaluator(registry.index()).evaluate(expectation(registry), universe);
        }

        assertEquals(before, FileOps.treeHash(registryRoot),
                "expecting a relationship must never bring it into being");
        assertTrue(registry.verify().passed());
    }

    @Test
    void anExpectationMustRelateTwoDistinctIdentitiesAndStateWhyItIsExpected() {
        assertThrows(IllegalArgumentException.class, () -> ExpectedRelationship.create(
                "uao-000000000000", "asa.core/verified_by@1", "subject",
                "uao-000000000000", "object", "Self-relation."));
        assertThrows(IllegalArgumentException.class, () -> ExpectedRelationship.create(
                "uao-000000000000", "asa.core/verified_by@1", "subject",
                "uao-111111111111", "object", "  "));
    }

    // ---------------------------------------------------------------- helpers

    private final Map<String,PipelineResult> results = new java.util.LinkedHashMap<>();
    private Path registryRoot;

    private ExpectedRelationship expectation(FoundryRegistry registry) {
        List<Object> identities = array(registry.index().get("identities"));
        String subject = String.valueOf(object(identities.getFirst()).get("uid"));
        String target = identities.size() > 1 ? String.valueOf(object(identities.get(1)).get("uid")) : "uao-111111111111";
        return ExpectedRelationship.create(subject, "asa.core/verified_by@1", "subject", target, "object",
                "Programme-level expectation used to exercise absence evaluation.");
    }

    /** A registry index carrying identity-bound relationship candidates, built without admission. */
    private Map<String,Object> candidateBearingIndex() {
        PipelineResult withRelationship = manufactureFrom(
                Path.of("src/test/resources/fixtures/relationship-bearing-cow.json"), "cand", fixture -> {
                    Map<String,Object> relationship = object(array(object(fixture.get("candidates")).get("relationships")).getFirst());
                    object(array(relationship.get("participants")).get(1)).put("candidateIdentityRef", "cid-bovine-context");
                });
        // EVIDENCE_INCOMPLETE packages are not admissible, so assemble the index shape directly
        // from the package's own retained bindings rather than pretending admission succeeded.
        List<Object> identities = new java.util.ArrayList<>();
        Map<String,Object> unresolved = object(array(FileOps.readJson(
                withRelationship.packagePath().resolve("unresolved-items.json"))).getFirst());
        for (Object raw : array(FileOps.readJson(withRelationship.packagePath().resolve("canonical-identities.json")))) {
            Map<String,Object> uao = object(raw);
            String uid = String.valueOf(uao.get("uid"));
            List<Object> bindings = new java.util.ArrayList<>();
            for (Object rawParticipant : array(unresolved.get("participants"))) {
                Map<String,Object> participant = object(rawParticipant);
                if (uid.equals(participant.get("uaoId"))) {
                    bindings.add(Map.of("packageId", "pkg-test", "relationshipCandidateId", unresolved.get("candidateId"),
                            "typeVersion", unresolved.get("typeVersion"), "role", participant.get("role"),
                            "identityBindingStatus", unresolved.get("identityBindingStatus"),
                            "canonicalUroPublished", Boolean.FALSE, "blockedBy", "URO_TYPE_AUTHORITY_UNAVAILABLE"));
                }
            }
            Map<String,Object> identity = new java.util.LinkedHashMap<>();
            identity.put("uid", uid);
            identity.put("resolutionKey", object(object(uao.get("internal_state")).get("foundry_identity")).get("resolution_key"));
            identity.put("canonicalLabels", List.of());
            identity.put("aliases", List.of());
            identity.put("externalIdentifiers", Map.of());
            identity.put("semanticVariantStatus", "SINGLE_VARIANT");
            identity.put("lifecycleState", "ACTIVE");
            identity.put("relationshipBindings", bindings);
            identities.add(identity);
        }
        Map<String,Object> index = new java.util.LinkedHashMap<>();
        index.put("registryVersion", "0.1.0");
        index.put("packages", List.of(Map.of("packageId", "pkg-test")));
        index.put("identities", identities);
        return index;
    }

    private static String uidOf(Map<String,Object> index, String resolutionKey) {
        return array(index.get("identities")).stream().map(NegativeSpaceTest::object)
                .filter(v -> resolutionKey.equals(v.get("resolutionKey")))
                .map(v -> String.valueOf(v.get("uid"))).findFirst().orElseThrow();
    }

    private PipelineResult manufacture(String suffix, Consumer<Map<String,Object>> mutation) {
        return manufactureFrom(COW, suffix, mutation);
    }

    private PipelineResult manufactureFrom(Path source, String suffix, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(source)));
        mutation.accept(fixture);
        Path fixturePath = temp.resolve("fixture-" + suffix + ".json");
        FileOps.writeJson(fixturePath, fixture);
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        PipelineResult result = new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix),
                temp.resolve("dist-" + suffix), "test-sha")
                .manufacture(request, new FixtureProvider(fixturePath, SCHEMAS), false);
        results.put(suffix, result);
        return result;
    }

    private FoundryRegistry registryWith(PipelineResult... packages) {
        registryRoot = temp.resolve("registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        for (PipelineResult result : packages) registry.register(result.packagePath());
        return registry;
    }

    private String rootUid(String suffix) {
        return object(FileOps.readJson(results.get(suffix).packagePath().resolve("manifest.json")))
                .get("rootUaoId").toString();
    }

    private static Map<String,Object> identity(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "identities", candidateId);
    }

    private static Map<String,Object> claim(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "claims", candidateId);
    }

    private static Map<String,Object> byCandidateId(Map<String,Object> fixture, String category, String candidateId) {
        return array(object(fixture.get("candidates")).get(category)).stream().map(NegativeSpaceTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
