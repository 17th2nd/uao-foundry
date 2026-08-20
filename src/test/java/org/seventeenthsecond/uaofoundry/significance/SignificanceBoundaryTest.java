package org.seventeenthsecond.uaofoundry.significance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 regression tests for the significance boundary and the A_x / R_x supply interface.
 *
 * <p>Two obligations. Significance must never become durable identity state — the negative half.
 * And the durable inputs a significance engine needs must actually be obtainable — the positive
 * half, which is what makes the negative half a boundary rather than a refusal.
 */
class SignificanceBoundaryTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path COW = Path.of("src/test/resources/fixtures/biological-cow.json");
    private static final String AT = "2026-08-20T00:00:00Z";

    @TempDir Path temp;
    private Path registryRoot;

    // ---------------------------------------------------------------- the negative half

    @Test
    void noManufacturedPackageCarriesASignificanceBearingField() {
        PipelineResult result = manufacture("clean", fixture -> {});
        List<String> found = new ArrayList<>();
        for (String file : List.of("manufactured-package.json", "canonical-identities.json",
                "identity-resolution.json", "publication-decision.json")) {
            SignificanceBoundary.collect(FileOps.readJson(result.packagePath().resolve(file)), "$", found);
        }
        assertTrue(found.isEmpty(), "manufactured output must be free of significance material: " + found);
    }

    @Test
    void everyProhibitedFieldNameIsActuallyDetected() {
        // Guards against the prohibition quietly becoming a shorter list than it claims.
        List<String> all = new ArrayList<>();
        all.addAll(SignificanceBoundary.ASA_FORBIDDEN);
        all.addAll(SignificanceBoundary.FOUNDRY_FORBIDDEN);
        assertEquals(12, all.size(), "the prohibited set must not silently shrink");

        for (String field : all) {
            List<String> errors = new ArrayList<>();
            SignificanceBoundary.collect(Map.of("internal_state", Map.of(field, "x")), "$", errors);
            assertEquals(1, errors.size(), "not detected at depth: " + field);
        }
    }

    @Test
    void aSignificanceFieldSmuggledIntoAPackageFailsVerification() {
        PipelineResult result = manufacture("smuggle", fixture -> {});
        Path path = result.packagePath().resolve("manufactured-package.json");
        Map<String,Object> manufactured = object(FileOps.readJson(path));
        object(array(manufactured.get("uaos")).getFirst()).put("attention_weight", "0.91");
        FileOps.writeJson(path, manufactured);

        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("does not store significance")),
                verification.errors().toString());
    }

    @Test
    void theTwoProhibitionTiersAreReportedDistinctly() {
        // ADR-0002's four reflect current ASA authority; the rest are Foundry-local tightening.
        // Conflating them would let a Foundry choice be mistaken for ASA authority.
        List<String> asa = new ArrayList<>();
        SignificanceBoundary.collect(Map.of("significance_value", "1"), "$", asa);
        assertTrue(asa.getFirst().contains("Forbidden ASA field"), asa.toString());

        List<String> foundry = new ArrayList<>();
        SignificanceBoundary.collect(Map.of("reasoning_tier", "1"), "$", foundry);
        assertTrue(foundry.getFirst().contains("Forbidden significance field"), foundry.toString());
    }

    @Test
    void neitherKernelDigestIsASignificanceValue() {
        // The digests are content addresses over disjoint projections. If either carried ordering
        // meaning it would be a ranking in disguise, so they must be insensitive to magnitude.
        PipelineResult result = manufacture("digest", fixture -> {});
        Map<String,Object> kernel = object(object(object(array(FileOps.readJson(
                result.packagePath().resolve("canonical-identities.json"))).getFirst())
                .get("internal_state")).get("foundry_identity"));

        assertTrue(String.valueOf(kernel.get("identity_digest")).matches("[a-f0-9]{64}"));
        assertTrue(String.valueOf(kernel.get("state_version")).matches("[a-f0-9]{64}"));
        List<String> errors = new ArrayList<>();
        SignificanceBoundary.collect(kernel, "$", errors);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    // ---------------------------------------------------------------- the positive half

    @Test
    void theExportSuppliesDurableInputsAndNamesWhatItDoesNotOwn() {
        FoundryRegistry registry = registryWith(manufacture("export", fixture -> {}));
        Map<String,Object> inputs = registry.significanceInputs(IdentityReference.uid(rootUid("export")));

        assertEquals(SignificanceInputs.INTERFACE_VERSION, inputs.get("significanceInterfaceVersion"));
        assertEquals("RESEARCH_CANDIDATE_NOT_RATIFIED_BY_ASA", inputs.get("formulationStatus"),
                "a consumer must know it is binding to an unratified formulation");

        Map<String,Object> ax = object(inputs.get("A_x"));
        assertEquals(rootUid("export"), object(ax.get("identity")).get("uid"));
        assertEquals("biology", object(ax.get("identity")).get("semanticType"));
        assertFalse(array(ax.get("assertions")).isEmpty(), "A_x must carry the durable attributes");
        assertEquals("DEFERRED_ON_RECORD", ax.get("assertionEpistemicStatus"),
                "assertions are recorded statements, not established truths");
        assertFalse(array(object(ax.get("provenance")).get("occurrences")).isEmpty());

        Map<String,Object> notSupplied = object(inputs.get("notSupplied"));
        assertTrue(object(notSupplied.get("runtimeOwned")).containsKey("C_q"));
        assertTrue(object(notSupplied.get("runtimeOwned")).containsKey("e"));
        assertTrue(object(notSupplied.get("significanceEngineOwned")).containsKey("S_v"));
        assertTrue(object(notSupplied.get("significanceEngineOwned")).containsKey("Plan"));
    }

    @Test
    void theExportComputesNoSignificanceOfItsOwn() {
        FoundryRegistry registry = registryWith(manufacture("nocompute", fixture -> {}));
        Map<String,Object> inputs = registry.significanceInputs(IdentityReference.uid(rootUid("nocompute")));

        List<String> errors = new ArrayList<>();
        SignificanceBoundary.collect(inputs, "$", errors);
        assertTrue(errors.isEmpty(), "the supply surface must not itself emit significance: " + errors);

        // The supplied halves carry no engine output. The notSupplied block is excluded on
        // purpose: naming R_v, S_v, Plan and Schedule as things it does not own is the opposite of
        // supplying them, and is the only place those names may appear.
        String supplied = Json.canonical(inputs.get("A_x")) + Json.canonical(inputs.get("R_x"));
        for (String member : List.of("\"R_v\"", "\"S_v\"", "\"Plan\"", "\"Schedule\"")) {
            assertFalse(supplied.contains(member), "supply surface must not carry engine output: " + member);
        }
        assertTrue(Json.canonical(inputs.get("notSupplied")).contains("\"S_v\""),
                "and must name what it does not own");
    }

    @Test
    void anEmptyRelationshipSetIsReportedProminentlyRatherThanQuietly() {
        FoundryRegistry registry = registryWith(manufacture("rx", fixture -> {}));
        Map<String,Object> rx = object(registry.significanceInputs(IdentityReference.uid(rootUid("rx"))).get("R_x"));

        assertTrue(array(rx.get("canonicalRelationships")).isEmpty());
        assertEquals(Boolean.FALSE, rx.get("complete"),
                "a significance architecture that depends on relationships must be told its input is incomplete");
        assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", rx.get("authorityStatus"));
        assertEquals("17th2nd/ASA#29", rx.get("blockedBy"));
        assertTrue(String.valueOf(rx.get("consequence")).contains("in isolation"));
    }

    @Test
    void theExportRefusesAnIdentityWhoseMeaningIsInDispute() {
        PipelineResult t0 = manufacture("dispute-t0", fixture -> {});
        PipelineResult t1 = manufacture("dispute-t1", fixture ->
                claim(fixture, "clm-root-scope").put("statement", "Fixture assertion: divergent."));
        FoundryRegistry registry = registryWith(t0, t1);

        // Refused at resolution, before any input is assembled: an identity whose meaning is in
        // dispute never resolves, so the export is never reached.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.significanceInputs(IdentityReference.uid(rootUid("dispute-t0"))));
        assertTrue(failure.getMessage().contains("SEMANTIC_VARIANTS_UNRECONCILED"), failure.getMessage());
    }

    @Test
    void theExportItselfAlsoRefusesUnreconciledVariantsIndependentlyOfResolution() {
        // Defence in depth: the registry path refuses at resolution, so this guard is unreachable
        // from there. Exercised directly so a future caller that skips resolution cannot bypass it.
        Map<String,Object> disputed = Map.of(
                "uid", "uao-000000000000",
                "semanticVariantStatus", "MULTIPLE_UNRECONCILED_VARIANTS",
                "lifecycleState", IdentityOperation.ACTIVE);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                SignificanceInputs.export(disputed, List.of()));
        assertTrue(failure.getMessage().contains("MULTIPLE_UNRECONCILED_VARIANTS"), failure.getMessage());
    }

    @Test
    void theExportRefusesAnIdentityThatIsNoLongerStanding() {
        FoundryRegistry registry = registryWith(manufacture("retired", fixture -> {}));
        registry.applyIdentityOperation(IdentityOperation.create(
                IdentityOperation.Kind.RETIRE, List.of(rootUid("retired")), List.of(),
                List.of("WITHDRAWN"), "Withdrawn.", List.of(), null, AT));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.significanceInputs(IdentityReference.uid(rootUid("retired"))));
        assertTrue(failure.getMessage().contains("IDENTITY_RETIRED"), failure.getMessage());

        // And directly, for the same defence-in-depth reason.
        Map<String,Object> retired = Map.of(
                "uid", "uao-000000000000",
                "semanticVariantStatus", "SINGLE_VARIANT",
                "lifecycleState", IdentityOperation.RETIRED);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SignificanceInputs.export(retired, List.of()))
                .getMessage().contains("IDENTITY_LIFECYCLE_NOT_ACTIVE"));
    }

    @Test
    void theExportRefusesAnythingShortOfAnExactlyResolvedIdentity() {
        FoundryRegistry registry = registryWith(manufacture("byalias", fixture -> {}));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                registry.significanceInputs(IdentityReference.alias("cow")));
        assertTrue(failure.getMessage().contains("exactly resolved"), failure.getMessage());
    }

    // ---------------------------------------------------------------- helpers

    private final Map<String,PipelineResult> results = new java.util.LinkedHashMap<>();

    private PipelineResult manufacture(String suffix, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(COW)));
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

    private static Map<String,Object> claim(Map<String,Object> fixture, String candidateId) {
        return array(object(fixture.get("candidates")).get("claims")).stream()
                .map(SignificanceBoundaryTest::object)
                .filter(v -> candidateId.equals(v.get("candidateId"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
