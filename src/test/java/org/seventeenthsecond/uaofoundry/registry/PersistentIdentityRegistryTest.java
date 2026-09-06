package org.seventeenthsecond.uaofoundry.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolution;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 regression tests for the registry as a persistent identity addressing layer.
 *
 * <p>Two properties dominate: an exact lookup obeys the same evidence rules as a manufacture-time
 * decision (so the registry cannot become a back door to identity certainty), and a failed
 * operation leaves the registry byte-identical.
 */
class PersistentIdentityRegistryTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");

    @TempDir Path temp;

    @Test
    void anExactAddressReturnsTheCompleteIdentityRecord() {
        PipelineResult result = manufacture("record", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        FoundryRegistry registry = registryWith(result);

        Map<String,Object> record = registry.identityRecord(IdentityReference.uid(rootUid(result)));
        assertEquals("SAME", object(record.get("resolution")).get("decision"));

        Map<String,Object> identity = object(record.get("identity"));
        assertEquals(rootUid(result), identity.get("uid"));
        assertEquals("biology", identity.get("semanticType"));
        assertEquals(Map.of("wikidata", "Q830"), identity.get("externalIdentifiers"));
        assertEquals(SemanticVariants.SINGLE_VARIANT, identity.get("semanticVariantStatus"));
        assertEquals(1, array(identity.get("stateVersions")).size());
        assertEquals(1, array(identity.get("occurrences")).size());
        assertEquals(1, array(identity.get("decisionHistory")).size(),
                "the identity's history must be inspectable, not merely its current state");
    }

    @Test
    void aDurableExternalIdentifierIsAnAddress() {
        PipelineResult result = manufacture("ext-address", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        FoundryRegistry registry = registryWith(result);

        Map<String,Object> record = registry.identityRecord(IdentityReference.externalIdentifier("wikidata", "Q830"));
        assertEquals("SAME", object(record.get("resolution")).get("decision"));
        assertEquals(rootUid(result), object(record.get("identity")).get("uid"));
    }

    @Test
    void anAliasReturnsCandidatesRatherThanAnIdentity() {
        FoundryRegistry registry = registryWith(manufacture("alias-lookup", fixture -> {}));

        Map<String,Object> record = registry.identityRecord(IdentityReference.alias("cow"));
        Map<String,Object> resolution = object(record.get("resolution"));
        assertEquals("UNRESOLVED", resolution.get("decision"),
                "an exact-lookup surface must not become a back door to identity by name");
        assertEquals(List.of(IdentityResolution.ALIAS_MATCH_INSUFFICIENT), resolution.get("reasonCodes"));
        assertNull(record.get("identity"));
        assertEquals(1, array(record.get("candidates")).size(), "the near-miss must still be shown");
    }

    @Test
    void decisionHistoryAccumulatesAcrossOccurrencesWithoutRewriting() {
        PipelineResult first = manufacture("hist-1", fixture -> {});
        FoundryRegistry registry = registryWith(first);
        Map<String,Object> afterFirst = deepCopy(identityByUid(registry.index(), rootUid(first)));

        // A second manufacture that consulted the registry, so its decision differs from the first.
        PipelineResult second = manufactureAgainst("hist-2", registry, fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: second occurrence."));
        registry.register(second.packagePath());

        List<Object> history = array(identityByUid(registry.index(), rootUid(first)).get("decisionHistory"));
        assertEquals(2, history.size(), "each occurrence contributes its own determination");
        assertTrue(history.stream().map(PersistentIdentityRegistryTest::object)
                        .anyMatch(v -> List.of("REGISTRY_NOT_CONSULTED").equals(v.get("reasonCodes"))),
                "the earlier determination must survive verbatim");
        assertTrue(history.stream().map(PersistentIdentityRegistryTest::object)
                        .anyMatch(v -> List.of(IdentityResolution.EXACT_RESOLUTION_KEY_MATCH).equals(v.get("reasonCodes"))));

        // The first occurrence's own record is unchanged; only the aggregate grew.
        List<Object> firstHistory = array(afterFirst.get("decisionHistory"));
        assertEquals(1, firstHistory.size());
        assertTrue(history.containsAll(firstHistory), "history grows by accretion, never by revision");
    }

    @Test
    void anUnreconciledIdentityRefusesToResolveWhileItsNeighboursDoNot() {
        PipelineResult t0 = manufacture("var-t0", fixture -> {});
        PipelineResult t1 = manufacture("var-t1", fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: divergent."));
        PipelineResult unrelated = manufacture("var-other", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        });
        FoundryRegistry registry = registryWith(t0, t1, unrelated);

        Map<String,Object> ambiguous = registry.identityRecord(IdentityReference.uid(rootUid(t0)));
        Map<String,Object> resolution = object(ambiguous.get("resolution"));
        assertEquals("UNRESOLVED", resolution.get("decision"));
        assertEquals(List.of(IdentityResolution.SEMANTIC_VARIANTS_UNRECONCILED), resolution.get("reasonCodes"));
        assertEquals(1, array(ambiguous.get("candidates")).size(),
                "refusing to resolve must still expose the disputed identity for inspection");

        Map<String,Object> clean = registry.identityRecord(IdentityReference.uid(rootUid(unrelated)));
        assertEquals("SAME", object(clean.get("resolution")).get("decision"),
                "one disputed identity must not disable the rest of the registry");
    }

    @Test
    void theIndexRemainsOrderIndependentWithIdentityHistoryPresent() {
        PipelineResult cow = manufacture("order-cow", fixture -> {});
        PipelineResult granite = manufacture("order-granite", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        });

        FoundryRegistry left = new FoundryRegistry(temp.resolve("left"), SCHEMAS);
        FoundryRegistry right = new FoundryRegistry(temp.resolve("right"), SCHEMAS);
        left.register(cow.packagePath());
        left.register(granite.packagePath());
        right.register(granite.packagePath());
        right.register(cow.packagePath());

        assertEquals(Json.canonical(left.index()), Json.canonical(right.index()),
                "identity history must not make the index depend on admission order");
        assertTrue(left.verify().passed());
        assertTrue(right.verify().passed());
    }

    @Test
    void aRefusedAdmissionLeavesTheRegistryByteIdentical() {
        PipelineResult registered = manufacture("immutable-seed", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        Path root = temp.resolve("registry");
        FoundryRegistry registry = new FoundryRegistry(root, SCHEMAS);
        registry.register(registered.packagePath());

        String before = FileOps.treeHash(root);

        // Same address, contradicting durable external identity: admission must be refused.
        PipelineResult conflicting = manufacture("immutable-conflict", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q99999")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(conflicting.packagePath()));

        assertEquals(before, FileOps.treeHash(root), "a refused admission must not mutate the registry");
        assertTrue(registry.verify().passed(), "and must leave it verifiable");
    }


    // ---------------------------------------------------------------- ENRICH (ADR-0007)

    /** Adds one sourced claim about the root identity while restating every fixture claim verbatim. */
    private static void addRootClaim(Map<String,Object> fixture, String statement) {
        Map<String,Object> candidates = object(fixture.get("candidates"));
        Map<String,Object> claim = new java.util.LinkedHashMap<>();
        claim.put("candidateId", "clm-root-enriched"); claim.put("subjectIdentityRef", "cid-root");
        claim.put("statement", statement); claim.put("channels", List.of("foundry")); claim.put("sourceRefs", List.of("src-cow-bio"));
        array(candidates.get("claims")).add(claim);
        Map<String,Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("evidenceId", "ev-root-enriched"); evidence.put("sourceRef", "src-cow-bio"); evidence.put("supportsCandidateRef", "clm-root-enriched");
        evidence.put("extract", "Synthetic fixture evidence for the enriching assertion."); evidence.put("locatorWithinSource", "sentence-2");
        array(candidates.get("evidence")).add(evidence);
    }

    @Test
    void enrichmentMakesAStrictSupersetTheCurrentStateAndReuseFollowsIt() {
        PipelineResult t0 = manufacture("enr-t0", fixture -> {});
        PipelineResult t1 = manufacture("enr-t1", fixture -> addRootClaim(fixture, "Fixture assertion: enriched with a second sourced statement."));
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String before = object(array(identityByUid(registry.index(), uid).get("occurrences")).getFirst()).get("semanticVariantDigest").toString();

        FoundryRegistry.EnrichmentResult result = registry.enrich(t1.packagePath(), uid, List.of("LIFE_CHRONOLOGY"),
                "Second sourced statement added; prior assertions restated verbatim.", "operator", "2026-09-06T00:00:00Z");
        assertEquals(1, result.assertionsAdded());
        assertEquals(before, result.fromVariant());

        Map<String,Object> identity = identityByUid(registry.index(), uid);
        assertEquals(SemanticVariants.SINGLE_VARIANT, identity.get("semanticVariantStatus"), "a superseded variant is history, not an unreconciled sibling");
        assertEquals(result.toVariant(), identity.get("currentVariant"));
        assertEquals(1, array(identity.get("variantHistory")).size());
        assertEquals(IdentityOperation.ACTIVE, identity.get("lifecycleState"), "enrichment never changes lifecycle");
        assertEquals(2, array(identity.get("occurrences")).size(), "both packages remain inspectable occurrences");
        assertTrue(registry.verify().passed());
        assertEquals("SAME", object(registry.identityRecord(IdentityReference.uid(uid)).get("resolution")).get("decision"));

        // Reuse follows the current state: restating the enriched form is a re-observation ...
        org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer analyzer = new org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer(SCHEMAS);
        String contextHash = org.seventeenthsecond.uaofoundry.util.Hashes.canonicalJson(Map.of("test", "context"));
        PipelineResult restated = manufactureAgainst("enr-restate", registry, fixture -> addRootClaim(fixture, "Fixture assertion: enriched with a second sourced statement."));
        Map<String,Object> report = analyzer.analyze(registry.index(), registryRoot, restated.packagePath(), contextHash);
        assertEquals(1, array(report.get("reusedUaos")).stream().map(PersistentIdentityRegistryTest::object).filter(v -> uid.equals(v.get("uid"))).count(),
                "restating the enriched form must count as reuse of the current state");
        // ... while restating the superseded form is now divergence.
        PipelineResult stalePackage = manufactureAgainst("enr-stale", registry, fixture -> {});
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, stalePackage.packagePath(), contextHash));
        assertTrue(stale.getMessage().contains("SEMANTIC_VARIANT_DIVERGENCE"), stale.getMessage());
    }

    @Test
    void enrichmentIsRefusedWhenPriorAssertionsAreRewordedOrDropped() {
        PipelineResult t0 = manufacture("enr2-t0", fixture -> {});
        PipelineResult reworded = manufacture("enr2-reworded", fixture -> {
            claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: re-worded, not restated.");
            addRootClaim(fixture, "Fixture assertion: an additional statement.");
        });
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String indexBefore = Json.canonical(registry.index());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> registry.enrich(reworded.packagePath(), uid,
                List.of("LIFE_CHRONOLOGY"), "attempt", "operator", "2026-09-06T00:00:00Z"));
        assertTrue(refused.getMessage().contains("restate every prior assertion verbatim"), refused.getMessage());
        assertEquals(indexBefore, Json.canonical(registry.index()), "a refused enrichment leaves the registry byte-identical");
        assertEquals(1, array(identityByUid(registry.index(), uid).get("occurrences")).size(), "the non-enriching package was never admitted");

        IllegalArgumentException unchanged = assertThrows(IllegalArgumentException.class, () -> registry.enrich(t0.packagePath(), uid,
                List.of("LIFE_CHRONOLOGY"), "attempt", "operator", "2026-09-06T00:00:00Z"));
        assertTrue(unchanged.getMessage().contains("nothing to enrich"), unchanged.getMessage());
    }

    @Test
    void anEnrichmentForkOrAnUnsupportedJournalEntryFailsClosed() {
        PipelineResult t0 = manufacture("enr3-t0", fixture -> {});
        PipelineResult t1 = manufacture("enr3-t1", fixture -> addRootClaim(fixture, "Fixture assertion: branch one."));
        PipelineResult t2 = manufacture("enr3-t2", fixture -> addRootClaim(fixture, "Fixture assertion: branch two."));
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        registry.enrich(t1.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "branch one", "operator", "2026-09-06T00:00:00Z");

        // A second enrichment leaving the ORIGINAL variant would be a fork. enrich() works from the
        // current variant, so t2 (which restates t0, not t1) is refused as dropping t1's assertion.
        IllegalArgumentException fork = assertThrows(IllegalArgumentException.class, () -> registry.enrich(t2.packagePath(), uid,
                List.of("LIFE_CHRONOLOGY"), "branch two", "operator", "2026-09-06T00:00:00Z"));
        assertTrue(fork.getMessage().contains("restate every prior assertion verbatim"), fork.getMessage());

        // A journal entry the packages do not support is refused at admission and never lands.
        Map<String,Object> identity = identityByUid(registry.index(), uid);
        String current = identity.get("currentVariant").toString();
        IdentityOperation bogus = IdentityOperation.enrich(uid, current, "f".repeat(64), "pkg-0000000000000000",
                List.of("LIFE_CHRONOLOGY"), "unsupported", "operator", "2026-09-06T00:00:00Z");
        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class, () -> registry.applyIdentityOperation(bogus));
        assertTrue(unsupported.getMessage().contains("holds no occurrence"), unsupported.getMessage());
        assertTrue(registry.verify().passed());
        assertEquals(1, array(identity.get("variantHistory")).size());
    }

    // ---------------------------------------------------------------- helpers

    private PipelineResult manufacture(String suffix, Consumer<Map<String,Object>> mutation) {
        return manufactureAgainst(suffix, null, mutation);
    }

    private PipelineResult manufactureAgainst(String suffix, FoundryRegistry registry, Consumer<Map<String,Object>> mutation) {
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

    private static Map<String,Object> identity(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "identities", candidateId);
    }

    private static Map<String,Object> claim(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "claims", candidateId);
    }

    private static Map<String,Object> byCandidateId(Map<String,Object> fixture, String category, String candidateId) {
        return array(object(fixture.get("candidates")).get(category)).stream()
                .map(PersistentIdentityRegistryTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId"))).findFirst().orElseThrow();
    }

    private static String rootUid(PipelineResult result) {
        return object(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("rootUaoId").toString();
    }

    private static Map<String,Object> identityByUid(Map<String,Object> index, String uid) {
        return array(index.get("identities")).stream().map(PersistentIdentityRegistryTest::object)
                .filter(v -> uid.equals(v.get("uid"))).findFirst().orElseThrow();
    }

    private static Map<String,Object> deepCopy(Map<String,Object> value) { return object(Json.parse(Json.canonical(value))); }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
