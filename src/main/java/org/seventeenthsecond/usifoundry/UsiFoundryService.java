package org.seventeenthsecond.usifoundry;

import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.provider.FoundryProvider;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;
import org.seventeenthsecond.uaofoundry.reuse.RegistryAwareCommandProvider;
import org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer;
import org.seventeenthsecond.uaofoundry.runs.RunRecord;
import org.seventeenthsecond.uaofoundry.runs.RunStore;
import org.seventeenthsecond.uaofoundry.staging.StagedRelationshipStore;
import org.seventeenthsecond.uaofoundry.usi.UsiIdentifiers;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The application-facing facade over the audited Foundry core (ADR-0004 §4).
 *
 * <p>This translates product terminology and shapes responses for the UI. It <b>re-implements
 * nothing</b>: identity resolution, canonicalisation, verification, registry admission and reuse
 * analysis all remain in {@code org.seventeenthsecond.uaofoundry}. Two implementations of identity
 * resolution would diverge, and the audited one would not be the one running.
 *
 * <p>Terminology mapping applied here, and nowhere deeper:
 *
 * <pre>
 * product              core
 * ────────────────────────────────────────────
 * usiId                canonical uid  (value unchanged — ADR-0005)
 * identifierScheme     "legacy-uao"
 * semantic identity    UAO identity
 * USI package          manufactured package
 * </pre>
 *
 * <p>{@code usiId} carries the canonical identifier <em>unchanged</em>. No {@code usi-} string is
 * minted: the prefix is pinned by ASA CSS, and an identifier an operator can copy must be one the
 * registry will accept back.
 */
public final class UsiFoundryService {

    private final UsiFoundryConfig config;
    private final FoundryRegistry registry;
    private final RunStore runStore;
    private final StagedRelationshipStore stagedRelationships;

    public UsiFoundryService(UsiFoundryConfig config) {
        this.config = config;
        this.registry = new FoundryRegistry(config.registry(), config.schemaDir());
        this.runStore = new RunStore(config.runs());
        this.stagedRelationships = new StagedRelationshipStore(config.stagedRelationships());
        initialiseRegistry();
    }

    /**
     * Initialises a brand-new registry so a first run does not open on a verification failure.
     *
     * <p>An empty registry directory with no index is a <em>new</em> registry, not a broken one.
     * The distinction matters: refusing to guess is right when packages exist without an index —
     * that is a damaged registry and the core correctly demands an explicit rebuild — but an
     * operator launching the application for the first time has nothing to rebuild.
     *
     * <p>Mirrors the discipline already used by {@code RegistryManufactureApplication}: initialise
     * only when there is genuinely nothing there, and otherwise leave verification to speak.
     */
    private void initialiseRegistry() {
        Path index = config.registry().resolve("index.json");
        if (Files.exists(index)) return;
        Path packages = config.registry().resolve("packages");
        if (Files.isDirectory(packages)) {
            try (var stream = Files.list(packages)) {
                if (stream.findAny().isPresent()) {
                    // Packages without an index is a damaged registry; an explicit rebuild is a
                    // deliberate operator act, not something an application start should do.
                    return;
                }
            } catch (Exception ex) {
                throw new UsiFoundryException(UsiFoundryException.CONFIGURATION_ERROR,
                        "Unable to inspect the registry: " + ex.getMessage());
            }
        }
        registry.rebuildAndPersist();
    }

    public UsiFoundryConfig config() { return config; }
    public RunStore runStore() { return runStore; }

    // ------------------------------------------------------------------ manufacture

    /** What a manufacture needs. Provider choice is explicit; nothing is inferred. */
    public record ManufactureRequest(String identity, String context, String provider,
                                     Path fixture, boolean register) {}

