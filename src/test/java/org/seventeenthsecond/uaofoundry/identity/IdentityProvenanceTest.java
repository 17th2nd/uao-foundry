package org.seventeenthsecond.uaofoundry.identity;

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
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 regression tests for identity provenance.
 *
 * <p>The property under test throughout is that the package can answer <em>why do we think these
 * references are the same object?</em> — with evidence, not merely a verdict — and that no earlier
 * determination is ever rewritten to make a later one look tidy.
 */
class IdentityProvenanceTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");

    @TempDir Path temp;

    @Test
    void withoutARegistryTheFoundryRecordsThatItDidNotLookRatherThanThatItFoundNothing() {
        PipelineResult result = manufacture("no-registry", null, fixture -> {});
        for (Map<String,Object> decision : decisions(result)) {
            assertEquals("UNRESOLVED", decision.get("decision"));
            assertEquals(List.of("REGISTRY_NOT_CONSULTED"), decision.get("reasonCodes"),
                    "not having looked must stay distinguishable from having looked and found nothing");
            assertNull(decision.get("uid"));
        }
    }

    @Test
    void consultingARegistryThatHoldsTheIdentityRecordsAnEvidencedReuseDecision() {
        FoundryRegistry registry = registryWith(manufacture("seed", null, fixture -> {}));

        PipelineResult second = manufacture("reuse", registry, fixture -> {});
        Map<String,Object> root = decisionFor(second, rootUid(second));
        assertEquals("SAME", root.get("decision"));
        assertEquals(rootUid(second), root.get("uid"));
        assertEquals(List.of(IdentityResolution.EXACT_RESOLUTION_KEY_MATCH), root.get("reasonCodes"));

        // The decision is evidence-bearing, not just a verdict.
        assertFalse(array(root.get("candidateRefs")).isEmpty(), "the decision must name what it was made over");
        assertFalse(array(root.get("sourceRefs")).isEmpty(), "the decision must retain its evidence custody");
    }

    @Test
    void consultingARegistryWithoutTheIdentityRecordsAnEvidencedAbsence() {
        FoundryRegistry registry = registryWith(manufacture("other", null, fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        }));

        PipelineResult result = manufacture("absent", registry, fixture -> {});
        Map<String,Object> root = decisionFor(result, rootUid(result));
        assertEquals("UNRESOLVED", root.get("decision"));
        assertEquals(List.of(IdentityResolution.NO_REGISTERED_MATCH), root.get("reasonCodes"));
        assertNull(root.get("uid"), "an unresolved decision must not bind a uid");
    }

    @Test
    void aMergeCandidateIsSurfacedInThePackageAndNeverActedOn() {
        FoundryRegistry registry = registryWith(manufacture("merge-seed", null, fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830"))));
        String registeredUid = rootUid(manufacture("merge-seed-again", null, fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830"))));

        // A different address carrying the same durable evidence: the same object under two names.
        PipelineResult result = manufacture("merge-candidate", registry, fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:biology:bos-taurus");
            root.put("externalIdentifiers", Map.of("wikidata", "Q830"));
        });

        Map<String,Object> root = decisionFor(result, rootUid(result));
        assertEquals("UNRESOLVED", root.get("decision"),
                "merging is a governed append-preserving operation, never a manufacture-time side effect");
        assertEquals(List.of(IdentityResolution.EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH), root.get("reasonCodes"));
        assertEquals(List.of(registeredUid), root.get("candidateUids"),
                "the package must name the identity a future merge would have to consider");
        assertNotEquals(registeredUid, rootUid(result), "no implicit merge may have occurred");
    }

    @Test
    void contradictingRegisteredExternalIdentityStopsManufacture() {
        FoundryRegistry registry = registryWith(manufacture("contra-seed", null, fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830"))));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                manufacture("contra", registry, fixture ->
                        identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q99999"))));
        assertTrue(failure.getMessage().contains("EXTERNAL_IDENTIFIER_CONTRADICTION"), failure.getMessage());
    }

    @Test
    void earlierDeterminationsAreNeverRewrittenByLaterOnes() {
        PipelineResult first = manufacture("history-1", null, fixture -> {});
        String firstDecisions = Json.canonical(FileOps.readJson(
                first.packagePath().resolve("identity-resolution.json")));

        FoundryRegistry registry = registryWith(first);
        PipelineResult second = manufacture("history-2", registry, fixture -> {});

        // The second manufacture reaches a different, better-evidenced conclusion...
        assertEquals("SAME", decisionFor(second, rootUid(second)).get("decision"));
        // ...and the first package's record of "I did not look" is untouched.
        assertEquals(firstDecisions, Json.canonical(FileOps.readJson(
                first.packagePath().resolve("identity-resolution.json"))),
                "history is preserved by accretion; an immutable package is never revised");
        assertTrue(new PackageVerifier(SCHEMAS).verify(first.packagePath()).passed());
    }

    @Test
    void everyResolvedIdentityCarriesExactlyOneDecision() {
        PipelineResult result = manufacture("coverage", null, fixture -> {});
        List<Object> resolved = array(object(FileOps.readJson(
                result.packagePath().resolve("identity-resolution.json"))).get("resolvedIdentities"));
        assertEquals(resolved.size(), decisions(result).size());
        assertTrue(resolved.size() >= 2, "the fixture must exercise more than the root identity");
    }

    // ---------------------------------------------------------------- forgery

    @Test
    void aDecisionClaimingReuseOfSomeOtherIdentityFailsVerification() {
        PipelineResult result = manufacture("forge-reuse", null, fixture -> {});
        mutateFirstDecision(result, decision -> {
            decision.put("decision", "SAME");
            decision.put("reasonCodes", List.of(IdentityResolution.EXACT_RESOLUTION_KEY_MATCH));
            decision.put("uid", "uao-000000000000");
        });
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("different registered identity")),
                verification.errors().toString());
    }

    @Test
    void aDecisionDetachedFromItsEvidenceFailsVerification() {
        PipelineResult result = manufacture("forge-evidence", null, fixture -> {});
        mutateFirstDecision(result, decision -> decision.put("sourceRefs", List.of()));
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("sourceRefs do not reconstruct")),
                verification.errors().toString());
    }

    @Test
    void removingTheDecisionRecordFailsVerification() {
        PipelineResult result = manufacture("forge-remove", null, fixture -> {});
        Path path = result.packagePath().resolve("identity-resolution.json");
        Map<String,Object> resolution = object(FileOps.readJson(path));
        resolution.remove("identityDecisions");
        FileOps.writeJson(path, resolution);

        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed(), "a package must not be able to drop its identity provenance");
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("identityDecisions")),
                verification.errors().toString());
    }

    // ---------------------------------------------------------------- helpers

    private PipelineResult manufacture(String suffix, FoundryRegistry registry, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(COW)));
        mutation.accept(fixture);
        Path fixturePath = temp.resolve("fixture-" + suffix + ".json");
        FileOps.writeJson(fixturePath, fixture);

        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        FixtureProvider provider = new FixtureProvider(fixturePath, SCHEMAS);
        Path work = temp.resolve("work-" + suffix);
        Path dist = temp.resolve("dist-" + suffix);
        FoundryPipeline pipeline = registry == null
                ? new FoundryPipeline(SCHEMAS, work, dist, "test-sha")
                : new FoundryPipeline(SCHEMAS, work, dist, "test-sha", registryRoot, registry.index());
        return pipeline.manufacture(request, provider, false);
    }

    private Path registryRoot;

    private FoundryRegistry registryWith(PipelineResult... packages) {
        registryRoot = temp.resolve("registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        for (PipelineResult result : packages) registry.register(result.packagePath());
        return registry;
    }

    private static List<Map<String,Object>> decisions(PipelineResult result) {
        return array(object(FileOps.readJson(result.packagePath().resolve("identity-resolution.json")))
                .get("identityDecisions")).stream().map(IdentityProvenanceTest::object).toList();
    }

    private static Map<String,Object> decisionFor(PipelineResult result, String uid) {
        return decisions(result).stream().filter(v -> uid.equals(v.get("uaoId"))).findFirst().orElseThrow();
    }

    private static void mutateFirstDecision(PipelineResult result, Consumer<Map<String,Object>> mutation) {
        Path path = result.packagePath().resolve("identity-resolution.json");
        Map<String,Object> resolution = object(FileOps.readJson(path));
        mutation.accept(object(array(resolution.get("identityDecisions")).getFirst()));
        FileOps.writeJson(path, resolution);
    }

    private static Map<String,Object> identity(Map<String,Object> fixture, String candidateId) {
        return array(object(fixture.get("candidates")).get("identities")).stream()
                .map(IdentityProvenanceTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId"))).findFirst().orElseThrow();
    }

    private static String rootUid(PipelineResult result) {
        return object(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("rootUaoId").toString();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
