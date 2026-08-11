package org.seventeenthsecond.uaofoundry.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FoundryRegistryTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @TempDir Path temp;

    @Test
    void registryIsDeterministicIndependentOfAdmissionOrderAndSearchesStableIdentityMetadata() {
        PipelineResult firstPackage = manufacture("cow", "biological-cow.json", "first");
        PipelineResult secondPackage = manufacture("granite", "material-granite.json", "second");

        FoundryRegistry left = new FoundryRegistry(temp.resolve("registry-left"), SCHEMAS);
        FoundryRegistry right = new FoundryRegistry(temp.resolve("registry-right"), SCHEMAS);

        FoundryRegistry.RegistrationResult leftFirst = left.register(firstPackage.packagePath());
        left.register(secondPackage.packagePath());
        right.register(secondPackage.packagePath());
        right.register(firstPackage.packagePath());

        assertEquals(Json.canonical(left.index()), Json.canonical(right.index()), "registry index must not depend on admission order");
        assertTrue(left.verify().passed());
        assertTrue(right.verify().passed());
        assertEquals(2, left.verify().packageCount());
        assertTrue(left.verify().identityCount() >= 3);

        FoundryRegistry.RegistrationResult duplicate = left.register(firstPackage.packagePath());
        assertTrue(duplicate.alreadyPresent(), "byte-identical package registration must be idempotent");
        assertEquals(leftFirst.packageId(), duplicate.packageId());

        List<Object> aliasMatches = left.search("cow");
        assertFalse(aliasMatches.isEmpty());
        Map<String,Object> firstMatch = object(aliasMatches.getFirst());
        assertTrue(array(firstMatch.get("matchKinds")).contains("ALIAS"));
        Map<String,Object> identity = object(firstMatch.get("identity"));
        assertTrue(((String) identity.get("uid")).matches("uao-[a-f0-9]{12}"));
        assertEquals("fixture:biology:adult-female-cattle", identity.get("resolutionKey"));

        Map<String,Object> context = left.discoveryContext("cow", 1);
        assertFalse(array(context.get("matches")).isEmpty());
        assertEquals(java.math.BigDecimal.valueOf(left.verify().identityCount()), context.get("totalIdentities"));
        assertEquals(Boolean.TRUE, context.get("catalogTruncated"));
    }

    @Test
    void registryVerificationDetectsTamperedRegisteredPackage() throws Exception {
        PipelineResult packageResult = manufacture("granite", "material-granite.json", "tamper");
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        FoundryRegistry.RegistrationResult registration = registry.register(packageResult.packagePath());
        assertTrue(registry.verify().passed());

        Path registered = registration.registryPath();
        Path snapshot;
        try (var stream = Files.list(registered.resolve("source-corpus"))) {
            snapshot = stream.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        Files.writeString(snapshot, "\nREGISTRY-TAMPER\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        FoundryRegistry.VerificationResult result = registry.verify();
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Package verification failed")));
    }

    @Test
    void registryCliRoundTripsRegisterSearchAndVerify() {
        PipelineResult packageResult = manufacture("cow", "biological-cow.json", "cli");
        Path registryPath = temp.resolve("cli-registry");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        RegistryApplication app = new RegistryApplication(new java.io.PrintStream(out), new java.io.PrintStream(err));

        assertEquals(0, app.run(new String[]{"register", packageResult.packagePath().toString(), "--registry", registryPath.toString()}), err.toString());
        out.reset(); err.reset();
        assertEquals(0, app.run(new String[]{"search", "cow", "--registry", registryPath.toString()}), err.toString());
        Map<String,Object> search = object(Json.parse(out.toString(StandardCharsets.UTF_8)));
        assertFalse(array(search.get("matches")).isEmpty());
        out.reset(); err.reset();
        assertEquals(0, app.run(new String[]{"verify", "--registry", registryPath.toString()}), err.toString());
        Map<String,Object> verification = object(Json.parse(out.toString(StandardCharsets.UTF_8)));
        assertEquals(Boolean.TRUE, verification.get("passed"));
    }


    @Test
    void semanticKeyCollisionIsRejectedTransactionally() {
        PipelineResult granite = manufacture("granite", "material-granite.json", "continuity-seed");
        Path registryRoot = temp.resolve("continuity-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        registry.register(granite.packagePath());
        String before = FileOps.treeHash(registryRoot);

        @SuppressWarnings("unchecked") Map<String,Object> bundle = (Map<String,Object>) FileOps.readJson(FIXTURES.resolve("material-granite.json"));
        Map<String,Object> candidates = Json.object(bundle.get("candidates"), "candidates");
        List<Object> identities = Json.array(candidates.get("identities"), "identities");
        Map<String,Object> root = identities.stream().map(v -> Json.object(v, "identity")).filter(v -> Boolean.TRUE.equals(v.get("root"))).findFirst().orElseThrow();
        root.put("label", "asbestos insulation board"); root.put("aliases", List.of("asbestos"));
        Path collisionFixture = temp.resolve("collision-fixture.json"); FileOps.writeJson(collisionFixture, bundle);
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("granite", "en", "experimental");
        PipelineResult collision = new FoundryPipeline(SCHEMAS, temp.resolve("work-collision"), temp.resolve("dist-collision"), "test-sha")
                .manufacture(request, new FixtureProvider(collisionFixture, SCHEMAS), false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.register(collision.packagePath()));
        assertTrue(ex.getMessage().contains("continuity") || ex.getMessage().contains("semantic merge"));
        assertEquals(before, FileOps.treeHash(registryRoot), "failed admission must leave registry byte-for-byte unchanged");
        assertTrue(registry.verify().passed());
    }

    @Test
    void searchFailsClosedOnTamperedStoredIndex() {
        PipelineResult granite = manufacture("granite", "material-granite.json", "index-tamper");
        Path registryRoot = temp.resolve("index-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        registry.register(granite.packagePath());
        Map<String,Object> index = Json.object(FileOps.readJson(registryRoot.resolve("index.json")), "index");
        Map<String,Object> identity = Json.object(Json.array(index.get("identities"), "identities").getFirst(), "identity");
        @SuppressWarnings("unchecked") List<Object> labels = (List<Object>) identity.get("canonicalLabels");
        labels.add("FORGED LABEL INJECTED INTO INDEX");
        FileOps.writeJson(registryRoot.resolve("index.json"), index);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.search("FORGED LABEL INJECTED INTO INDEX"));
        assertTrue(ex.getMessage().contains("does not match verified immutable package contents"));
    }

    @Test
    void sameStableIdentityAndSemanticVariantPreservesRepeatOccurrences() {
        PipelineResult first = manufacture("granite", "material-granite.json", "same-variant-a");
        Map<String,Object> bundle = object(FileOps.readJson(FIXTURES.resolve("material-granite.json")));
        Map<String,Object> source = object(array(bundle.get("sources")).getFirst());
        source.put("content", source.get("content") + " Additional provenance-only source material.");
        Path repeatFixture = temp.resolve("granite-repeat-provenance.json");
        FileOps.writeJson(repeatFixture, bundle);
        PipelineResult second = manufacture("granite", repeatFixture, "same-variant-b", "test-sha");
        assertNotEquals(first.packagePath().getFileName(), second.packagePath().getFileName(), "provenance-distinct packages must be independently addressable");

        FoundryRegistry registry = new FoundryRegistry(temp.resolve("same-variant-registry"), SCHEMAS);
        registry.register(first.packagePath());
        registry.register(second.packagePath());
        Map<String,Object> identity = identityByResolutionKey(registry.index(), "fixture:material:granite");
        assertEquals(SemanticVariants.SINGLE_VARIANT, identity.get("semanticVariantStatus"));
        List<Object> occurrences = array(identity.get("occurrences"));
        assertEquals(2, occurrences.size());
        Set<Object> digests = new LinkedHashSet<>();
        occurrences.forEach(v -> digests.add(object(v).get("semanticVariantDigest")));
        assertEquals(1, digests.size(), "provenance-only package differences must retain one semantic variant");
        assertTrue(registry.verify().passed());
    }

    @Test
    void differingSemanticVariantsRemainImmutableAndSurfaceUnreconciledStateDeterministically() {
        PipelineResult original = manufacture("granite", "material-granite.json", "variant-original");
        Map<String,Object> bundle = object(FileOps.readJson(FIXTURES.resolve("material-granite.json")));
        Map<String,Object> candidates = object(bundle.get("candidates"));
        Map<String,Object> claim = object(array(candidates.get("claims")).getFirst());
        claim.put("statement", "A different unreconciled assertion set for the same stable granite identity.");
        Path variantFixture = temp.resolve("granite-semantic-variant.json");
        FileOps.writeJson(variantFixture, bundle);
        PipelineResult variant = manufacture("granite", variantFixture, "variant-different", "test-sha");

        Path registryRoot = temp.resolve("different-variant-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        FoundryRegistry.RegistrationResult first = registry.register(original.packagePath());
        FoundryRegistry.RegistrationResult second = registry.register(variant.packagePath());
        Map<String,Object> before = registry.index();
        Map<String,Object> identity = identityByResolutionKey(before, "fixture:material:granite");
        assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS, identity.get("semanticVariantStatus"));
        List<Object> occurrences = array(identity.get("occurrences"));
        assertEquals(2, occurrences.size());
        Set<Object> digests = new LinkedHashSet<>();
        occurrences.forEach(v -> digests.add(object(v).get("semanticVariantDigest")));
        assertEquals(2, digests.size());
        assertTrue(Files.isDirectory(first.registryPath()));
        assertTrue(Files.isDirectory(second.registryPath()));

        Map<String,Object> rebuilt = registry.rebuildAndPersist();
        assertEquals(Json.canonical(before), Json.canonical(rebuilt), "variant index rebuild must be deterministic");
        assertTrue(registry.verify().passed());
        Map<String,Object> context = registry.discoveryContext("fixture:material:granite", 5000);
        Map<String,Object> searchHit = object(array(context.get("matches")).getFirst());
        Map<String,Object> discoveryIdentity = object(searchHit.get("identity"));
        assertEquals(SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS, discoveryIdentity.get("semanticVariantStatus"));
        assertEquals(2, array(discoveryIdentity.get("occurrences")).size());
    }

    private PipelineResult manufacture(String seed, String fixture, String suffix) {
        return manufacture(seed, FIXTURES.resolve(fixture), suffix, "test-sha");
    }

    private PipelineResult manufacture(String seed, String fixture, String suffix, String repositoryCommit) {
        return manufacture(seed, FIXTURES.resolve(fixture), suffix, repositoryCommit);
    }

    private PipelineResult manufacture(String seed, Path fixture, String suffix, String repositoryCommit) {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FixtureProvider provider = new FixtureProvider(fixture, SCHEMAS);
        return new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), repositoryCommit)
                .manufacture(request, provider, false);
    }

    private Map<String,Object> identityByResolutionKey(Map<String,Object> index, String key) {
        return array(index.get("identities")).stream().map(FoundryRegistryTest::object)
                .filter(v -> key.equals(v.get("resolutionKey"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