    /**
     * Runs one manufacture to completion and records the run.
     *
     * <p>Stage progress is observable while this runs: the core writes {@code checkpoint.json} into
     * the job directory as each stage completes, and {@link #stageProgress(Path)} reads it. That is
     * genuine progress from the pipeline's own artefacts, not a timer pretending to be one.
     */
    public Map<String,Object> manufacture(ManufactureRequest request, Path jobWorkDir) {
        String startedAt = now();
        Map<String,Object> preIndex = registry.index();

        RequestLoader loader = new RequestLoader(config.schemaDir().resolve("manufacturing-request.schema.json"));
        boolean fixtureMode = request.fixture() != null;
        ManufacturingRequest core = loader.fromSeed(request.identity(), config.defaultLanguage(),
                config.defaultProfile(), fixtureMode ? "fixture" : "live");

        String registryContextHash = null;
        FoundryProvider provider;
        if (fixtureMode) {
            if (!Files.isRegularFile(request.fixture())) {
                throw new UsiFoundryException("CONFIGURATION_ERROR", "Fixture bundle not found: " + request.fixture());
            }
            provider = new FixtureProvider(request.fixture(), config.schemaDir());
        } else {
            String command = config.claudeCommand();
            if (command == null) {
                throw new UsiFoundryException("CONFIGURATION_ERROR",
                        "No provider command is configured. Set claudeCommand in " + config.configFile()
                                + ", or manufacture from a fixture bundle.");
            }
            Map<String,Object> discovery = registry.discoveryContext(core.identitySeed(), config.catalogLimit());
            registryContextHash = Hashes.canonicalJson(discovery);
            provider = new RegistryAwareCommandProvider(Path.of(command), core, config.schemaDir(),
                    Duration.ofSeconds(config.providerTimeoutSeconds()), discovery, config.registry());
        }

        PipelineResult result;
        try {
            result = new FoundryPipeline(config.schemaDir(), jobWorkDir, config.packages(), "usi-app",
                    config.registry(), preIndex).manufacture(core, provider, false);
        } catch (IllegalArgumentException ex) {
            throw UsiFoundryException.classify(ex);
        }

        Map<String,Object> reuse;
        try {
            ReuseAnalyzer analyzer = new ReuseAnalyzer(config.schemaDir());
            reuse = analyzer.analyze(preIndex, config.registry(), result.packagePath(),
                    registryContextHash == null ? Hashes.canonicalJson(preIndex) : registryContextHash);
            new SchemaValidator().validate(reuse, config.schemaDir().resolve("reuse-report.schema.json"))
                    .requireValid("Reuse report");
        } catch (IllegalArgumentException ex) {
            throw UsiFoundryException.classify(ex);
        }

        String admission = "NOT_REQUESTED";
        String admissionDetail = null;
        if (request.register()) {
            try {
                registry.register(result.packagePath());
                admission = "REGISTERED";
            } catch (IllegalArgumentException ex) {
                admission = "REFUSED";
                admissionDetail = ex.getMessage();
            }
        }

        // Directive §18: retain identity-bound relationship candidates so persistent relationship
        // reconstruction can be studied while ASA#29 blocks accumulation. This changes no
        // publication decision and enters no registry index -- the package above is still
        // EVIDENCE_INCOMPLETE and still inadmissible if it carries a relationship candidate.
        stagedRelationships.stageFrom(result.packagePath(), now());

        List<String> usiIds = new ArrayList<>();
        for (Object raw : Json.array(FileOps.readJson(result.packagePath().resolve("canonical-identities.json")), "identities")) {
            usiIds.add(String.valueOf(Json.object(raw, "identity").get("uid")));
        }
        String packageId = String.valueOf(Json.object(
                FileOps.readJson(result.packagePath().resolve("manifest.json")), "manifest").get("packageId"));

        String status = !result.verificationPassed() ? RunRecord.VERIFICATION_FAILED
                : "REFUSED".equals(admission) ? RunRecord.ADMISSION_REFUSED
                : RunRecord.COMPLETED;
        RunRecord run = runStore.record(RunRecord.create(
                request.identity(), request.context(), fixtureMode ? "fixture" : "claude-code", status,
                packageId, usiIds, Hashes.canonicalJson(preIndex), Hashes.canonicalJson(registry.index()),
                reuse, startedAt, now(), null, admissionDetail));

        return manufactureResult(result, reuse, packageId, usiIds, admission, admissionDetail, run);
    }

