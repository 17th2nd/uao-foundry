package org.seventeenthsecond.uaofoundry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FoundryApplicationTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @TempDir Path temp;

    @Test
    void sameCompiledPipelineCompletesThreeStructurallyDifferentDomains() {
        record Case(String seed, String fixture) {}
        List<Case> cases = List.of(
                new Case("cow", "biological-cow.json"),
                new Case("granite", "material-granite.json"),
                new Case("pie", "cultural-pie.json")
        );
        Set<String> rootIds = new HashSet<>();
        Set<String> planHashes = new HashSet<>();
        Set<Integer> identityCounts = new HashSet<>();

        for (Case item : cases) {
            Path work = temp.resolve("work-" + item.seed());
            Path dist = temp.resolve("dist-" + item.seed());
            PipelineResult result = run(item.seed(), item.fixture(), work, dist, false);
            assertEquals("EXPERIMENTAL", result.publicationStatus());
            assertTrue(result.verificationPassed());
            assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
            rootIds.add(result.rootUaoId());

            Object plan = FileOps.readJson(result.packagePath().resolve("manufacturing-plan.json"));
            planHashes.add(org.seventeenthsecond.uaofoundry.util.Hashes.canonicalJson(plan));
            Object canonical = FileOps.readJson(result.packagePath().resolve("canonical-identities.json"));
            identityCounts.add(Json.array(canonical, "canonical identities").size());
        }

        assertEquals(3, rootIds.size(), "cross-domain roots must be distinct");
        assertEquals(3, planHashes.size(), "plans must differ without production code changes");
        assertTrue(identityCounts.size() >= 2, "fixtures should exercise structurally different identity sets");
    }

    @Test
    void repeatedFixtureManufactureIsByteDeterministicAndResumeReusesStages() {
        Path work = temp.resolve("work");
        Path dist = temp.resolve("dist");
        PipelineResult first = run("cow", "biological-cow.json", work, dist, false);
        String firstHash = FileOps.treeHash(first.packagePath());

        PipelineResult second = run("cow", "biological-cow.json", work, dist, false);
        String secondHash = FileOps.treeHash(second.packagePath());
        assertEquals(firstHash, secondHash);

        PipelineResult resumed = run("cow", "biological-cow.json", work, dist, true);
        assertTrue(resumed.resumedStages() >= 10, "resume must reuse verified stage checkpoints");
        assertEquals(firstHash, FileOps.treeHash(resumed.packagePath()));
    }

    @Test
    void tamperedSourceSnapshotFailsPackageVerification() throws Exception {
        PipelineResult result = run("granite", "material-granite.json", temp.resolve("work"), temp.resolve("dist"), false);
        Path snapshot;
        try (var stream = Files.list(result.packagePath().resolve("source-corpus"))) {
            snapshot = stream.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        Files.writeString(snapshot, "\nTAMPER\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("Checksum mismatch")));
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("Source snapshot content hash mismatch")));
    }

    @Test
    void requestContractRejectsUnknownFieldsAndStableIdIsDeterministic() {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest first = loader.fromSeed("cow", "en", "experimental");
        ManufacturingRequest second = loader.fromSeed("cow", "en", "experimental");
        assertEquals(first.requestId(), second.requestId());
        assertThrows(IllegalArgumentException.class, () -> loader.fromObject(Map.of("identitySeed", "cow", "invented", "no")));
    }

    @Test
    void jsonParserRejectsDuplicateObjectKeys() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertTrue(ex.getMessage().contains("Duplicate JSON object key"));
    }

    @Test
    void directManufactureWithoutProviderFailsClosed() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        FoundryApplication app = new FoundryApplication(new PrintStream(stdout), new PrintStream(stderr));
        int exit = app.run(new String[]{"manufacture", "cow"});
        assertEquals(2, exit);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("requires a provider"));
    }

    @Test
    void nonEmptyRelationshipCandidateIsNotPublishedWithoutTypeRoleAuthority() {
        Path fixture = temp.resolve("relationship-fixture.json");
        @SuppressWarnings("unchecked") Map<String,Object> bundle = (Map<String,Object>) FileOps.readJson(FIXTURES.resolve("biological-cow.json"));
        Map<String,Object> candidates = Json.object(bundle.get("candidates"), "candidates");
        List<Object> relationships = new ArrayList<>();
        relationships.add(Map.of(
                "candidateId", "rel-unresolved",
                "typeVersion", "asa.core/example@1",
                "participants", List.of(Map.of("role", "subject", "candidateIdentityRef", "cid-root")),
                "identityLiterals", Map.of("statement", "fixture relationship"),
                "contextualBindings", List.of(),
                "sourceRefs", List.of("src-cow-bio")
        ));
        candidates.put("relationships", relationships);
        FileOps.writeJson(fixture, bundle);

        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        FixtureProvider provider = new FixtureProvider(fixture, SCHEMAS);
        PipelineResult result = new FoundryPipeline(SCHEMAS, temp.resolve("work-rel"), temp.resolve("dist-rel"), "test-sha")
                .manufacture(request, provider, false);
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        assertTrue(result.verificationPassed(), "canonical output may remain structurally valid while relationship candidate is excluded");
        List<Object> unresolved = Json.array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json")), "unresolved");
        assertFalse(unresolved.isEmpty());
        assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", Json.object(unresolved.getFirst(), "unresolved item").get("code"));
    }

    @Test
    void productionJavaContainsNoDemonstrationIdentityKnowledge() throws Exception {
        List<String> forbiddenDemoTerms = List.of("cow", "granite", "pie", "hydrogen", "rock");
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                for (String term : forbiddenDemoTerms) {
                    assertFalse(text.matches("(?s).*\\b" + term + "\\b.*"), () -> "production source embeds demonstration term '" + term + "' in " + path);
                }
            }
        }
    }

    private PipelineResult run(String seed, String fixture, Path work, Path dist, boolean resume) {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FixtureProvider provider = new FixtureProvider(FIXTURES.resolve(fixture), SCHEMAS);
        return new FoundryPipeline(SCHEMAS, work, dist, "test-sha").manufacture(request, provider, resume);
    }
}
