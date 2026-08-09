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

/** Sixteen-stage domain-independent manufacturing pipeline orchestrator. */
public final class FoundryPipeline extends PackageStages {
    public static final String FOUNDRY_VERSION = VERSION;

    public FoundryPipeline(Path schemaDir, Path workDir, Path distDir, String repositoryCommit) { super(schemaDir, workDir, distDir, repositoryCommit); }

    public PipelineResult manufacture(ManufacturingRequest request, FoundryProvider provider, boolean resume) {
        if (!request.executionMode().equals(provider.executionMode())) {
            throw new IllegalArgumentException("Request executionMode=" + request.executionMode()
                    + " does not match provider executionMode=" + provider.executionMode() + ".");
        }
        String normalisedRequestSeed = normalise(request.identitySeed());
        if (!normalisedRequestSeed.equals(normalise(provider.identitySeed()))) {
            throw new IllegalArgumentException("Provider identitySeed does not match request after normalisation.");
        }

        Map<String, Object> jobKey = new LinkedHashMap<>();
        jobKey.put("request", request.toMap());
        jobKey.put("providerHash", provider.hash());
        jobKey.put("foundryVersion", FOUNDRY_VERSION);
        this.jobId = StableIdentifiers.forJson("job", 16, jobKey);
        this.jobDir = workDir.resolve(jobId);
        if (!resume) FileOps.deleteTree(jobDir);
        try { Files.createDirectories(jobDir); } catch (Exception ex) { throw new IllegalArgumentException("Unable to create job directory: " + ex.getMessage(), ex); }
        this.checkpoint = loadCheckpoint(resume);
        this.resumedStages = 0;

        Path providerSnapshot = jobDir.resolve("provider-snapshot.json");
        if (!resume) {
            FileOps.writeJson(providerSnapshot, provider.snapshot());
        } else if (!Files.isRegularFile(providerSnapshot)) {
            throw new IllegalArgumentException("Provider snapshot is missing for resumed job: " + providerSnapshot);
        }
        if (checkpoint.containsKey("providerHash") && !provider.hash().equals(checkpoint.get("providerHash"))) {
            throw new IllegalArgumentException("Provider snapshot hash differs from the original job provider hash.");
        }
        if (checkpoint.containsKey("providerExecutionMode") && !provider.executionMode().equals(checkpoint.get("providerExecutionMode"))) {
            throw new IllegalArgumentException("Provider snapshot execution mode differs from the original job.");
        }

        checkpoint.putIfAbsent("request", deepCopy(request.toMap()));
        checkpoint.putIfAbsent("providerSource", provider.source().toString());
        checkpoint.putIfAbsent("providerKind", provider.kind());
        checkpoint.putIfAbsent("providerName", provider.name());
        checkpoint.putIfAbsent("providerExecutionMode", provider.executionMode());
        checkpoint.putIfAbsent("providerSnapshot", "provider-snapshot.json");
        checkpoint.putIfAbsent("providerHash", provider.hash());
        checkpoint.putIfAbsent("repositoryCommit", repositoryCommit);
        FileOps.writeJson(jobDir.resolve("checkpoint.json"), checkpoint);

        Map<String, Object> job = map(stage("01_JOB_INITIALISATION", "01-job-initialisation.json", () -> jobInitialisation(request, provider)));
        Map<String, Object> seed = map(stage("02_SEED_NORMALISATION", "02-identity-seed.json", () -> seedNormalisation(request)));
        Map<String, Object> interpretations = map(stage("03_IDENTITY_INTERPRETATION", "03-interpretation-candidates.json", () -> interpretations(seed, provider)));
        Map<String, Object> scope = map(stage("04_SCOPE_RESOLUTION", "04-scope-resolution.json", () -> scopeResolution(interpretations, provider)));
        Map<String, Object> plan = map(stage("05_MANUFACTURING_PLANNING", "05-manufacturing-plan.json", () -> manufacturingPlan(scope, provider)));
        Map<String, Object> sourceStrategy = map(stage("06_SOURCE_STRATEGY", "06-source-strategy.json", () -> sourceStrategy(provider)));
        Map<String, Object> sourceRegistry = sourceAcquisition(provider); // replayed intentionally: validates snapshot side effects
        Map<String, Object> candidates = map(stage("08_KNOWLEDGE_EXTRACTION", "08-candidate-knowledge.json", () -> knowledgeExtraction(provider)));
        Map<String, Object> candidateValidation = map(stage("09_CANDIDATE_VALIDATION", "09-candidate-validation.json", () -> candidateValidation(candidates)));
        Map<String, Object> resolution = map(stage("10_IDENTITY_RESOLUTION", "10-identity-resolution.json", () -> identityResolution(candidateValidation)));
        Map<String, Object> relationships = map(stage("11_RELATIONSHIP_CONSTRUCTION", "11-relationship-construction.json", () -> relationshipConstruction(candidateValidation, resolution)));
        Map<String, Object> canonical = map(stage("12_CANONICAL_BUILD", "12-canonical-build.json", () -> canonicalBuild(request, provider, sourceRegistry, candidateValidation, resolution, relationships)));
        Map<String, Object> coverage = map(stage("13_COMPLETENESS_ANALYSIS", "13-coverage-report.json", () -> completeness(plan, provider)));
        Map<String, Object> verification = map(stage("14_VERIFICATION", "14-verification-report.json", () -> verification(scope, sourceRegistry, candidateValidation, resolution, relationships, canonical)));
        Map<String, Object> publication = map(stage("15_PUBLICATION_DECISION", "15-publication-decision.json", () -> publicationDecision(scope, candidateValidation, relationships, coverage, verification)));
        Map<String, Object> packageResult = packageManufacture(request, provider, job, seed, interpretations, scope, plan, sourceStrategy, sourceRegistry, candidates, candidateValidation, resolution, relationships, canonical, coverage, verification, publication);

        String rootUaoId = string(canonical.get("rootUaoId"), "rootUaoId");
        String status = string(publication.get("status"), "publication status");
        Path packagePath = Path.of(string(packageResult.get("packagePath"), "packagePath"));
        return new PipelineResult(jobId, packagePath, status, rootUaoId, bool(verification.get("passed")), resumedStages);
    }

}
