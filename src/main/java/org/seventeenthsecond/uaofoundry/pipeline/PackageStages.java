package org.seventeenthsecond.uaofoundry.pipeline;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.provider.FoundryProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;
import org.seventeenthsecond.uaofoundry.validation.ValidationResult;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;
import org.seventeenthsecond.uaofoundry.verifier.PackageContentDigest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Deterministic package assembly and post-package verification stage. */
class PackageStages extends CanonicalStages {
    protected PackageStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit) { super(schemaDir, workDir, distDir, repositoryCommit); }
    protected PackageStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit, Path registryRoot, Map<String,Object> registryIndex) { super(schemaDir, workDir, distDir, repositoryCommit, registryRoot, registryIndex); }

    protected Map<String, Object> packageManufacture(
            ManufacturingRequest request, FoundryProvider provider, Map<String, Object> job, Map<String, Object> seed,
            Map<String, Object> interpretations, Map<String, Object> scope, Map<String, Object> plan,
            Map<String, Object> sourceStrategy, Map<String, Object> sourceRegistry, Map<String, Object> candidates,
            Map<String, Object> candidateValidation, Map<String, Object> resolution, Map<String, Object> relationships,
            Map<String, Object> canonical, Map<String, Object> coverage, Map<String, Object> verification,
            Map<String, Object> publication) {
        String label = string(scope.get("canonicalWorkingLabel"), "canonicalWorkingLabel");
        String status = string(publication.get("status"), "publication status");
        String slug = slug(label);
        Path packageDir = distDir.resolve(".staging-" + jobId);
        FileOps.deleteTree(packageDir);
        try { Files.createDirectories(packageDir); } catch (Exception ex) { throw new IllegalArgumentException("Unable to create package staging directory: " + ex.getMessage(), ex); }

        Map<String, Object> finalJob = deepCopyMap(job);
        Map<String, Object> stageStatus = map(finalJob.get("stageStatus"));
        for (String stage : STAGES) stageStatus.put(stage, "COMPLETE");

        FileOps.writeJson(packageDir.resolve("manufacturing-job.json"), finalJob);
        FileOps.writeJson(packageDir.resolve("manufacturing-request.json"), request.toMap());
        FileOps.writeJson(packageDir.resolve("provider-snapshot.json"), FileOps.readJson(jobDir.resolve("provider-snapshot.json")));
        FileOps.writeJson(packageDir.resolve("identity-seed.json"), seed);
        FileOps.writeJson(packageDir.resolve("interpretation-candidates.json"), interpretations);
        FileOps.writeJson(packageDir.resolve("scope-resolution.json"), scope);
        FileOps.writeJson(packageDir.resolve("manufacturing-plan.json"), plan);
        FileOps.writeJson(packageDir.resolve("source-strategy.json"), sourceStrategy);
        FileOps.writeJson(packageDir.resolve("source-registry.json"), sourceRegistry);
        FileOps.copyTree(jobDir.resolve("source-corpus"), packageDir.resolve("source-corpus"));

        Map<String, Object> valid = map(candidateValidation.get("valid"));
        FileOps.writeJson(packageDir.resolve("candidate-identities.json"), valid.get("identities"));
        FileOps.writeJson(packageDir.resolve("candidate-relationships.json"), valid.get("relationships"));
        FileOps.writeJson(packageDir.resolve("candidate-claims.json"), valid.get("claims"));
        FileOps.writeJson(packageDir.resolve("candidate-evidence.json"), valid.get("evidence"));
        FileOps.writeJson(packageDir.resolve("candidate-states.json"), valid.get("states"));
        FileOps.writeJson(packageDir.resolve("candidate-events.json"), valid.get("events"));
        FileOps.writeJson(packageDir.resolve("candidate-language-mappings.json"), valid.get("languageMappings"));
        FileOps.writeJson(packageDir.resolve("candidate-quarantine.json"), candidateValidation.get("quarantined"));
        FileOps.writeJson(packageDir.resolve("identity-resolution.json"), resolution);
        FileOps.writeJson(packageDir.resolve("canonical-identities.json"), canonical.get("uaos"));
        FileOps.writeJson(packageDir.resolve("canonical-relationships.json"), canonical.get("uros"));
        FileOps.writeJson(packageDir.resolve("provenance-ledger.json"), canonical.get("provenanceLedger"));
        FileOps.writeJson(packageDir.resolve("coverage-report.json"), coverage);
        FileOps.writeJson(packageDir.resolve("verification-report.json"), verification);
        FileOps.writeJson(packageDir.resolve("unresolved-items.json"), canonical.get("unresolvedItems"));
        FileOps.writeJson(packageDir.resolve("publication-decision.json"), publication);
        if (relationshipEdition != null) {
            // The edition travels inside the package so the verifier can re-derive every typed
            // relationship from package bytes alone, and so a later reader sees exactly which
            // (digest-pinned, non-admitted) vocabulary the records were validated against.
            FileOps.writeJson(packageDir.resolve("experimental-relationships.json"), canonical.get("experimentalRelationships"));
            FileOps.writeJson(packageDir.resolve("relationship-type-edition.json"), relationshipEdition.document());
        }

        Map<String, Object> manufactured = new LinkedHashMap<>();
        manufactured.put("packageVersion", "0.1.0");
        manufactured.put("request", request.toMap());
        manufactured.put("rootUaoId", canonical.get("rootUaoId"));
        manufactured.put("uaos", canonical.get("uaos"));
        manufactured.put("uros", canonical.get("uros"));
        manufactured.put("verification", verification);
        manufactured.put("publicationDecision", publication);
        validate(manufactured, "manufactured-package.schema.json", "Manufactured package");
        FileOps.writeJson(packageDir.resolve("manufactured-package.json"), manufactured);

        String contentDigest = PackageContentDigest.compute(packageDir);
        String packageId = StableIdentifiers.forText("pkg", 16, contentDigest);
        List<String> fileNames = new ArrayList<>(packageFiles(packageDir, false));
        fileNames.add("manifest.json");
        fileNames = fileNames.stream().distinct().sorted().toList();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packageId", packageId);
        manifest.put("packageVersion", request.requestedVersion());
        manifest.put("rootUaoId", canonical.get("rootUaoId"));
        manifest.put("publicationStatus", status);
        manifest.put("jobId", jobId);
        manifest.put("contentDigest", contentDigest);
        manifest.put("files", new ArrayList<>(fileNames));
        validate(manifest, "release-manifest.schema.json", "Release manifest");
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        writeChecksums(packageDir);

        PackageVerifier.Result packageVerification = new PackageVerifier(schemaDir).verify(packageDir);
        if (!packageVerification.passed()) {
            throw new IllegalArgumentException("Packaged artifact failed checksum/schema verification: " + String.join("; ", packageVerification.errors()));
        }
        Path finalPackageDir = distDir.resolve("UAO-" + slug + "-v" + request.requestedVersion() + "-"
                + status.toLowerCase(Locale.ROOT).replace('_','-') + "-" + packageId);
        if (Files.exists(finalPackageDir)) {
            if (!FileOps.treeHash(finalPackageDir).equals(FileOps.treeHash(packageDir))) {
                throw new IllegalArgumentException("Package output collision: existing path has different content: " + finalPackageDir);
            }
            FileOps.deleteTree(packageDir);
        } else {
            try { Files.move(packageDir, finalPackageDir); }
            catch (Exception ex) { throw new IllegalArgumentException("Unable to finalize package directory: " + ex.getMessage(), ex); }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("packagePath", finalPackageDir.toString());
        out.put("packageId", packageId);
        out.put("contentDigest", contentDigest);
        out.put("checksumVerification", "PASSED");
        out.put("publicationStatus", status);
        Path stageFile = jobDir.resolve("16-package-manufacture.json");
        FileOps.writeJson(stageFile, out);
        markStage("16_PACKAGE_MANUFACTURE", stageFile);
        return out;
    }
}
