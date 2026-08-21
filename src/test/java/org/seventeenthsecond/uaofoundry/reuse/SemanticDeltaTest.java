package org.seventeenthsecond.uaofoundry.reuse;

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
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticDeltaTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @TempDir Path temp;

    @Test
    void registryAwareManufactureReportsStableIdentityReuseAndRegistryEvidence() throws Exception {
        Path registryRoot = temp.resolve("registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        PipelineResult prior = manufactureFixture("cow", "biological-cow.json", "prior");
        FoundryRegistry.RegistrationResult priorRegistration = registry.register(prior.packagePath());

        Path bundle = temp.resolve("registry-cow-bundle.json");
        Map<String,Object> providerBundle = object(FileOps.readJson(FIXTURES.resolve("biological-cow.json")));
        List<Object> sources = array(providerBundle.get("sources"));
        Map<String,Object> firstSource = object(sources.getFirst());
        firstSource.put("locator", "registry://" + priorRegistration.packageId() + "/source-corpus/" + firstSource.get("sourceId") + ".txt");
        FileOps.writeJson(bundle, providerBundle);

        Path capture = temp.resolve("provider-input.json");
        RunResult run = registryManufacture("cow", providerScript(bundle, capture), registryRoot, temp.resolve("work-reuse"), temp.resolve("dist-reuse"), true);
        assertEquals(0, run.exit(), run.stderr());
        Map<String,Object> response = object(Json.parse(run.stdout()));
        Map<String,Object> report = object(response.get("reuse"));
        Map<String,Object> counts = object(report.get("counts"));
        assertEquals(java.math.BigDecimal.valueOf(2), counts.get("reusedUaoCount"));
        assertEquals(java.math.BigDecimal.ZERO, counts.get("newUaoCount"));
        assertEquals(java.math.BigDecimal.ONE, counts.get("registrySourceCount"));
        assertEquals(java.math.BigDecimal.ZERO, counts.get("newSourceCount"));
        Path packagePath = Path.of((String) response.get("packagePath"));
        // ADR-0006 / finding P9-1: reuse evidence is recorded beside the registry, not inside the
        // content-addressed package. The evidence must still exist and still be inspectable -- the
        // assertion moved location, not intent.
        assertFalse(Files.isRegularFile(packagePath.resolve("reuse-report.json")),
                "volatile run evidence must not live inside an immutable package");
        Path runStore = Path.of((String) response.get("runStore"));
        Path runFile = runStore.resolve(response.get("runId") + ".json");
        assertTrue(Files.isRegularFile(runFile), "reuse evidence must be recorded as run evidence");
        assertEquals(Json.canonical(report),
                Json.canonical(object(object(FileOps.readJson(runFile)).get("reuseReport"))),
                "the recorded reuse report must be the one the analyzer computed");
        assertTrue(new PackageVerifier(SCHEMAS).verify(packagePath).passed());
        assertTrue(registry.verify().passed());
        Map<String,Object> providerInput = object(FileOps.readJson(capture));
        assertFalse(array(object(providerInput.get("registryContext")).get("matches")).isEmpty());
        assertEquals("REUSE_VERIFIED_REGISTRY_IDENTITIES_BEFORE_NEW_ACQUISITION", object(providerInput.get("constraints")).get("reusePreference"));
    }

    @Test
    void registryContextParticipatesInDeterministicTransactionIdentity() throws Exception {
        Path bundle = FIXTURES.resolve("biological-cow.json");
        RunResult empty = registryManufacture("cow", providerScript(bundle, temp.resolve("input-empty.json")), temp.resolve("empty-registry"), temp.resolve("work-empty"), temp.resolve("dist-empty"), false);
        assertEquals(0, empty.exit(), empty.stderr());
        String emptyJob = (String) object(Json.parse(empty.stdout())).get("jobId");

        Path populatedRegistry = temp.resolve("populated-registry");
        FoundryRegistry registry = new FoundryRegistry(populatedRegistry, SCHEMAS);
        registry.register(manufactureFixture("cow", "biological-cow.json", "seed-registry").packagePath());
        RunResult populated = registryManufacture("cow", providerScript(bundle, temp.resolve("input-populated.json")), populatedRegistry, temp.resolve("work-populated"), temp.resolve("dist-populated"), false);
        assertEquals(0, populated.exit(), populated.stderr());
        assertNotEquals(emptyJob, object(Json.parse(populated.stdout())).get("jobId"));
    }

    @Test
    void registryLocatorCannotClaimReuseWhenEvidenceBytesDiffer() throws Exception {
        Path registryRoot = temp.resolve("evidence-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        FoundryRegistry.RegistrationResult prior = registry.register(manufactureFixture("cow", "biological-cow.json", "evidence-seed").packagePath());
        Map<String,Object> providerBundle = object(FileOps.readJson(FIXTURES.resolve("biological-cow.json")));
        Map<String,Object> firstSource = object(array(providerBundle.get("sources")).getFirst());
        firstSource.put("locator", "registry://" + prior.packageId() + "/source-corpus/" + firstSource.get("sourceId") + ".txt");
        firstSource.put("content", "different bytes pretending to be registry reuse");
        Path bundle = temp.resolve("false-reuse.json");
        FileOps.writeJson(bundle, providerBundle);
        RunResult result = registryManufacture("cow", providerScript(bundle, temp.resolve("false-reuse-input.json")), registryRoot, temp.resolve("false-reuse-work"), temp.resolve("false-reuse-dist"), false);
        assertEquals(0, result.exit(), result.stderr());
        Map<String,Object> response = object(Json.parse(result.stdout()));
        Map<String,Object> reuse = object(response.get("reuse"));
        Map<String,Object> counts = object(reuse.get("counts"));
        assertEquals(new java.math.BigDecimal("1"), counts.get("registrySourceCount"));
        Path packagePath = Path.of((String) response.get("packagePath"));
        String sourceId = (String) firstSource.get("sourceId");
        assertEquals(
                Files.readString(prior.registryPath().resolve("source-corpus").resolve(sourceId + ".txt")),
                Files.readString(packagePath.resolve("source-corpus").resolve(sourceId + ".txt")));
        assertFalse(Files.readString(packagePath.resolve("source-corpus").resolve(sourceId + ".txt")).contains("different bytes pretending"));
    }

    @Test
    void tamperedRegistryFailsBeforeProviderAcquisition() throws Exception {
        Path registryRoot = temp.resolve("tampered-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        FoundryRegistry.RegistrationResult registered = registry.register(manufactureFixture("granite", "material-granite.json", "tamper-seed").packagePath());
        Files.writeString(registered.registryPath().resolve("manifest.json"), "\n ", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        Path capture = temp.resolve("must-not-exist.json");
        RunResult result = registryManufacture("granite", providerScript(FIXTURES.resolve("material-granite.json"), capture), registryRoot, temp.resolve("work-tamper"), temp.resolve("dist-tamper"), false);
        assertEquals(2, result.exit());
        assertTrue(result.stderr().contains("Registry verification failed before manufacture"));
        assertFalse(Files.exists(capture));
    }

    @Test
    void automaticDiscoveryRefusesMultipleUnreconciledVariantsWithoutRegistryMutation() throws Exception {
        Path registryRoot = temp.resolve("unreconciled-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        registry.register(manufactureFixture("granite", "material-granite.json", "unreconciled-original").packagePath());
        Path variantBundle = semanticVariantBundle("Unreconciled alternate statement for the same stable granite identity.", "unreconciled");
        registry.register(manufactureFixture("granite", variantBundle, "unreconciled-variant").packagePath());
        String before = FileOps.treeHash(registryRoot);

        Path capture = temp.resolve("unreconciled-provider-must-not-run.json");
        RunResult result = registryManufacture("granite", providerScript(FIXTURES.resolve("material-granite.json"), capture),
                registryRoot, temp.resolve("unreconciled-work"), temp.resolve("unreconciled-dist"), false);
        assertEquals(2, result.exit());
        assertTrue(result.stderr().contains("MULTIPLE_UNRECONCILED_VARIANTS"), result.stderr());
        assertFalse(Files.exists(capture), "ambiguous matched identity must be refused before provider acquisition");
        assertEquals(before, FileOps.treeHash(registryRoot), "failed automatic reuse must not mutate the registry");
        assertTrue(registry.verify().passed());
    }

    @Test
    void automaticReuseRefusesAFirstDifferingSemanticVariantWithoutCountingItAsReused() throws Exception {
        Path registryRoot = temp.resolve("variant-divergence-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        registry.register(manufactureFixture("granite", "material-granite.json", "variant-divergence-original").packagePath());
        String before = FileOps.treeHash(registryRoot);
        Path variantBundle = semanticVariantBundle("A newly encountered divergent statement for the stable granite identity.", "divergence");
        Path capture = temp.resolve("variant-divergence-provider-input.json");

        RunResult result = registryManufacture("granite", providerScript(variantBundle, capture), registryRoot,
                temp.resolve("variant-divergence-work"), temp.resolve("variant-divergence-dist"), true);
        assertEquals(2, result.exit());
        assertTrue(result.stderr().contains("SEMANTIC_VARIANT_DIVERGENCE"), result.stderr());
        assertTrue(Files.isRegularFile(capture), "provider may create a new immutable candidate package before divergence is known");
        assertTrue(result.stdout().isBlank(), "divergent identity must not be reported as safely reused");
        assertEquals(before, FileOps.treeHash(registryRoot), "failed reuse and requested registration must leave registry unchanged");
        assertTrue(registry.verify().passed());
    }

    @Test
    void reuseAnalyzerDirectlyRejectsRegistryEvidenceHashMismatch() {
        Path registryRoot = temp.resolve("analyzer-hash-registry");
        FoundryRegistry registry = new FoundryRegistry(registryRoot, SCHEMAS);
        FoundryRegistry.RegistrationResult prior = registry.register(
                manufactureFixture("cow", "biological-cow.json", "analyzer-hash-prior").packagePath());
        PipelineResult candidate = manufactureFixture("cow", "biological-cow.json", "analyzer-hash-candidate");
        Map<String,Object> sourceRegistry = object(FileOps.readJson(candidate.packagePath().resolve("source-registry.json")));
        Map<String,Object> source = object(array(sourceRegistry.get("sources")).getFirst());
        String sourceId = (String) source.get("sourceId");
        source.put("locator", "registry://" + prior.packageId() + "/source-corpus/" + sourceId + ".txt");
        source.put("sha256", "0".repeat(64));
        FileOps.writeJson(candidate.packagePath().resolve("source-registry.json"), sourceRegistry);

        Map<String,Object> index = registry.index();
        String contextHash = Hashes.canonicalJson(registry.discoveryContext("cow", 5000));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ReuseAnalyzer(SCHEMAS).analyze(index, registryRoot, candidate.packagePath(), contextHash));
        assertTrue(ex.getMessage().contains("Registry evidence hash mismatch"), ex.getMessage());
    }

    private PipelineResult manufactureFixture(String seed, String fixture, String suffix) {
        return manufactureFixture(seed, FIXTURES.resolve(fixture), suffix);
    }

    private PipelineResult manufactureFixture(String seed, Path fixture, String suffix) {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FixtureProvider provider = new FixtureProvider(fixture, SCHEMAS);
        return new FoundryPipeline(SCHEMAS, temp.resolve("fixture-work-" + suffix), temp.resolve("fixture-dist-" + suffix), "test-sha").manufacture(request, provider, false);
    }

    private Path semanticVariantBundle(String statement, String suffix) {
        Map<String,Object> bundle = object(FileOps.readJson(FIXTURES.resolve("material-granite.json")));
        Map<String,Object> candidates = object(bundle.get("candidates"));
        object(array(candidates.get("claims")).getFirst()).put("statement", statement);
        Path path = temp.resolve("semantic-variant-" + suffix + ".json");
        FileOps.writeJson(path, bundle);
        return path;
    }

    private RunResult registryManufacture(String seed, Path command, Path registry, Path work, Path dist, boolean register) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        RegistryManufactureApplication app = new RegistryManufactureApplication(new PrintStream(stdout), new PrintStream(stderr));
        java.util.ArrayList<String> args = new java.util.ArrayList<>(List.of(seed, "--provider-command", command.toString(), "--provider-timeout-seconds", "10", "--registry", registry.toString(), "--work-dir", work.toString(), "--dist-dir", dist.toString(), "--repository-commit", "test-sha"));
        if (register) args.add("--register");
        int exit = app.run(args.toArray(String[]::new));
        return new RunResult(exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private Path providerScript(Path bundle, Path capture) throws Exception {
        Path command = temp.resolve("provider-" + Math.abs(capture.hashCode()) + ".sh");
        String safeBundle = bundle.toAbsolutePath().normalize().toString().replace("'", "'\"'\"'");
        String safeCapture = capture.toAbsolutePath().normalize().toString().replace("'", "'\"'\"'");
        Files.writeString(command, "#!/usr/bin/env sh\nset -eu\ncat > '" + safeCapture + "'\ncat '" + safeBundle + "'\n", StandardCharsets.UTF_8);
        assertTrue(command.toFile().setExecutable(true));
        return command;
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
    private record RunResult(int exit, String stdout, String stderr) {}
}
