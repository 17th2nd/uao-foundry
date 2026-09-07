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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Adds one sourced claim about the context identity (cid-bovine-context) while restating every fixture claim verbatim. */
    private static void addContextClaim(Map<String,Object> fixture, String statement) {
        Map<String,Object> candidates = object(fixture.get("candidates"));
        Map<String,Object> claim = new java.util.LinkedHashMap<>();
        claim.put("candidateId", "clm-context-enriched"); claim.put("subjectIdentityRef", "cid-bovine-context");
        claim.put("statement", statement); claim.put("channels", List.of("foundry")); claim.put("sourceRefs", List.of("src-cow-bio"));
        array(candidates.get("claims")).add(claim);
        Map<String,Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("evidenceId", "ev-context-enriched"); evidence.put("sourceRef", "src-cow-bio"); evidence.put("supportsCandidateRef", "clm-context-enriched");
        evidence.put("extract", "Synthetic fixture evidence for the context enrichment."); evidence.put("locatorWithinSource", "sentence-3");
        array(candidates.get("evidence")).add(evidence);
    }
    private static void rewordContext(Map<String,Object> fixture) {
        for (Object raw : array(object(fixture.get("candidates")).get("claims"))) {
            Map<String,Object> claim = object(raw);
            if ("cid-bovine-context".equals(claim.get("subjectIdentityRef"))) claim.put("statement", claim.get("statement") + " (re-worded)");
        }
    }
    private String variantIn(FoundryRegistry registry, String uid, String packageId) {
        return array(identityByUid(registry.index(), uid).get("occurrences")).stream().map(PersistentIdentityRegistryTest::object)
                .filter(o -> packageId.equals(o.get("packageId"))).map(o -> o.get("semanticVariantDigest").toString()).findFirst().orElseThrow();
    }
    private String contextUid(PipelineResult result) {
        return array(FileOps.readJson(result.packagePath().resolve("canonical-identities.json"))).stream().map(PersistentIdentityRegistryTest::object)
                .map(u -> String.valueOf(u.get("uid"))).filter(u -> !u.equals(rootUid(result))).findFirst().orElseThrow();
    }

    @Test
    void aLaterEnrichmentOfTheOtherIdentityIsAccepted() {
        // Codex pass-I F-I2: enrich A with a package carrying B verbatim, then enrich B with a package carrying the
        // enriched A verbatim. Both are legitimate successions; the second must not be refused by the first.
        PipelineResult t0 = manufacture("enr9-t0", fixture -> {});
        PipelineResult a1b0 = manufacture("enr9-a1b0", fixture -> addRootClaim(fixture, "Fixture assertion: one more sourced statement."));
        PipelineResult a1b1 = manufacture("enr9-a1b1", fixture -> { addRootClaim(fixture, "Fixture assertion: one more sourced statement."); addContextClaim(fixture, "Fixture assertion: the context gained a statement."); });
        FoundryRegistry registry = registryWith(t0);
        String a = rootUid(t0), b = contextUid(t0);
        assertEquals(1, registry.enrich(a1b0.packagePath(), a, List.of("LIFE_CHRONOLOGY"), "A first", "operator", "2026-09-07T00:00:00Z").assertionsAdded());
        assertEquals(1, registry.enrich(a1b1.packagePath(), b, List.of("LIFE_CHRONOLOGY"), "B second, A restated in its enriched state", "operator", "2026-09-07T00:00:01Z").assertionsAdded());
        assertTrue(registry.verify().passed());
        for (Object raw : array(registry.index().get("identities"))) assertEquals(SemanticVariants.SINGLE_VARIANT, object(raw).get("semanticVariantStatus"));
        assertEquals(3, array(identityByUid(registry.index(), a).get("occurrences")).size());
    }

    @Test
    void theWholePackageRuleIsAttributableAndOrderIndependent() {
        // Codex pass-I F-I1 probe, with the invariant stated precisely: an ENRICH package introduces no new variant of
        // another identity. Here A0/B0, A1/B0, A0/B1 and an unlinked A0/B2 are all plainly registered, then A0→A1 and
        // B0→B1 are recorded through the journal. Both enrichments are valid: their packages carry the other identity
        // in a state it has elsewhere. B stays unreconciled, but that is the plain admission of A0/B2's doing, which
        // the registry's own law permits -- not the enrichments'. A package that would introduce B2 as part of an
        // ENRICH is refused (theJournalPathCannotLeaveASecondIdentityUnreconciledEither).
        PipelineResult t0 = manufacture("enr10-a0b0", fixture -> {});
        PipelineResult a1b0 = manufacture("enr10-a1b0", fixture -> addRootClaim(fixture, "Fixture assertion: one more sourced statement."));
        PipelineResult a0b1 = manufacture("enr10-a0b1", fixture -> addContextClaim(fixture, "Fixture assertion: the context gained a statement."));
        PipelineResult a0b2 = manufacture("enr10-a0b2", fixture -> rewordContext(fixture));
        FoundryRegistry registry = registryWith(t0);
        String a = rootUid(t0), b = contextUid(t0);
        String a0 = variantIn(registry, a, registry.index().get("packages") == null ? "" : object(array(registry.index().get("packages")).getFirst()).get("packageId").toString());
        FoundryRegistry.RegistrationResult pA1 = registry.register(a1b0.packagePath());
        FoundryRegistry.RegistrationResult pB1 = registry.register(a0b1.packagePath());
        registry.register(a0b2.packagePath());
        String b0 = variantIn(registry, b, pA1.packageId()), a1 = variantIn(registry, a, pA1.packageId()), b1 = variantIn(registry, b, pB1.packageId());
        registry.applyIdentityOperation(IdentityOperation.enrich(b, b0, b1, pB1.packageId(), List.of("LIFE_CHRONOLOGY"), "B", "operator", "2026-09-07T00:00:00Z"));
        registry.applyIdentityOperation(IdentityOperation.enrich(a, a0, a1, pA1.packageId(), List.of("LIFE_CHRONOLOGY"), "A", "operator", "2026-09-07T00:00:00Z"));
        assertTrue(registry.verify().passed());
        assertEquals(SemanticVariants.SINGLE_VARIANT, identityByUid(registry.index(), a).get("semanticVariantStatus"));
        assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS, identityByUid(registry.index(), b).get("semanticVariantStatus"), "B's unlinked sibling comes from the plain admission of A0/B2");
        assertEquals(2, array(registry.index().get("identityOperations")).size());
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
    void anEnrichmentThatRenamesOrForgetsTheIdentityIsRefusedByRegistryAndAnalyzer() {
        // Codex pass-F F-F1: the enrichment law covers identity continuity, not only the assertion superset.
        PipelineResult t0 = manufacture("enr6-t0", fixture -> identity(fixture, "cid-root").put("aliases", List.of("cow", "heifer")));
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String indexBefore = Json.canonical(registry.index());
        org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer analyzer = new org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer(SCHEMAS);
        String contextHash = org.seventeenthsecond.uaofoundry.util.Hashes.canonicalJson(Map.of("test", "context"));

        PipelineResult lostAlias = manufacture("enr6-alias", fixture -> {
            identity(fixture, "cid-root").put("aliases", List.of("cow"));
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
        });
        IllegalArgumentException a = assertThrows(IllegalArgumentException.class, () -> registry.enrich(lostAlias.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "drops heifer", "operator", "2026-09-07T00:00:00Z"));
        assertTrue(a.getMessage().contains("drops 1 alias"), a.getMessage());
        IllegalArgumentException a2 = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, lostAlias.packagePath(), contextHash, Set.of(uid)));
        assertTrue(a2.getMessage().contains("ENRICHMENT_IDENTITY_REGRESSION"), a2.getMessage());

        PipelineResult renamed = manufacture("enr6-label", fixture -> {
            identity(fixture, "cid-root").put("label", "female bovine");
            identity(fixture, "cid-root").put("aliases", List.of("cow", "heifer", "adult female cattle"));
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
        });
        IllegalArgumentException b = assertThrows(IllegalArgumentException.class, () -> registry.enrich(renamed.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "renames", "operator", "2026-09-07T00:00:00Z"));
        assertTrue(b.getMessage().contains("renames the identity"), b.getMessage());
        assertEquals(indexBefore, Json.canonical(registry.index()), "refused enrichments leave the registry byte-identical");

        // Names may only grow: an added alias with an added assertion is an enrichment.
        PipelineResult grown = manufacture("enr6-grown", fixture -> {
            identity(fixture, "cid-root").put("aliases", List.of("cow", "heifer", "bovine female"));
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
        });
        assertEquals(1, registry.enrich(grown.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "grown", "operator", "2026-09-07T00:00:00Z").assertionsAdded());
        assertTrue(registry.verify().passed());
    }

    @Test
    void anEnrichmentPackageMayNotCarryADivergentVariantOfAnotherRegisteredIdentity() {
        // Codex pass-G F-G1: the package is admitted whole, so a valid enrichment of the root plus a re-worded
        // occurrence of the (registered) context identity must be refused before anything is written.
        PipelineResult t0 = manufacture("enr7-t0", fixture -> {});
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String before = FileOps.treeHash(registryRoot);
        PipelineResult mixed = manufacture("enr7-mixed", fixture -> {
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
            for (Object raw : array(object(fixture.get("candidates")).get("claims"))) {
                Map<String,Object> claim = object(raw);
                if ("cid-bovine-context".equals(claim.get("subjectIdentityRef"))) claim.put("statement", claim.get("statement") + " (re-worded)");
            }
        });
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> registry.enrich(mixed.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "mixed", "operator", "2026-09-07T00:00:00Z"));
        assertTrue(refused.getMessage().contains("divergent variant of"), refused.getMessage());
        assertEquals(before, FileOps.treeHash(registryRoot), "refused before anything was written");
        for (Object raw : array(registry.index().get("identities"))) assertEquals(SemanticVariants.SINGLE_VARIANT, object(raw).get("semanticVariantStatus"));

        // The analyzer holds the same one-target invariant, whatever a caller passes.
        org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer analyzer = new org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer(SCHEMAS);
        IllegalArgumentException two = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, mixed.packagePath(),
                org.seventeenthsecond.uaofoundry.util.Hashes.canonicalJson(Map.of("test", "context")), Set.of(uid, "uao-000000000000")));
        assertTrue(two.getMessage().contains("ENRICHMENT_ONE_TARGET"), two.getMessage());

        // With the context identity restated verbatim, the same enrichment is admitted.
        PipelineResult clean = manufacture("enr7-clean", fixture -> addRootClaim(fixture, "Fixture assertion: one more sourced statement."));
        assertEquals(1, registry.enrich(clean.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "clean", "operator", "2026-09-07T00:00:00Z").assertionsAdded());
        assertTrue(registry.verify().passed());
        for (Object raw : array(registry.index().get("identities"))) assertEquals(SemanticVariants.SINGLE_VARIANT, object(raw).get("semanticVariantStatus"));
    }

    @Test
    void theJournalPathCannotLeaveASecondIdentityUnreconciledEither() throws Exception {
        // Codex pass-H F-H1: plain register() followed by applyIdentityOperation(ENRICH) bypassed the convenience
        // path's preflight. The whole-package rule is now re-derived on every index build, so the journal entry is
        // refused and removed, whichever path recorded it.
        PipelineResult t0 = manufacture("enr8-t0", fixture -> {});
        PipelineResult mixed = manufacture("enr8-mixed", fixture -> {
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
            for (Object raw : array(object(fixture.get("candidates")).get("claims"))) {
                Map<String,Object> claim = object(raw);
                if ("cid-bovine-context".equals(claim.get("subjectIdentityRef"))) claim.put("statement", claim.get("statement") + " (re-worded)");
            }
        });
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String v0 = object(array(identityByUid(registry.index(), uid).get("occurrences")).getFirst()).get("semanticVariantDigest").toString();
        FoundryRegistry.RegistrationResult admitted = registry.register(mixed.packagePath());   // legal on its own: both identities become unreconciled
        String v1 = array(identityByUid(registry.index(), uid).get("occurrences")).stream().map(PersistentIdentityRegistryTest::object)
                .filter(o -> admitted.packageId().equals(o.get("packageId"))).map(o -> o.get("semanticVariantDigest").toString()).findFirst().orElseThrow();
        String before = FileOps.treeHash(registryRoot);
        IdentityOperation enrich = IdentityOperation.enrich(uid, v0, v1, admitted.packageId(), List.of("LIFE_CHRONOLOGY"), "journal path", "operator", "2026-09-07T00:00:00Z");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> registry.applyIdentityOperation(enrich));
        assertTrue(refused.getMessage().contains("introduces a new variant of"), refused.getMessage());
        assertEquals(before, FileOps.treeHash(registryRoot), "the refused journal entry is removed again");
        assertTrue(registry.verify().passed());
        for (Object raw : array(registry.index().get("identities"))) {
            assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS, object(raw).get("semanticVariantStatus"), "no half-applied state: both stay as the plain admission left them");
        }

        // The same journal path with the other identity restated verbatim is accepted, on a fresh registry.
        FoundryRegistry clean = new FoundryRegistry(temp.resolve("registry-enr8-clean"), SCHEMAS);
        clean.register(t0.packagePath());
        PipelineResult grown = manufacture("enr8-clean", fixture -> addRootClaim(fixture, "Fixture assertion: one more sourced statement."));
        FoundryRegistry.RegistrationResult ok = clean.register(grown.packagePath());
        String v1c = array(identityByUid(clean.index(), uid).get("occurrences")).stream().map(PersistentIdentityRegistryTest::object)
                .filter(o -> ok.packageId().equals(o.get("packageId"))).map(o -> o.get("semanticVariantDigest").toString()).findFirst().orElseThrow();
        clean.applyIdentityOperation(IdentityOperation.enrich(uid, v0, v1c, ok.packageId(), List.of("LIFE_CHRONOLOGY"), "journal path, clean", "operator", "2026-09-07T00:00:00Z"));
        assertTrue(clean.verify().passed());
        for (Object raw : array(clean.index().get("identities"))) assertEquals(SemanticVariants.SINGLE_VARIANT, object(raw).get("semanticVariantStatus"));
    }

    @Test
    void theOperationSchemaAdmitsOnlyOneSubjectAndTargetForEnrich() {
        // Codex pass-H F-H2: the machine-readable contract states the one-identity rule, not only the Java constructor.
        Map<String,Object> record = new java.util.LinkedHashMap<>(IdentityOperation.enrich("uao-aaaaaaaaaaaa", "a".repeat(64), "b".repeat(64), "pkg-0000000000000001",
                List.of("LIFE_CHRONOLOGY"), "one", "operator", "2026-09-07T00:00:00Z").toMap());
        new org.seventeenthsecond.uaofoundry.validation.SchemaValidator().validate(record, SCHEMAS.resolve("identity-operation.schema.json")).requireValid("single-subject ENRICH");
        Map<String,Object> two = new java.util.LinkedHashMap<>(record);
        two.put("subjects", List.of("uao-aaaaaaaaaaaa", "uao-bbbbbbbbbbbb")); two.put("targets", List.of("uao-aaaaaaaaaaaa", "uao-bbbbbbbbbbbb"));
        assertThrows(IllegalArgumentException.class, () -> new org.seventeenthsecond.uaofoundry.validation.SchemaValidator().validate(two, SCHEMAS.resolve("identity-operation.schema.json")).requireValid("two-subject ENRICH"));
    }

    @Test
    void aGenuineEnrichmentForkInTheJournalFailsTheIndexClosed() {
        PipelineResult t0 = manufacture("enr3-t0", fixture -> {});
        PipelineResult t1 = manufacture("enr3-t1", fixture -> addRootClaim(fixture, "Fixture assertion: branch one."));
        PipelineResult t2 = manufacture("enr3-t2", fixture -> addRootClaim(fixture, "Fixture assertion: branch two."));
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String v0 = object(array(identityByUid(registry.index(), uid).get("occurrences")).getFirst()).get("semanticVariantDigest").toString();
        FoundryRegistry.EnrichmentResult first = registry.enrich(t1.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "branch one", "operator", "2026-09-06T00:00:00Z");

        // Register the second superset as a plain occurrence, then write a second ENRICH leaving v0 straight
        // into the journal: two successors of one variant is a history that cannot have happened.
        FoundryRegistry.RegistrationResult second = registry.register(t2.packagePath());
        String v2 = array(identityByUid(registry.index(), uid).get("occurrences")).stream().map(PersistentIdentityRegistryTest::object)
                .filter(o -> second.packageId().equals(o.get("packageId"))).map(o -> o.get("semanticVariantDigest").toString()).findFirst().orElseThrow();
        String before = FileOps.treeHash(registryRoot);
        IdentityOperation fork = IdentityOperation.enrich(uid, v0, v2, second.packageId(), List.of("LIFE_CHRONOLOGY"), "branch two", "operator", "2026-09-06T00:00:00Z");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> registry.applyIdentityOperation(fork));
        assertTrue(refused.getMessage().contains("two enrichments leaving variant"), refused.getMessage());
        assertEquals(before, FileOps.treeHash(registryRoot), "a refused journal entry is removed again");
        assertTrue(registry.verify().passed());
        Map<String,Object> after = identityByUid(registry.index(), uid);
        assertEquals(1, array(after.get("variantHistory")).size());
        assertEquals(first.toVariant(), object(array(after.get("variantHistory")).getFirst()).get("toVariant"), "the accepted succession is untouched by the refused fork");
        assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS, after.get("semanticVariantStatus"),
                "the unlinked second superset is an unreconciled sibling of the enriched state, reported as such");
        assertNull(after.get("currentVariant"), "with an unreconciled sibling present there is no single current variant to name");

        // A journal entry the packages do not support never lands either.
        IdentityOperation bogus = IdentityOperation.enrich(uid, first.toVariant(), "f".repeat(64), "pkg-0000000000000000",
                List.of("LIFE_CHRONOLOGY"), "unsupported", "operator", "2026-09-06T00:00:00Z");
        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class, () -> registry.applyIdentityOperation(bogus));
        assertTrue(unsupported.getMessage().contains("holds no occurrence"), unsupported.getMessage());
        assertTrue(registry.verify().passed());
    }

    @Test
    void enrichLeavesTheRegistryByteIdenticalOnRefusalBeforeAndAfterAdmission() throws Exception {
        PipelineResult t0 = manufacture("enr5-t0", fixture -> {});
        PipelineResult t1 = manufacture("enr5-t1", fixture -> addRootClaim(fixture, "Fixture assertion: enriched."));
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        String before = FileOps.treeHash(registryRoot);

        // Before admission: operation metadata is validated first, so a blank justification never writes.
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> registry.enrich(t1.packagePath(), uid,
                List.of("LIFE_CHRONOLOGY"), "   ", "operator", "2026-09-06T00:00:00Z"));
        assertTrue(blank.getMessage().contains("justification"), blank.getMessage());
        assertEquals(before, FileOps.treeHash(registryRoot), "refusal before admission must not touch the registry");
        assertEquals(1, array(identityByUid(registry.index(), uid).get("occurrences")).size());

        // After admission (Codex pass-B F-B3, pass-C F-C2): a plain FILE squats the journal path. index() treats a
        // non-directory journal as empty, register() still admits the package -- packages/ and index.json are
        // siblings -- and only the ENRICH record write fails (its parent cannot be created). That failure is
        // genuinely after admission, needs no permission trick, and can never be skipped: the rollback must remove
        // the admitted package, restore the index byte-for-byte and leave no journal behind.
        String indexBefore = Files.readString(registryRoot.resolve("index.json"));
        String packageId = object(FileOps.readJson(t1.packagePath().resolve("manifest.json"))).get("packageId").toString();
        Path journal = registryRoot.resolve("identity-operations");
        Files.writeString(journal, "not a directory");
        String beforeWithJournal = FileOps.treeHash(registryRoot);
        IllegalArgumentException afterAdmission = assertThrows(IllegalArgumentException.class, () -> registry.enrich(t1.packagePath(), uid,
                List.of("LIFE_CHRONOLOGY"), "fails after admission", "operator", "2026-09-06T00:00:00Z"));
        assertTrue(afterAdmission.getMessage().contains("Unable to write"), "the failure is the journal write, after admission: " + afterAdmission.getMessage());
        assertFalse(Files.isDirectory(registryRoot.resolve("packages").resolve(packageId)), "the package admitted before the failure is rolled back");
        assertEquals(indexBefore, Files.readString(registryRoot.resolve("index.json")), "the index is restored byte-for-byte");
        assertTrue(Files.isRegularFile(journal), "nothing replaced the squatting file, so no ENRICH record was written");
        assertEquals(beforeWithJournal, FileOps.treeHash(registryRoot), "the registry is byte-identical to before the call");
        Files.delete(journal);
        assertEquals(before, FileOps.treeHash(registryRoot), "with the squatting file gone, byte-identical to before both attempts");
        assertTrue(registry.verify().passed());
        assertEquals(1, array(identityByUid(registry.index(), uid).get("occurrences")).size());

        // The rollback left nothing behind that blocks the same enrichment once the journal path is free again.
        FoundryRegistry.EnrichmentResult recovered = registry.enrich(t1.packagePath(), uid, List.of("LIFE_CHRONOLOGY"), "after recovery", "operator", "2026-09-06T00:00:00Z");
        assertEquals(1, recovered.assertionsAdded());
        assertTrue(Files.isDirectory(registryRoot.resolve("packages").resolve(packageId)));
        assertTrue(registry.verify().passed());
        assertEquals(recovered.toVariant(), identityByUid(registry.index(), uid).get("currentVariant"));
    }

    @Test
    void theReuseAnalyzerAcceptsAnEnrichmentOnlyWhenNamedAndOnlyAsAStrictSuperset() {
        PipelineResult t0 = manufacture("enr4-t0", fixture -> {});
        FoundryRegistry registry = registryWith(t0);
        String uid = rootUid(t0);
        org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer analyzer = new org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer(SCHEMAS);
        String contextHash = org.seventeenthsecond.uaofoundry.util.Hashes.canonicalJson(Map.of("test", "context"));

        PipelineResult superset = manufactureAgainst("enr4-superset", registry, fixture -> addRootClaim(fixture, "Fixture assertion: one more sourced statement."));
        // Unnamed, a superset is still divergence: enrichment is an operator decision, never a default.
        IllegalArgumentException unnamed = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, superset.packagePath(), contextHash));
        assertTrue(unnamed.getMessage().contains("SEMANTIC_VARIANT_DIVERGENCE"), unnamed.getMessage());
        // Named, it is reported as an enrichment with the variant it supersedes and the count it adds.
        Map<String,Object> report = analyzer.analyze(registry.index(), registryRoot, superset.packagePath(), contextHash, Set.of(uid));
        Map<String,Object> enriched = object(array(report.get("enrichedUaos")).getFirst());
        assertEquals(uid, enriched.get("uid"));
        assertEquals(java.math.BigDecimal.ONE, enriched.get("assertionsAdded"));
        assertEquals(object(array(identityByUid(registry.index(), uid).get("occurrences")).getFirst()).get("semanticVariantDigest"), enriched.get("fromVariant"));
        assertTrue(array(report.get("reusedUaos")).stream().map(PersistentIdentityRegistryTest::object).noneMatch(v -> uid.equals(v.get("uid"))), "an enriched identity is not also counted as reused");

        // Named but re-worded: refused under the enrichment law, not silently accepted.
        PipelineResult reworded = manufactureAgainst("enr4-reworded", registry, fixture -> {
            claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: re-worded.");
            addRootClaim(fixture, "Fixture assertion: one more sourced statement.");
        });
        IllegalArgumentException notSuperset = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, reworded.packagePath(), contextHash, Set.of(uid)));
        assertTrue(notSuperset.getMessage().contains("ENRICHMENT_NOT_SUPERSET"), notSuperset.getMessage());

        // Named but unchanged: the flag promised an enrichment the package does not deliver.
        PipelineResult unchanged = manufactureAgainst("enr4-unchanged", registry, fixture -> {});
        IllegalArgumentException absent = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(registry.index(), registryRoot, unchanged.packagePath(), contextHash, Set.of(uid)));
        assertTrue(absent.getMessage().contains("ENRICHMENT_TARGET_ABSENT"), absent.getMessage());
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
