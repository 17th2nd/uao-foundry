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
import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 regression tests for the persistent identity kernel.
 *
 * <p>These encode the identity properties the programme requires to stay true regardless of later
 * work. The recurring theme is that <em>resemblance is not identity</em>: sharing a name, a label
 * or a path proves nothing, and the only things permitted to establish sameness are an exact
 * address or durable third-party evidence.
 */
class PersistentIdentityKernelTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");

    @TempDir Path temp;

    // ---------------------------------------------------------------- kernel content

    @Test
    void externalIdentifiersReachTheCanonicalPackageAndTheRegistryIndex() {
        // Finding P0-1: externalIdentifiers was schema-declared, fixture-supplied and read by no
        // code, so a provider supplying durable external identity had it silently discarded.
        PipelineResult result = manufacture("ext-carry", fixture -> {
            identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830"));
        });

        Map<String,Object> kernel = kernelOf(result, rootUid(result));
        assertEquals(Map.of("wikidata", "Q830"), kernel.get("external_identifiers"),
                "durable external identity must survive into the canonical package");

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(result.packagePath());
        Map<String,Object> indexed = identityByUid(registry.index(), rootUid(result));
        assertEquals(Map.of("wikidata", "Q830"), indexed.get("externalIdentifiers"),
                "the registry must index external identity so it can route a later reference");

        List<Object> hits = registry.search("wikidata:Q830");
        assertFalse(hits.isEmpty(), "an external identifier must be a first-class lookup key");
        assertTrue(matchKinds(hits.getFirst()).contains("EXTERNAL_IDENTIFIER"));
    }

    @Test
    void semanticTypeIsDeclaredByTheKeyGrammarAndNullWhenTheNamespaceDeclaresNone() {
        PipelineResult typed = manufacture("typed", fixture -> {});
        assertEquals("biology", kernelOf(typed, rootUid(typed)).get("semantic_type"));

        // An external registry identifier says which object is meant, not what kind it is.
        // A guessed type would be indistinguishable from a declared one, so it stays null.
        PipelineResult external = manufacture("external", fixture -> {
            identity(fixture, "cid-root").put("resolutionKey", "ext:wikidata:Q830");
        });
        assertNull(kernelOf(external, uidForKey(external, "ext:wikidata:Q830")).get("semantic_type"));
    }

    // ---------------------------------------------------------------- identity vs state

    @Test
    void stateChangeDoesNotCreateANewIdentity() {
        PipelineResult before = manufacture("state-t0", fixture -> {});
        PipelineResult after = manufacture("state-t1", fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: revised state at t1."));

        String uid = rootUid(before);
        assertEquals(uid, rootUid(after), "a state change must not change the identity address");

        Map<String,Object> t0 = kernelOf(before, uid);
        Map<String,Object> t1 = kernelOf(after, uid);
        assertEquals(t0.get("identity_digest"), t1.get("identity_digest"),
                "identity_digest covers what the identity is, and must be stable across state change");
        assertNotEquals(t0.get("state_version"), t1.get("state_version"),
                "state_version covers what the identity asserts, and must move when state moves");

        // Both states are preserved as occurrences of one identity; neither is chosen or discarded.
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(before.packagePath());
        registry.register(after.packagePath());
        Map<String,Object> indexed = identityByUid(registry.index(), uid);
        assertEquals(2, array(indexed.get("stateVersions")).size(), "both states must remain inspectable");
        assertEquals(2, array(indexed.get("occurrences")).size());
    }

    @Test
    void renameUnderOneAddressPreservesIdentity() {
        PipelineResult original = manufacture("rename-before", fixture -> {});
        PipelineResult renamed = manufacture("rename-after", fixture ->
                identity(fixture, "cid-root").put("label", "adult female bovine"));

        assertEquals(rootUid(original), rootUid(renamed),
                "renaming what we call an object must not manufacture a second object");
        assertNotEquals(kernelOf(original, rootUid(original)).get("identity_digest"),
                kernelOf(renamed, rootUid(renamed)).get("identity_digest"),
                "a rename does change identity-bearing material, and must be visible as such");
    }

    @Test
    void oneIdentityEvidencedFromDifferentSourcesIsStillOneIdentity() {
        // Regression. Adding alias_provenance to the kernel in Phase 3 put candidate and source
        // refs inside the meaning-bearing projection, so the same identity acquired from a
        // differently-named source was flagged MULTIPLE_UNRECONCILED_VARIANTS and refused for
        // reuse -- defeating the whole point of persistent identity, which is that one identity
        // may be evidenced repeatedly from different places. Provenance is not meaning.
        PipelineResult first = manufacture("src-a", fixture -> {});
        PipelineResult second = manufacture("src-b", fixture -> renameSource(fixture, "src-cow-bio", "src-cow-alt"));

        String uid = rootUid(first);
        assertEquals(uid, rootUid(second));
        assertNotEquals(kernelOf(first, uid).get("alias_provenance"), kernelOf(second, uid).get("alias_provenance"),
                "the provenance genuinely differs between the two manufactures");

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(first.packagePath());
        registry.register(second.packagePath());

        Map<String,Object> indexed = identityByUid(registry.index(), uid);
        assertEquals(SemanticVariants.SINGLE_VARIANT, indexed.get("semanticVariantStatus"),
                "differently-sourced evidence for one identity must not read as a semantic variant");
        assertEquals(1, array(indexed.get("stateVersions")).size(), "one identity in one state, evidenced twice");
        assertEquals(2, array(indexed.get("occurrences")).size());

        assertEquals(IdentityDecision.SAME,
                new IdentityResolver(registry.index()).resolve(IdentityReference.uid(uid)).decision(),
                "and it must remain reusable");
    }

    // ---------------------------------------------------------------- resemblance is not identity

    @Test
    void aSharedAliasDoesNotResolveToTheSameIdentity() {
        PipelineResult first = manufacture("alias-a", fixture -> {});
        PipelineResult second = manufacture("alias-b", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:machinery:cattle-crush");
            root.put("label", "cattle crush");
            root.put("aliases", List.of("cow"));   // the same human word, a different object
        });
        assertNotEquals(rootUid(first), rootUid(second));

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(first.packagePath());
        registry.register(second.packagePath());

        IdentityResolver resolver = new IdentityResolver(registry.index());
        IdentityResolution resolution = resolver.resolve(IdentityReference.alias("cow"));
        assertEquals(IdentityDecision.UNRESOLVED, resolution.decision(),
                "a name match is a hint and must never decide identity");
        assertTrue(resolution.reasonCodes().contains(IdentityResolution.ALIAS_MATCH_INSUFFICIENT));
        assertTrue(resolution.candidateUids().size() >= 2, "the ambiguity must be surfaced, not hidden");
        assertNull(resolution.uid());
    }

    @Test
    void anUnknownReferenceIsUnresolvedRatherThanDifferent() {
        PipelineResult result = manufacture("unknown", fixture -> {});
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(result.packagePath());
        IdentityResolver resolver = new IdentityResolver(registry.index());

        for (IdentityReference reference : List.of(
                IdentityReference.alias("something never registered"),
                IdentityReference.uid("uao-000000000000"),
                IdentityReference.externalIdentifier("wikidata", "Q1"))) {
            IdentityResolution resolution = resolver.resolve(reference);
            assertEquals(IdentityDecision.UNRESOLVED, resolution.decision(),
                    "never having seen a reference is not evidence that it denotes a different object");
            assertTrue(resolution.reasonCodes().contains(IdentityResolution.NO_REGISTERED_MATCH));
        }
    }

    // ---------------------------------------------------------------- external identity evidence

    @Test
    void externalIdentifierContinuityResolvesToTheRegisteredIdentity() {
        PipelineResult result = manufacture("ext-continuity", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(result.packagePath());

        IdentityResolution resolution = new IdentityResolver(registry.index())
                .resolve(IdentityReference.externalIdentifier("wikidata", "Q830"));
        assertEquals(IdentityDecision.SAME, resolution.decision());
        assertEquals(rootUid(result), resolution.uid());
        assertTrue(resolution.reasonCodes().contains(IdentityResolution.EXTERNAL_IDENTIFIER_CONTINUITY));
    }

    @Test
    void oneExternalIdentifierMatchingSeveralIdentitiesIsAmbiguousRatherThanArbitrary() {
        PipelineResult first = manufacture("amb-a", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        PipelineResult second = manufacture("amb-b", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:biology:bos-taurus");
            root.put("externalIdentifiers", Map.of("wikidata", "Q830"));
        });

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(first.packagePath());
        registry.register(second.packagePath());

        IdentityResolution resolution = new IdentityResolver(registry.index())
                .resolve(IdentityReference.externalIdentifier("wikidata", "Q830"));
        assertEquals(IdentityDecision.UNRESOLVED, resolution.decision());
        assertTrue(resolution.reasonCodes().contains(IdentityResolution.EXTERNAL_IDENTIFIER_AMBIGUOUS));
        assertEquals(2, resolution.candidateUids().size());
    }

    @Test
    void sameEvidenceUnderADifferentAddressIsAMergeCandidateAndNeverAnImplicitMerge() {
        PipelineResult registered = manufacture("merge-a", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(registered.packagePath());

        IdentityResolution resolution = new IdentityResolver(registry.index())
                .resolveCandidate("fixture:biology:bos-taurus", Map.of("wikidata", "Q830"));
        assertEquals(IdentityDecision.UNRESOLVED, resolution.decision(),
                "merging two addresses is a governed append-preserving operation, never a manufacture-time side effect");
        assertTrue(resolution.reasonCodes().contains(IdentityResolution.EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH));
        assertEquals(List.of(rootUid(registered)), resolution.candidateUids());
    }

    @Test
    void contradictingExternalEvidenceUnderOneAddressIsPositiveEvidenceOfDifference() {
        PipelineResult registered = manufacture("contra", fixture ->
                identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830")));
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(registered.packagePath());

        IdentityResolution resolution = new IdentityResolver(registry.index())
                .resolveCandidate(kernelOf(registered, rootUid(registered)).get("resolution_key").toString(),
                        Map.of("wikidata", "Q99999"));
        assertEquals(IdentityDecision.DIFFERENT, resolution.decision());
        assertTrue(resolution.reasonCodes().contains(IdentityResolution.EXTERNAL_IDENTIFIER_CONTRADICTION));
    }

    // ---------------------------------------------------------------- fail-closed manufacture

    @Test
    void anAddressContradictingItsOwnExternalIdentifierIsRefused() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                manufacture("self-contra", fixture -> {
                    Map<String,Object> root = identity(fixture, "cid-root");
                    root.put("resolutionKey", "ext:wikidata:Q830");
                    root.put("externalIdentifiers", Map.of("wikidata", "Q99999"));
                }));
        assertTrue(failure.getMessage().contains("EXTERNAL_IDENTIFIER_CONTRADICTION"), failure.getMessage());
    }

    @Test
    void candidatesSharingAnAddressMayNotDisagreeAboutDurableExternalIdentity() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                manufacture("group-contra", fixture -> {
                    identity(fixture, "cid-root").put("externalIdentifiers", Map.of("wikidata", "Q830"));
                    Map<String,Object> other = identity(fixture, "cid-bovine-context");
                    other.put("resolutionKey", "fixture:biology:adult-female-cattle");
                    other.put("externalIdentifiers", Map.of("wikidata", "Q99999"));
                }));
        assertTrue(failure.getMessage().contains("EXTERNAL_IDENTIFIER_CONTRADICTION"), failure.getMessage());
    }

    @Test
    void anEphemeralExternalIdentifierIsRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                ExternalIdentifiers.requireCanonical(Map.of("Wikidata", "Q830"), "test"),
                "an upper-case scheme would make one identifier compare unequal to itself");
        assertThrows(IllegalArgumentException.class, () ->
                ExternalIdentifiers.requireCanonical(Map.of("wikidata", " Q830 "), "test"),
                "untrimmed identifiers would silently split one identity in two");
        assertThrows(IllegalArgumentException.class, () ->
                ExternalIdentifiers.requireCanonical(Map.of("wikidata", "Q 830"), "test"));
    }

    // ---------------------------------------------------------------- derived, never authored

    @Test
    void aForgedIdentityDigestFailsVerification() {
        PipelineResult result = manufacture("forge-identity", fixture -> {});
        forgeKernelField(result, "identity_digest", "0".repeat(64));
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed(), "the kernel digests are derived and must not be assertable");
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("identity kernel")), verification.errors().toString());
    }

    @Test
    void aForgedStateVersionFailsVerification() {
        PipelineResult result = manufacture("forge-state", fixture -> {});
        forgeKernelField(result, "state_version", "1".repeat(64));
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("identity kernel")), verification.errors().toString());
    }

    // ---------------------------------------------------------------- sticky unresolved variants

    @Test
    void unreconciledVariantsBlockResolutionWithoutAffectingUnrelatedIdentities() {
        PipelineResult t0 = manufacture("sticky-t0", fixture -> {});
        PipelineResult t1 = manufacture("sticky-t1", fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: divergent variant."));
        PipelineResult unrelated = manufacture("sticky-other", fixture -> {
            Map<String,Object> root = identity(fixture, "cid-root");
            root.put("resolutionKey", "fixture:material:granite");
            root.put("label", "granite");
            root.put("aliases", List.of("granite rock"));
        });

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        registry.register(t0.packagePath());
        registry.register(t1.packagePath());
        registry.register(unrelated.packagePath());

        Map<String,Object> index = registry.index();
        String ambiguous = rootUid(t0);
        assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS,
                identityByUid(index, ambiguous).get("semanticVariantStatus"));

        IdentityResolver resolver = new IdentityResolver(index);
        IdentityResolution blocked = resolver.resolve(IdentityReference.uid(ambiguous));
        assertEquals(IdentityDecision.UNRESOLVED, blocked.decision(),
                "an unreconciled identity stays unresolved however exact the address");
        assertTrue(blocked.reasonCodes().contains(IdentityResolution.SEMANTIC_VARIANTS_UNRECONCILED));

        IdentityResolution unaffected = resolver.resolve(IdentityReference.uid(rootUid(unrelated)));
        assertEquals(IdentityDecision.SAME, unaffected.decision(),
                "ambiguity in one identity must not contaminate unrelated identities");
    }

    // ---------------------------------------------------------------- helpers

    private PipelineResult manufacture(String suffix, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(COW)));
        mutation.accept(fixture);
        Path path = temp.resolve("fixture-" + suffix + ".json");
        FileOps.writeJson(path, fixture);

        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        return new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), "test-sha")
                .manufacture(request, new FixtureProvider(path, SCHEMAS), false);
    }

    /** Renames a source everywhere it is referenced, changing provenance without changing meaning. */
    private static void renameSource(Map<String,Object> fixture, String from, String to) {
        String canonical = Json.canonical(fixture)
                .replace("\"" + from + "\"", "\"" + to + "\"")
                .replace("fixture://" + from, "fixture://" + to);
        fixture.clear();
        fixture.putAll(object(Json.parse(canonical)));
    }

    private static Map<String,Object> identity(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "identities", candidateId);
    }

    private static Map<String,Object> claim(Map<String,Object> fixture, String candidateId) {
        return byCandidateId(fixture, "claims", candidateId);
    }

    private static Map<String,Object> byCandidateId(Map<String,Object> fixture, String category, String candidateId) {
        return array(object(fixture.get("candidates")).get(category)).stream()
                .map(PersistentIdentityKernelTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId")))
                .findFirst().orElseThrow(() -> new AssertionError("fixture has no " + category + " " + candidateId));
    }

    private static String rootUid(PipelineResult result) {
        return object(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("rootUaoId").toString();
    }

    private static String uidForKey(PipelineResult result, String resolutionKey) {
        return array(FileOps.readJson(result.packagePath().resolve("canonical-identities.json"))).stream()
                .map(PersistentIdentityKernelTest::object)
                .filter(v -> resolutionKey.equals(kernel(v).get("resolution_key")))
                .findFirst().orElseThrow().get("uid").toString();
    }

    private static Map<String,Object> kernelOf(PipelineResult result, String uid) {
        return array(FileOps.readJson(result.packagePath().resolve("canonical-identities.json"))).stream()
                .map(PersistentIdentityKernelTest::object)
                .filter(v -> uid.equals(v.get("uid")))
                .findFirst().map(PersistentIdentityKernelTest::kernel).orElseThrow();
    }

    private static Map<String,Object> kernel(Map<String,Object> uao) {
        return object(object(uao.get("internal_state")).get("foundry_identity"));
    }

    /** Rewrites one derived kernel field in an otherwise intact package. */
    private static void forgeKernelField(PipelineResult result, String field, String value) {
        Path path = result.packagePath().resolve("canonical-identities.json");
        List<Object> identities = array(FileOps.readJson(path));
        kernel(object(identities.getFirst())).put(field, value);
        FileOps.writeJson(path, identities);
    }

    private static Map<String,Object> identityByUid(Map<String,Object> index, String uid) {
        return array(index.get("identities")).stream().map(PersistentIdentityKernelTest::object)
                .filter(v -> uid.equals(v.get("uid"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static List<String> matchKinds(Object hit) {
        return (List<String>) (List<?>) array(object(hit).get("matchKinds"));
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
