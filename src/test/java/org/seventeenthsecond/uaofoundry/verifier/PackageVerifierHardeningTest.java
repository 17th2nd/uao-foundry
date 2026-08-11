package org.seventeenthsecond.uaofoundry.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageVerifierHardeningTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/biological-cow.json");

    @TempDir Path temp;

    @Test
    void rehashedCrossFileSemanticDivergenceFailsVerification() throws Exception {
        Path packageDir = manufacture("cow", "cross-file");
        List<Object> identities = Json.array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities");
        Map<String,Object> identity = Json.object(identities.getFirst(), "identity");
        Map<String,Object> internal = Json.object(identity.get("internal_state"), "internal_state");
        Map<String,Object> foundry = Json.object(internal.get("foundry_identity"), "foundry_identity");
        foundry.put("canonical_label", foundry.get("canonical_label") + " forged");
        FileOps.writeJson(packageDir.resolve("canonical-identities.json"), identities);
        rewriteChecksums(packageDir);

        PackageVerifier.Result result = new PackageVerifier(SCHEMAS).verify(packageDir);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("manufactured-package.uaos differs")), result.errors().toString());
        assertFalse(result.errors().stream().anyMatch(e -> e.startsWith("Checksum mismatch")), "negative control must prove failure after valid checksum rewrite");
    }

    @Test
    void rehashedForgedUaoIdFailsDeterministicIdentityDerivation() throws Exception {
        Path packageDir = manufacture("cow", "uid-derivation");
        List<Object> canonical = Json.array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities");
        Map<String,Object> target = Json.object(canonical.getFirst(), "target UAO");
        String oldUid = (String) target.get("uid");
        String forgedUid = "uao-000000000000".equals(oldUid) ? "uao-111111111111" : "uao-000000000000";
        target.put("uid", forgedUid);
        FileOps.writeJson(packageDir.resolve("canonical-identities.json"), canonical);

        Map<String,Object> manufactured = Json.object(FileOps.readJson(packageDir.resolve("manufactured-package.json")), "manufactured package");
        List<Object> manufacturedUaos = Json.array(manufactured.get("uaos"), "manufactured uaos");
        for (Object raw : manufacturedUaos) {
            Map<String,Object> uao = Json.object(raw, "manufactured UAO");
            if (oldUid.equals(uao.get("uid"))) uao.put("uid", forgedUid);
        }
        if (oldUid.equals(manufactured.get("rootUaoId"))) manufactured.put("rootUaoId", forgedUid);
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);

        Map<String,Object> manifest = Json.object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        if (oldUid.equals(manifest.get("rootUaoId"))) manifest.put("rootUaoId", forgedUid);
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        rewriteChecksums(packageDir);

        PackageVerifier.Result result = new PackageVerifier(SCHEMAS).verify(packageDir);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("deterministic resolution_key derivation")), result.errors().toString());
        assertFalse(result.errors().stream().anyMatch(e -> e.startsWith("Checksum mismatch")), "negative control must prove failure after valid checksum rewrite");
    }


    @Test
    void rehashedConsistentAssertionForgeryFailsSemanticReconstruction() throws Exception {
        Path packageDir = manufacture("cow", "consistent-forgery");
        String forged = "FORGED SAFETY ASSERTION THAT DOES NOT EXIST IN THE PROVIDER CANDIDATE SET";
        List<Object> canonical = Json.array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities");
        Map<String,Object> first = Json.object(canonical.getFirst(), "uao");
        Json.object(Json.array(first.get("assertions"), "assertions").getFirst(), "assertion").put("statement", forged);
        FileOps.writeJson(packageDir.resolve("canonical-identities.json"), canonical);
        Map<String,Object> manufactured = Json.object(FileOps.readJson(packageDir.resolve("manufactured-package.json")), "manufactured");
        List<Object> manufacturedUaos = Json.array(manufactured.get("uaos"), "uaos");
        Json.object(Json.array(Json.object(manufacturedUaos.getFirst(), "uao").get("assertions"), "assertions").getFirst(), "assertion").put("statement", forged);
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);
        rewriteChecksums(packageDir);

        PackageVerifier.Result result = new PackageVerifier(SCHEMAS).verify(packageDir);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("contentDigest") || e.contains("Canonical assertions")), result.errors().toString());
    }

    @Test
    void relationshipIncompletePackageCannotBeRehashedIntoExperimental() throws Exception {
        Path relationshipFixture = Path.of("src/test/resources/fixtures/relationship-bearing-cow.json");
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        PipelineResult produced = new FoundryPipeline(SCHEMAS, temp.resolve("work-elevation"), temp.resolve("dist-elevation"), "hardening-test-sha")
                .manufacture(request, new FixtureProvider(relationshipFixture, SCHEMAS), false);
        assertTrue("EVIDENCE_INCOMPLETE".equals(produced.publicationStatus()));
        Path packageDir = produced.packagePath();

        Map<String,Object> decision = Json.object(FileOps.readJson(packageDir.resolve("publication-decision.json")), "decision");
        decision.put("status", "EXPERIMENTAL"); decision.put("eligible", true);
        FileOps.writeJson(packageDir.resolve("publication-decision.json"), decision);
        Map<String,Object> manufactured = Json.object(FileOps.readJson(packageDir.resolve("manufactured-package.json")), "manufactured");
        manufactured.put("publicationDecision", decision);
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);
        Map<String,Object> manifest = Json.object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        manifest.put("publicationStatus", "EXPERIMENTAL");
        String contentDigest = PackageContentDigest.compute(packageDir);
        manifest.put("contentDigest", contentDigest);
        manifest.put("packageId", StableIdentifiers.forText("pkg", 16, contentDigest));
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        rewriteChecksums(packageDir);

        PackageVerifier.Result result = new PackageVerifier(SCHEMAS).verify(packageDir);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Publication decision cannot be reconstructed")), result.errors().toString());
    }

    @Test
    void providerClaimForgeryFailsExactProjectionReconciliationAfterFullReaddressing() throws Exception {
        Path packageDir = manufactureFixture("granite", "material-granite.json", "provider-claim-forgery");
        String forged = "FORGED PROVIDER-ORIGINATED CLAIM";
        List<Object> claims = Json.array(FileOps.readJson(packageDir.resolve("candidate-claims.json")), "claims");
        Map<String,Object> claim = Json.object(claims.getFirst(), "claim");
        String candidateId = (String) claim.get("candidateId");
        String original = (String) claim.put("statement", forged);
        FileOps.writeJson(packageDir.resolve("candidate-claims.json"), claims);

        replaceAssertion(packageDir.resolve("canonical-identities.json"), original, forged);
        replaceAssertionInManufacturedPackage(packageDir, original, forged);
        List<Object> ledger = Json.array(FileOps.readJson(packageDir.resolve("provenance-ledger.json")), "ledger");
        ledger.stream().map(v -> Json.object(v, "ledger entry"))
                .filter(v -> candidateId.equals(v.get("candidateId"))).forEach(v -> v.put("statement", forged));
        FileOps.writeJson(packageDir.resolve("provenance-ledger.json"), ledger);
        refreshContentAddressAndChecksums(packageDir);

        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(packageDir), "claims");
    }

    @Test
    void providerRelationshipCannotDisappearAndBeElevatedToExperimental() throws Exception {
        Path packageDir = manufactureFixture("cow", "relationship-bearing-cow.json", "provider-relationship-hiding");
        FileOps.writeJson(packageDir.resolve("candidate-relationships.json"), List.of());
        FileOps.writeJson(packageDir.resolve("unresolved-items.json"), List.of());
        Map<String,Object> decision = Json.object(FileOps.readJson(packageDir.resolve("publication-decision.json")), "decision");
        decision.put("status", "EXPERIMENTAL");
        decision.put("eligible", true);
        decision.put("reasons", List.of("FORGED ELEVATION"));
        FileOps.writeJson(packageDir.resolve("publication-decision.json"), decision);
        Map<String,Object> manufactured = Json.object(FileOps.readJson(packageDir.resolve("manufactured-package.json")), "manufactured");
        manufactured.put("publicationDecision", decision);
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);
        Map<String,Object> manifest = Json.object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        manifest.put("publicationStatus", "EXPERIMENTAL");
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        refreshContentAddressAndChecksums(packageDir);

        PackageVerifier.Result result = new PackageVerifier(SCHEMAS).verify(packageDir);
        assertProjectionReconciliationFailure(result, "relationships");
        assertFalse(result.errors().stream().anyMatch(v -> v.startsWith("Checksum mismatch")), result.errors().toString());
    }

    @Test
    void providerCandidateOmissionAndInjectionFailReconciliation() throws Exception {
        Path omitted = manufactureFixture("granite", "material-granite.json", "provider-omission");
        List<Object> omittedEvidence = Json.array(FileOps.readJson(omitted.resolve("candidate-evidence.json")), "evidence");
        omittedEvidence.removeLast();
        FileOps.writeJson(omitted.resolve("candidate-evidence.json"), omittedEvidence);
        refreshContentAddressAndChecksums(omitted);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(omitted), "evidence");

        Path injected = manufactureFixture("granite", "material-granite.json", "provider-injection");
        List<Object> injectedEvidence = Json.array(FileOps.readJson(injected.resolve("candidate-evidence.json")), "evidence");
        Map<String,Object> extra = new LinkedHashMap<>(Json.object(
                Json.parse(Json.canonical(injectedEvidence.getFirst())), "copied evidence"));
        extra.put("evidenceId", "ev-injected-without-provider-origin");
        injectedEvidence.add(extra);
        FileOps.writeJson(injected.resolve("candidate-evidence.json"), injectedEvidence);
        refreshContentAddressAndChecksums(injected);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(injected), "evidence");
    }

    @Test
    void candidateIdSubstitutionFailsReconciliationEvenWhenDownstreamReferencesAreUpdated() throws Exception {
        Path packageDir = manufactureFixture("granite", "material-granite.json", "candidate-id-substitution");
        String original = "cid-root";
        String substituted = "cid-root-substituted";
        List<Object> identities = Json.array(FileOps.readJson(packageDir.resolve("candidate-identities.json")), "identities");
        Json.object(identities.getFirst(), "identity").put("candidateId", substituted);
        FileOps.writeJson(packageDir.resolve("candidate-identities.json"), identities);
        List<Object> claims = Json.array(FileOps.readJson(packageDir.resolve("candidate-claims.json")), "claims");
        claims.stream().map(v -> Json.object(v, "claim"))
                .filter(v -> original.equals(v.get("subjectIdentityRef"))).forEach(v -> v.put("subjectIdentityRef", substituted));
        FileOps.writeJson(packageDir.resolve("candidate-claims.json"), claims);
        Map<String,Object> resolution = Json.object(FileOps.readJson(packageDir.resolve("identity-resolution.json")), "resolution");
        Map<String,Object> candidateToUao = Json.object(resolution.get("candidateToUao"), "candidateToUao");
        Object uid = candidateToUao.remove(original);
        candidateToUao.put(substituted, uid);
        for (Object raw : Json.array(resolution.get("resolvedIdentities"), "resolved identities")) {
            List<Object> refs = Json.array(Json.object(raw, "resolved identity").get("candidateRefs"), "candidate refs");
            for (int i = 0; i < refs.size(); i++) if (original.equals(refs.get(i))) refs.set(i, substituted);
        }
        FileOps.writeJson(packageDir.resolve("identity-resolution.json"), resolution);
        refreshContentAddressAndChecksums(packageDir);

        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(packageDir), "identities");
    }

    @Test
    void acceptedAndQuarantinedDuplicationFailsReconciliation() throws Exception {
        Path packageDir = manufactureFixture("granite", "material-granite.json", "accepted-quarantine-duplication");
        Map<String,Object> snapshot = Json.object(FileOps.readJson(packageDir.resolve("provider-snapshot.json")), "snapshot");
        Map<String,Object> candidates = Json.object(snapshot.get("candidates"), "provider candidates");
        Object providerClaim = Json.array(candidates.get("claims"), "provider claims").getFirst();
        Map<String,Object> duplicate = new LinkedHashMap<>();
        duplicate.put("category", "claims");
        duplicate.put("index", java.math.BigDecimal.ZERO);
        duplicate.put("errors", List.of("FORGED QUARANTINE"));
        duplicate.put("record", providerClaim);
        FileOps.writeJson(packageDir.resolve("candidate-quarantine.json"), List.of(duplicate));
        refreshContentAddressAndChecksums(packageDir);

        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(packageDir), "candidate-quarantine.json");
    }

    @Test
    void quarantineOmissionDuplicationCategorySubstitutionAndRecordAlterationFailReconciliation() throws Exception {
        Path baseline = manufactureWithInvalidEvidence("quarantine-baseline");
        PackageVerifier.Result baselineResult = new PackageVerifier(SCHEMAS).verify(baseline);
        assertTrue(baselineResult.passed(), baselineResult.errors().toString());

        Path omitted = manufactureWithInvalidEvidence("quarantine-omitted");
        FileOps.writeJson(omitted.resolve("candidate-quarantine.json"), List.of());
        refreshContentAddressAndChecksums(omitted);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(omitted), "candidate-quarantine.json");

        Path duplicated = manufactureWithInvalidEvidence("quarantine-duplicated");
        List<Object> duplicateRecords = Json.array(FileOps.readJson(duplicated.resolve("candidate-quarantine.json")), "quarantine");
        duplicateRecords.add(Json.parse(Json.canonical(duplicateRecords.getFirst())));
        FileOps.writeJson(duplicated.resolve("candidate-quarantine.json"), duplicateRecords);
        refreshContentAddressAndChecksums(duplicated);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(duplicated), "candidate-quarantine.json");

        Path substituted = manufactureWithInvalidEvidence("quarantine-category-substitution");
        List<Object> substitutedRecords = Json.array(FileOps.readJson(substituted.resolve("candidate-quarantine.json")), "quarantine");
        Json.object(substitutedRecords.getFirst(), "quarantine entry").put("category", "claims");
        FileOps.writeJson(substituted.resolve("candidate-quarantine.json"), substitutedRecords);
        refreshContentAddressAndChecksums(substituted);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(substituted), "candidate-quarantine.json");

        Path altered = manufactureWithInvalidEvidence("quarantine-record-altered");
        List<Object> alteredRecords = Json.array(FileOps.readJson(altered.resolve("candidate-quarantine.json")), "quarantine");
        Map<String,Object> record = Json.object(Json.object(alteredRecords.getFirst(), "quarantine entry").get("record"), "record");
        record.put("extract", "ALTERED QUARANTINED RECORD");
        FileOps.writeJson(altered.resolve("candidate-quarantine.json"), alteredRecords);
        refreshContentAddressAndChecksums(altered);
        assertProjectionReconciliationFailure(new PackageVerifier(SCHEMAS).verify(altered), "candidate-quarantine.json");
    }

    private Path manufacture(String seed, String suffix) {
        return manufactureFixture(seed, "biological-cow.json", suffix);
    }

    private Path manufactureFixture(String seed, String fixture, String suffix) {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FixtureProvider provider = new FixtureProvider(Path.of("src/test/resources/fixtures").resolve(fixture), SCHEMAS);
        PipelineResult result = new FoundryPipeline(
                SCHEMAS,
                temp.resolve("work-" + suffix),
                temp.resolve("dist-" + suffix),
                "hardening-test-sha"
        ).manufacture(request, provider, false);
        return result.packagePath();
    }

    private Path manufactureWithInvalidEvidence(String suffix) {
        Map<String,Object> bundle = Json.object(FileOps.readJson(Path.of("src/test/resources/fixtures/material-granite.json")), "fixture");
        Map<String,Object> candidates = Json.object(bundle.get("candidates"), "candidates");
        List<Object> evidence = Json.array(candidates.get("evidence"), "evidence");
        Map<String,Object> invalid = new LinkedHashMap<>();
        invalid.put("sourceRef", "src-granite-material");
        invalid.put("supportsCandidateRef", "clm-material");
        invalid.put("extract", "Provider record deliberately missing evidenceId.");
        evidence.add(invalid);
        Path fixture = temp.resolve(suffix + "-fixture.json");
        FileOps.writeJson(fixture, bundle);
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("granite", "en", "experimental");
        return new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), "hardening-test-sha")
                .manufacture(request, new FixtureProvider(fixture, SCHEMAS), false).packagePath();
    }

    private void replaceAssertion(Path canonicalPath, String original, String replacement) {
        List<Object> uaos = Json.array(FileOps.readJson(canonicalPath), "uaos");
        for (Object raw : uaos) for (Object assertionRaw : Json.array(Json.object(raw, "uao").get("assertions"), "assertions")) {
            Map<String,Object> assertion = Json.object(assertionRaw, "assertion");
            if (original.equals(assertion.get("statement"))) assertion.put("statement", replacement);
        }
        FileOps.writeJson(canonicalPath, uaos);
    }

    private void replaceAssertionInManufacturedPackage(Path packageDir, String original, String replacement) {
        Map<String,Object> manufactured = Json.object(FileOps.readJson(packageDir.resolve("manufactured-package.json")), "manufactured");
        for (Object raw : Json.array(manufactured.get("uaos"), "uaos")) {
            for (Object assertionRaw : Json.array(Json.object(raw, "uao").get("assertions"), "assertions")) {
                Map<String,Object> assertion = Json.object(assertionRaw, "assertion");
                if (original.equals(assertion.get("statement"))) assertion.put("statement", replacement);
            }
        }
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);
    }

    private void refreshContentAddressAndChecksums(Path packageDir) throws Exception {
        Map<String,Object> manifest = Json.object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        String digest = PackageContentDigest.compute(packageDir);
        manifest.put("contentDigest", digest);
        manifest.put("packageId", StableIdentifiers.forText("pkg", 16, digest));
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        rewriteChecksums(packageDir);
    }

    private void assertProjectionReconciliationFailure(PackageVerifier.Result result, String expectedDetail) {
        assertFalse(result.passed(), result.errors().toString());
        assertTrue(result.checks().contains("PROVIDER_PROJECTION_RECONCILIATION"), result.checks().toString());
        assertTrue(result.errors().stream().anyMatch(v -> v.contains("Provider projection reconciliation") && v.contains(expectedDetail)), result.errors().toString());
    }

    private void rewriteChecksums(Path packageDir) throws Exception {
        List<Path> files;
        try (var stream = Files.walk(packageDir)) {
            files = new ArrayList<>(stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !"checksums.sha256".equals(packageDir.relativize(path).toString().replace('\\', '/')))
                    .sorted(Comparator.comparing(path -> packageDir.relativize(path).toString()))
                    .toList());
        }
        StringBuilder out = new StringBuilder();
        for (Path file : files) {
            String relative = packageDir.relativize(file).toString().replace('\\', '/');
            out.append(Hashes.sha256(Files.readAllBytes(file))).append("  ").append(relative).append('\n');
        }
        FileOps.writeText(packageDir.resolve("checksums.sha256"), out.toString());
    }
}