    private Map<String,Object> manufactureResult(PipelineResult result, Map<String,Object> reuse,
                                                 String packageId, List<String> usiIds,
                                                 String admission, String admissionDetail, RunRecord run) {
        Map<String,Object> counts = Json.object(reuse.get("counts"), "counts");
        List<Object> unresolved = Json.array(
                FileOps.readJson(result.packagePath().resolve("unresolved-items.json")), "unresolved");

        int unreconciled = 0;
        Map<String,Object> index = registry.index();
        for (Object raw : Json.array(index.get("identities"), "identities")) {
            Map<String,Object> identity = Json.object(raw, "identity");
            if (usiIds.contains(String.valueOf(identity.get("uid")))
                    && SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(identity.get("semanticVariantStatus"))) {
                unreconciled++;
            }
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("usiId", result.rootUaoId());
        out.put("identifierScheme", UsiIdentifiers.schemeOf(result.rootUaoId()));
        out.put("canonicalLabel", canonicalLabelOf(result.packagePath(), result.rootUaoId()));
        out.put("verification", result.verificationPassed() ? "PASS" : "FAIL");
        out.put("publicationStatus", result.publicationStatus());
        out.put("registryAdmission", admission);
        if (admissionDetail != null) out.put("registryAdmissionDetail", admissionDetail);
        out.put("packageId", packageId);
        out.put("packagePath", result.packagePath().toString());
        out.put("runId", run.runId());
        out.put("usiIds", new ArrayList<>(usiIds));

        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("existingIdentitiesReused", counts.get("reusedUaoCount"));
        summary.put("newIdentitiesManufactured", counts.get("newUaoCount"));
        summary.put("registrySourcesReused", counts.get("registrySourceCount"));
        summary.put("newSourcesAcquired", counts.get("newSourceCount"));
        summary.put("relationshipsDiscovered", BigDecimal.valueOf(unresolved.size()));
        summary.put("relationshipsUnresolved", BigDecimal.valueOf(unresolved.size()));
        summary.put("semanticVariants", BigDecimal.valueOf(unreconciled));
        out.put("counts", summary);

        if (!unresolved.isEmpty()) {
            out.put("relationshipAuthority", "URO_TYPE_AUTHORITY_UNAVAILABLE");
            out.put("relationshipAuthorityNote",
                    "Relationship candidates are retained and bound to persistent identities, but canonical "
                            + "publication is fail-closed pending 17th2nd/ASA#29.");
        }
        return out;
    }

    private String canonicalLabelOf(Path packageDir, String uid) {
        for (Object raw : Json.array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "identities")) {
            Map<String,Object> identity = Json.object(raw, "identity");
            if (uid.equals(identity.get("uid"))) {
                return String.valueOf(Json.object(Json.object(identity.get("internal_state"), "internal_state")
                        .get("foundry_identity"), "foundry_identity").get("canonical_label"));
            }
        }
        return uid;
    }

