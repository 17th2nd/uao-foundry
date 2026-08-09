package org.seventeenthsecond.uaofoundry.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
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

    private Path manufacture(String seed, String suffix) {
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FixtureProvider provider = new FixtureProvider(FIXTURE, SCHEMAS);
        PipelineResult result = new FoundryPipeline(
                SCHEMAS,
                temp.resolve("work-" + suffix),
                temp.resolve("dist-" + suffix),
                "hardening-test-sha"
        ).manufacture(request, provider, false);
        return result.packagePath();
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
