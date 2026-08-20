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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 adversarial tests for merge, split, supersession and retirement.
 *
 * <p>The property under attack throughout is destructiveness. A lifecycle operation must change
 * what the registry <em>says</em> without changing anything it <em>holds</em>: every package stays
 * byte-identical, every prior determination stays readable, and every old reference still resolves
 * far enough to learn what became of it.
 */
class IdentityLifecycleTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");
    private static final String AT = "2026-08-20T00:00:00Z";

    @TempDir Path temp;
    private Path registryRoot;

    // ---------------------------------------------------------------- non-destructiveness

    @Test
    void aMergeLeavesEveryPackageByteIdenticalAndEveryHistoryReadable() {
        PipelineResult left = manufacture("merge-left", fixture -> {});
        PipelineResult right = manufacture("merge-right", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:biology:bos-taurus");
        });
        FoundryRegistry registry = registryWith(left, right);

        String leftUid = rootUid(left);
        String rightUid = rootUid(right);
        String packagesBefore = FileOps.treeHash(registryRoot.resolve("packages"));
        Map<String,Object> leftHistoryBefore = identityByUid(registry.index(), leftUid);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.MERGE, List.of(leftUid, rightUid), List.of(rightUid),
                List.of("EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH"), "Both addresses denote one bovine class.",
                List.of(), "operator:test", AT));

        assertEquals(packagesBefore, FileOps.treeHash(registryRoot.resolve("packages")),
                "a merge must not touch a single manufactured byte");
        assertTrue(registry.verify().passed());

        Map<String,Object> merged = identityByUid(registry.index(), leftUid);
        assertEquals(IdentityOperation.MERGED, merged.get("lifecycleState"));
        assertEquals(List.of(rightUid), merged.get("successorUids"));
        assertEquals(leftHistoryBefore.get("decisionHistory"), merged.get("decisionHistory"),
                "the merged identity's own history survives the merge intact");
        assertEquals(leftHistoryBefore.get("occurrences"), merged.get("occurrences"));

        // The surviving identity is untouched by having absorbed another.
        assertEquals(IdentityOperation.ACTIVE, identityByUid(registry.index(), rightUid).get("lifecycleState"));
    }

    @Test
    void aMergedReferenceStillResolvesFarEnoughToLearnItsFate() {
        PipelineResult left = manufacture("fate-left", fixture -> {});
        PipelineResult right = manufacture("fate-right", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:bos-taurus"));
        FoundryRegistry registry = registryWith(left, right);
        String leftUid = rootUid(left);
        String rightUid = rootUid(right);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.MERGE, List.of(leftUid, rightUid), List.of(rightUid),
                List.of("MANUAL_REVIEW"), "One object under two addresses.", List.of(), null, AT));

        Map<String,Object> record = registry.identityRecord(IdentityReference.uid(leftUid));
        Map<String,Object> resolution = object(record.get("resolution"));
        assertEquals("UNRESOLVED", resolution.get("decision"),
                "resolution must not be silently redirected to the survivor");
        assertEquals(List.of(IdentityResolution.IDENTITY_MERGED), resolution.get("reasonCodes"));
        assertTrue(array(resolution.get("candidateUids")).contains(rightUid),
                "but the caller must be told what it became");
        assertFalse(array(record.get("candidates")).isEmpty(), "the old identity remains inspectable");
    }

    @Test
    void aSplitNamesEveryResultAndChoosesNoneOfThem() {
        PipelineResult conflated = manufacture("split-src", fixture -> {});
        PipelineResult first = manufacture("split-a", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:dairy-cattle"));
        PipelineResult second = manufacture("split-b", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:beef-cattle"));
        FoundryRegistry registry = registryWith(conflated, first, second);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.SPLIT, List.of(rootUid(conflated)),
                List.of(rootUid(first), rootUid(second)),
                List.of("CONFLATION_DETECTED"), "The original address conflated two husbandry classes.",
                List.of(), null, AT));

        Map<String,Object> resolution = object(registry.identityRecord(
                IdentityReference.uid(rootUid(conflated))).get("resolution"));
        assertEquals("UNRESOLVED", resolution.get("decision"));
        assertEquals(List.of(IdentityResolution.IDENTITY_SPLIT), resolution.get("reasonCodes"));
        assertEquals(3, array(resolution.get("candidateUids")).size(),
                "a split must surface the original and both results, and pick neither");
    }

    @Test
    void supersessionAndRetirementBothStopResolutionAndDifferInWhatTheyOffer() {
        PipelineResult original = manufacture("sup-a", fixture -> {});
        PipelineResult successor = manufacture("sup-b", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:bos-taurus"));
        PipelineResult retired = manufacture("sup-c", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:material:granite"));
        FoundryRegistry registry = registryWith(original, successor, retired);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(original)), List.of(rootUid(successor)),
                List.of("SUPERSEDED_BY_REVISION"), "Replaced by a better-scoped identity.", List.of(), null, AT));
        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(retired)), List.of(),
                List.of("WITHDRAWN"), "Manufactured in error.", List.of(), null, AT));

        Map<String,Object> superseded = object(registry.identityRecord(
                IdentityReference.uid(rootUid(original))).get("resolution"));
        assertEquals(List.of(IdentityResolution.IDENTITY_SUPERSEDED), superseded.get("reasonCodes"));
        assertTrue(array(superseded.get("candidateUids")).contains(rootUid(successor)),
                "supersession names a successor");

        Map<String,Object> gone = object(registry.identityRecord(
                IdentityReference.uid(rootUid(retired))).get("resolution"));
        assertEquals(List.of(IdentityResolution.IDENTITY_RETIRED), gone.get("reasonCodes"));
        assertEquals(List.of(rootUid(retired)), gone.get("candidateUids"),
                "retirement offers nothing in its place, by definition");
    }

    @Test
    void aLifecycleOperationDoesNotDisturbUnrelatedIdentities() {
        PipelineResult target = manufacture("iso-a", fixture -> {});
        PipelineResult unrelated = manufacture("iso-b", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        });
        FoundryRegistry registry = registryWith(target, unrelated);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(target)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT));

        assertEquals("SAME", object(registry.identityRecord(
                IdentityReference.uid(rootUid(unrelated))).get("resolution")).get("decision"));
    }

    // ---------------------------------------------------------------- reuse must not undo a decision

    @Test
    void aRetiredIdentityIsNotSilentlyReusedByTheNextManufacture() {
        PipelineResult seed = manufacture("reuse-seed", fixture -> {});
        FoundryRegistry registry = registryWith(seed);
        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(seed)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT));

        // The same key is proposed again. Without the lifecycle guard the retirement would be
        // quietly undone by whichever manufacture next happened to name that address.
        PipelineResult again = manufactureAgainst("reuse-after", registry, fixture -> {});
        Map<String,Object> decision = decisions(again).stream()
                .filter(v -> rootUid(seed).equals(v.get("uaoId"))).findFirst().orElseThrow();
        assertEquals("UNRESOLVED", decision.get("decision"));
        assertEquals(List.of(IdentityResolution.IDENTITY_RETIRED), decision.get("reasonCodes"));
    }

    // ---------------------------------------------------------------- adversarial

    @Test
    void anOperationOnAnUnregisteredIdentityIsRefused() {
        FoundryRegistry registry = registryWith(manufacture("adv-seed", fixture -> {}));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.applyIdentityOperation(IdentityOperation.create(
                        IdentityOperation.Kind.RETIRE, List.of("uao-000000000000"), List.of(),
                        List.of("WITHDRAWN"), "Nothing to withdraw.", List.of(), null, AT)));
        assertTrue(failure.getMessage().contains("not a registered identity"), failure.getMessage());
    }

    @Test
    void twoContradictoryOperationsOnOneIdentityAreRefusedAndLeaveNothingBehind() {
        PipelineResult first = manufacture("contra-a", fixture -> {});
        PipelineResult second = manufacture("contra-b", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:bos-taurus"));
        FoundryRegistry registry = registryWith(first, second);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(first)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT));
        String after = FileOps.treeHash(registryRoot);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.applyIdentityOperation(IdentityOperation.create(
                        IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(first)), List.of(rootUid(second)),
                        List.of("SUPERSEDED_BY_REVISION"), "Also superseded?", List.of(), null, AT)));
        assertTrue(failure.getMessage().contains("two lifecycle operations"), failure.getMessage());
        assertEquals(after, FileOps.treeHash(registryRoot),
                "a refused operation must leave the registry byte-identical");
        assertTrue(registry.verify().passed());
    }

    @Test
    void aCycleOfSupersessionsIsRefused() {
        PipelineResult a = manufacture("cyc-a", fixture -> {});
        PipelineResult b = manufacture("cyc-b", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:bos-taurus"));
        FoundryRegistry registry = registryWith(a, b);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(a)), List.of(rootUid(b)),
                List.of("SUPERSEDED_BY_REVISION"), "A becomes B.", List.of(), null, AT));
        String after = FileOps.treeHash(registryRoot);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.applyIdentityOperation(IdentityOperation.create(
                        IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(b)), List.of(rootUid(a)),
                        List.of("SUPERSEDED_BY_REVISION"), "B becomes A.", List.of(), null, AT)));
        assertTrue(failure.getMessage().contains("cycle"), failure.getMessage());
        assertEquals(after, FileOps.treeHash(registryRoot));
    }

    @Test
    void aChainOfSupersessionsIsPermittedBecauseItIsAHistoryThatCouldHaveHappened() {
        PipelineResult a = manufacture("chain-a", fixture -> {});
        PipelineResult b = manufacture("chain-b", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:bos-taurus"));
        PipelineResult c = manufacture("chain-c", fixture ->
                identity(fixture, "cid-root").put("resolutionKey", "fixture:biology:dairy-cattle"));
        FoundryRegistry registry = registryWith(a, b, c);

        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(a)), List.of(rootUid(b)),
                List.of("SUPERSEDED_BY_REVISION"), "A becomes B.", List.of(), null, AT));
        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.SUPERSEDE, List.of(rootUid(b)), List.of(rootUid(c)),
                List.of("SUPERSEDED_BY_REVISION"), "B becomes C.", List.of(), null, AT));

        assertTrue(registry.verify().passed());
        assertEquals(IdentityOperation.SUPERSEDED, identityByUid(registry.index(), rootUid(a)).get("lifecycleState"));
        assertEquals(IdentityOperation.SUPERSEDED, identityByUid(registry.index(), rootUid(b)).get("lifecycleState"));
        assertEquals(IdentityOperation.ACTIVE, identityByUid(registry.index(), rootUid(c)).get("lifecycleState"));
    }

    @Test
    void anEditedOperationRecordBreaksItsContentAddress() {
        PipelineResult seed = manufacture("tamper", fixture -> {});
        FoundryRegistry registry = registryWith(seed);
        FoundryRegistry.OperationResult recorded = registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(seed)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn for a stated reason.", List.of(), null, AT));

        Path file = registryRoot.resolve("identity-operations").resolve(recorded.operationId() + ".json");
        assertTrue(Files.isRegularFile(file));
        Map<String,Object> tampered = object(FileOps.readJson(file));
        tampered.put("justification", "Withdrawn for a different reason.");
        FileOps.writeJson(file, tampered);

        assertFalse(registry.verify().passed(), "an operation must not be rewritable after the fact");
    }

    @Test
    void anOperationWithoutAStatedReasonCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of("uao-000000000000"), List.of(),
                List.of("WITHDRAWN"), "   ", List.of(), null, AT));
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of("uao-000000000000"), List.of(),
                List.of(), "Reasonless.", List.of(), null, AT));
    }

    @Test
    void degenerateOperationShapesAreRefused() {
        String a = "uao-000000000000";
        String b = "uao-111111111111";
        // Merging one identity is not an operation.
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.MERGE, List.of(a), List.of(a), List.of("X"), "j", List.of(), null, AT));
        // A split into one thing is not a split.
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.SPLIT, List.of(a), List.of(b), List.of("X"), "j", List.of(), null, AT));
        // A retirement with a successor is a supersession wearing the wrong name.
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(a), List.of(b), List.of("X"), "j", List.of(), null, AT));
        // Nothing supersedes itself.
        assertThrows(IllegalArgumentException.class, () -> IdentityOperation.create(
                IdentityOperation.Kind.SUPERSEDE, List.of(a), List.of(a), List.of("X"), "j", List.of(), null, AT));
    }

    @Test
    void recordingTheSameOperationTwiceIsIdempotent() {
        PipelineResult seed = manufacture("idem", fixture -> {});
        FoundryRegistry registry = registryWith(seed);
        IdentityOperation operation = IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(seed)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT);

        FoundryRegistry.OperationResult first = registry.applyIdentityOperation(operation);
        String after = FileOps.treeHash(registryRoot);
        FoundryRegistry.OperationResult again = registry.applyIdentityOperation(operation);

        assertEquals(first.operationId(), again.operationId());
        assertTrue(again.alreadyPresent());
        assertEquals(after, FileOps.treeHash(registryRoot), "a content-addressed replay changes nothing");
    }

    @Test
    void manufacturedPackagesStillVerifyAfterTheirIdentityIsRetired() {
        PipelineResult seed = manufacture("verify-after", fixture -> {});
        FoundryRegistry registry = registryWith(seed);
        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid(seed)), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT));

        assertTrue(new PackageVerifier(SCHEMAS).verify(seed.packagePath()).passed(),
                "a lifecycle decision is registry-level and must not invalidate a manufactured package");
        assertTrue(registry.verify().passed());
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

    private FoundryRegistry registryWith(PipelineResult... packages) {
        registryRoot = temp.resolve("registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        for (PipelineResult result : packages) registry.register(result.packagePath());
        return registry;
    }

    private static List<Map<String,Object>> decisions(PipelineResult result) {
        return array(object(FileOps.readJson(result.packagePath().resolve("identity-resolution.json")))
                .get("identityDecisions")).stream().map(IdentityLifecycleTest::object).toList();
    }

    private static Map<String,Object> identity(Map<String,Object> fixture, String candidateId) {
        return array(object(fixture.get("candidates")).get("identities")).stream()
                .map(IdentityLifecycleTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId"))).findFirst().orElseThrow();
    }

    private static String rootUid(PipelineResult result) {
        return object(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("rootUaoId").toString();
    }

    private static Map<String,Object> identityByUid(Map<String,Object> index, String uid) {
        return array(index.get("identities")).stream().map(IdentityLifecycleTest::object)
                .filter(v -> uid.equals(v.get("uid"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