    /**
     * Real stage progress, read from the checkpoint the pipeline writes as it goes.
     *
     * <p>Returns every stage with its actual status. A stage is only {@code COMPLETE} because the
     * core recorded it, so the UI never has to invent motion.
     */
    public Map<String,Object> stageProgress(Path jobWorkDir) {
        List<String> stages = List.of(
                "01_JOB_INITIALISATION", "02_SEED_NORMALISATION", "03_IDENTITY_INTERPRETATION",
                "04_SCOPE_RESOLUTION", "05_MANUFACTURING_PLANNING", "06_SOURCE_STRATEGY",
                "07_SOURCE_ACQUISITION", "08_KNOWLEDGE_EXTRACTION", "09_CANDIDATE_VALIDATION",
                "10_IDENTITY_RESOLUTION", "11_RELATIONSHIP_CONSTRUCTION", "12_CANONICAL_BUILD",
                "13_COMPLETENESS_ANALYSIS", "14_VERIFICATION", "15_PUBLICATION_DECISION",
                "16_PACKAGE_MANUFACTURE");

        Map<String,Object> completed = Map.of();
        if (Files.isDirectory(jobWorkDir)) {
            try (var stream = Files.list(jobWorkDir)) {
                for (Path job : stream.filter(Files::isDirectory).toList()) {
                    Path checkpoint = job.resolve("checkpoint.json");
                    if (Files.isRegularFile(checkpoint)) {
                        Object recorded = Json.object(FileOps.readJson(checkpoint), "checkpoint").get("completed");
                        if (recorded instanceof Map<?,?> map) {
                            @SuppressWarnings("unchecked") Map<String,Object> typed = (Map<String,Object>) map;
                            completed = typed;
                        }
                    }
                }
            } catch (Exception ignored) {
                // A partially written checkpoint simply means no progress is readable yet.
            }
        }

        List<Object> out = new ArrayList<>();
        boolean seenPending = false;
        for (String stage : stages) {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("stage", stage);
            boolean done = completed.containsKey(stage);
            entry.put("status", done ? "COMPLETE" : seenPending ? "PENDING" : "ACTIVE");
            if (!done) seenPending = true;
            out.add(entry);
        }
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("stages", out);
        response.put("completedCount", BigDecimal.valueOf(completed.size()));
        response.put("totalCount", BigDecimal.valueOf(stages.size()));
        return response;
    }

    // ------------------------------------------------------------------ discovery

    public Map<String,Object> search(String query) {
        List<Object> matches = new ArrayList<>();
        for (Object raw : registry.search(query)) {
            Map<String,Object> hit = Json.object(raw, "hit");
            Map<String,Object> identity = Json.object(hit.get("identity"), "identity");
            matches.add(identitySummary(identity, Json.array(hit.get("matchKinds"), "matchKinds")));
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("matches", matches);
        return out;
    }

    public Map<String,Object> identity(String reference) {
        Map<String,Object> record = registry.identityRecord(referenceOf(reference));
        Map<String,Object> resolution = Json.object(record.get("resolution"), "resolution");
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("query", reference);
        out.put("decision", resolution.get("decision"));
        out.put("reasonCodes", resolution.get("reasonCodes"));
        if (record.get("identity") instanceof Map<?,?> raw) {
            @SuppressWarnings("unchecked") Map<String,Object> identity = (Map<String,Object>) raw;
            out.put("identity", identityDetail(identity));
        } else {
            List<Object> candidates = new ArrayList<>();
            for (Object raw : Json.array(record.get("candidates"), "candidates")) {
                candidates.add(identitySummary(Json.object(raw, "identity"), List.of()));
            }
            out.put("candidates", candidates);
        }
        return out;
    }

    private Map<String,Object> identitySummary(Map<String,Object> identity, List<Object> matchKinds) {
        String uid = String.valueOf(identity.get("uid"));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("usiId", uid);
        out.put("identifierScheme", UsiIdentifiers.schemeOf(uid));
        out.put("resolutionKey", identity.get("resolutionKey"));
        out.put("semanticType", identity.get("semanticType"));
        out.put("canonicalLabels", identity.get("canonicalLabels"));
        out.put("aliases", identity.get("aliases"));
        out.put("lifecycleState", identity.get("lifecycleState"));
        out.put("semanticVariantStatus", identity.get("semanticVariantStatus"));
        out.put("occurrenceCount", BigDecimal.valueOf(Json.array(identity.get("occurrences"), "occurrences").size()));
        if (!matchKinds.isEmpty()) out.put("matchedBy", matchKinds);
        return out;
    }

    private Map<String,Object> identityDetail(Map<String,Object> identity) {
        Map<String,Object> out = identitySummary(identity, List.of());
        out.put("externalIdentifiers", identity.get("externalIdentifiers"));
        out.put("stateVersions", identity.get("stateVersions"));
        out.put("successorUids", identity.get("successorUids"));
        out.put("lifecycleOperationId", identity.get("lifecycleOperationId"));
        out.put("occurrences", identity.get("occurrences"));
        out.put("identityDecisions", identity.get("decisionHistory"));
        out.put("relationshipBindings", identity.get("relationshipBindings"));
        return out;
    }

    // ------------------------------------------------------------------ packages, runs, status

    public Map<String,Object> packageDetail(String packageId) {
        Path dir = config.registry().resolve("packages").resolve(packageId).normalize();
        if (!dir.startsWith(config.registry().resolve("packages")) || !Files.isDirectory(dir)) {
            throw new UsiFoundryException("NOT_FOUND", "No registered package: " + packageId);
        }
        Map<String,Object> manifest = Json.object(FileOps.readJson(dir.resolve("manifest.json")), "manifest");
        Map<String,Object> verification = Json.object(FileOps.readJson(dir.resolve("verification-report.json")), "verification");
        Map<String,Object> publication = Json.object(FileOps.readJson(dir.resolve("publication-decision.json")), "publication");

        List<Object> identities = new ArrayList<>();
        for (Object raw : Json.array(FileOps.readJson(dir.resolve("canonical-identities.json")), "identities")) {
            Map<String,Object> uao = Json.object(raw, "identity");
            Map<String,Object> kernel = Json.object(Json.object(uao.get("internal_state"), "internal_state")
                    .get("foundry_identity"), "foundry_identity");
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("usiId", uao.get("uid"));
            entry.put("canonicalLabel", kernel.get("canonical_label"));
            entry.put("semanticType", kernel.get("semantic_type"));
            entry.put("externalIdentifiers", kernel.get("external_identifiers"));
            entry.put("lifecycleStatus", uao.get("lifecycle_status"));
            entry.put("assertionCount", BigDecimal.valueOf(Json.array(uao.get("assertions"), "assertions").size()));
            identities.add(entry);
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("packageId", manifest.get("packageId"));
        out.put("contentDigest", manifest.get("contentDigest"));
        out.put("rootUsiId", manifest.get("rootUaoId"));
        out.put("publicationStatus", manifest.get("publicationStatus"));
        out.put("verificationPassed", verification.get("passed"));
        out.put("verificationChecks", verification.get("checks"));
        out.put("verificationWarnings", verification.get("warnings"));
        out.put("publicationReasons", publication.get("reasons"));
        out.put("identities", identities);
        out.put("sources", Json.object(FileOps.readJson(dir.resolve("source-registry.json")), "sources").get("sources"));
        out.put("unresolvedRelationships", FileOps.readJson(dir.resolve("unresolved-items.json")));
        out.put("files", manifest.get("files"));
        out.put("legacyEmbeddedReuseReport", Files.isRegularFile(dir.resolve("reuse-report.json")));
        return out;
    }

    public Map<String,Object> runs(int limit) {
        List<Object> out = new ArrayList<>();
        for (RunRecord run : runStore.list()) {
            if (out.size() >= limit) break;
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("runId", run.runId());
            entry.put("identity", run.identitySeed());
            entry.put("context", run.context());
            entry.put("provider", run.provider());
            entry.put("status", run.status());
            entry.put("packageId", run.packageId());
            entry.put("usiCount", BigDecimal.valueOf(run.usiIds().size()));
            entry.put("startedAt", run.startedAt());
            entry.put("completedAt", run.completedAt());
            if (run.reuseReport() != null) entry.put("counts", run.reuseReport().get("counts"));
            if (run.note() != null) entry.put("note", run.note());
            out.add(entry);
        }
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("runs", out);
        response.put("runStore", runStore.root().toString());
        return response;
    }

    public Map<String,Object> status() {
        FoundryRegistry.VerificationResult verification = registry.verify();
        Map<String,Object> index = registry.index();
        int unreconciled = 0, nonActive = 0;
        for (Object raw : Json.array(index.get("identities"), "identities")) {
            Map<String,Object> identity = Json.object(raw, "identity");
            if (SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(identity.get("semanticVariantStatus"))) unreconciled++;
            Object lifecycle = identity.get("lifecycleState");
            if (lifecycle != null && !"ACTIVE".equals(lifecycle)) nonActive++;
        }
        Map<String,Object> out = new LinkedHashMap<>(config.describe());
        out.put("registryVerification", verification.passed() ? "PASS" : "FAIL");
        out.put("registryErrors", verification.errors());
        out.put("packageCount", BigDecimal.valueOf(verification.packageCount()));
        out.put("identityCount", BigDecimal.valueOf(verification.identityCount()));
        out.put("unreconciledIdentities", BigDecimal.valueOf(unreconciled));
        out.put("nonActiveIdentities", BigDecimal.valueOf(nonActive));
        out.put("identityOperations", index.get("identityOperations"));
        out.put("runCount", BigDecimal.valueOf(runStore.list().size()));
        try {
            out.put("stagedRelationshipCount", BigDecimal.valueOf(stagedRelationships.list().size()));
        } catch (IllegalArgumentException ex) {
            // The store fails closed on a record that lost its non-canonical labelling. That must
            // be visible to the operator, not fatal to the rest of the status view.
            out.put("stagedRelationshipStoreError", ex.getMessage());
        }
        out.put("relationshipAuthority", "URO_TYPE_AUTHORITY_UNAVAILABLE");
        out.put("relationshipAuthorityIssue", "17th2nd/ASA#29");
        return out;
    }

    public Map<String,Object> verifyRegistry() {
        FoundryRegistry.VerificationResult result = registry.verify();
        return result.toMap();
    }

    /**
     * The staged candidate relationship neighbourhood of one identity (§18).
     *
     * <p>Explicitly non-canonical. These edges were asserted by providers and bound to persistent
     * identities; none is governed, and no URO exists. The view is here so persistent relationship
     * reconstruction can be measured while ASA#29 is open, not so it can be believed.
     */
    public Map<String,Object> stagedRelationships(String reference) {
        Map<String,Object> record = registry.identityRecord(referenceOf(reference));
        Map<String,Object> resolution = Json.object(record.get("resolution"), "resolution");
        if (!"SAME".equals(resolution.get("decision"))) {
            throw new UsiFoundryException(UsiFoundryException.IDENTITY_AMBIGUITY,
                    "Staged relationships require an exactly resolved identity; resolution was "
                            + resolution.get("decision") + ".");
        }
        return stagedRelationships.neighbourhood(String.valueOf(resolution.get("uid")));
    }

    /** {@code A_x} / {@code R_x} debugging view (§20). The application never computes significance. */
    public Map<String,Object> significanceInputs(String reference) {
        return registry.significanceInputs(referenceOf(reference));
    }

    // ------------------------------------------------------------------ plumbing

    static IdentityReference referenceOf(String value) {
        if (value == null || value.isBlank()) throw new UsiFoundryException("INVALID_INPUT", "A reference is required.");
        if (value.matches("uao-[a-f0-9]{12}")) return IdentityReference.uid(value);
        if (value.matches("usi-[a-f0-9]{12}")) {
            // Accepted as a courtesy so a future-form identifier is not silently unresolvable,
            // and translated to the canonical form the registry actually holds (ADR-0005).
            return IdentityReference.uid(UsiIdentifiers.toLegacy(value));
        }
        if (value.startsWith("foundry:") || value.startsWith("fixture:") || value.startsWith("ext:")) {
            return IdentityReference.resolutionKey(value);
        }
        if (value.matches("[a-z][a-z0-9._-]*:\\S+")) {
            int split = value.indexOf(':');
            return IdentityReference.externalIdentifier(value.substring(0, split), value.substring(split + 1));
        }
        return IdentityReference.alias(value);
    }

    /**
     * Millisecond resolution, deliberately.
     *
     * <p>Run records are content-addressed, so two records with identical content collapse into
     * one. At second resolution ten rapid identical manufactures produced only four records —
     * operational evidence that undercounts attempts is not evidence. Milliseconds separate
     * distinct attempts while leaving a genuine replay with supplied timestamps idempotent.
     */
    private static String now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
