package org.seventeenthsecond.uaofoundry.console;

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
import org.seventeenthsecond.uaofoundry.runs.RunRecord;
import org.seventeenthsecond.uaofoundry.runs.RunStore;
import org.seventeenthsecond.uaofoundry.reuse.RegistryAwareCommandProvider;
import org.seventeenthsecond.uaofoundry.reuse.ReuseAnalyzer;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The operator-facing manufacturing surface.
 *
 * <p>One command runs the whole flow — consult the registry, acquire evidence, resolve identity,
 * verify, optionally admit — and reports it in the terms an operator actually asks about: how much
 * was reused, how much is genuinely new, what remains unresolved, and whether it passed.
 *
 * <p>A CLI rather than a web UI, deliberately. The programme's target surface is a handful of
 * inputs and a dozen counters; a browser adds a server, a build step and a second place for the
 * numbers to disagree with the packages they came from, in exchange for nothing an operator needs.
 *
 * <p>This console orchestrates the existing machinery and re-implements none of it. Every number
 * it prints is read back from the manufactured package, the Foundry-computed reuse report or the
 * verified registry index — never accumulated as it goes. That matters: a counter maintained by the
 * reporting layer can drift from the artefact it describes, and a demonstration that shows numbers
 * the packages do not support is worse than no demonstration.
 */
public final class OperatorConsole {
    private static final String RULE = "─".repeat(66);

    private final PrintStream out;
    private final PrintStream err;

    public OperatorConsole() { this(System.out, System.err); }
    public OperatorConsole(PrintStream out, PrintStream err) { this.out = out; this.err = err; }

    public static void main(String[] args) {
        int exit = new OperatorConsole().run(args);
        if (exit != 0) System.exit(exit);
    }

    public int run(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0])) {
            usage();
            return args.length == 0 ? 2 : 0;
        }
        try {
            Options options = Options.parse(java.util.Arrays.copyOfRange(args, 1, args.length));
            return switch (args[0]) {
                case "manufacture" -> manufacture(options);
                case "search" -> search(options);
                case "identity" -> identity(options);
                case "status" -> status(options);
                default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
            };
        } catch (IllegalArgumentException ex) {
            err.println("error: " + ex.getMessage());
            return 2;
        }
    }

    // ------------------------------------------------------------------ manufacture

    private int manufacture(Options options) {
        String seed = options.single("manufacture requires exactly one identity expression.");
        String startedAt = options.clock();
        Path schemaDir = options.schemaDir();
        FoundryRegistry registry = options.registry() == null ? null : new FoundryRegistry(options.registry(), schemaDir);
        Map<String,Object> preIndex = registry == null ? null : readIndex(registry, options.registry());

        RequestLoader loader = new RequestLoader(schemaDir.resolve("manufacturing-request.schema.json"));
        String executionMode = options.fixture() != null ? "fixture" : "live";
        ManufacturingRequest request = loader.fromSeed(seed, options.language(), options.profile(), executionMode);

        Map<String,Object> registryContext = null;
        String registryContextHash = null;
        FoundryProvider provider;
        if (options.fixture() != null) {
            provider = new FixtureProvider(options.fixture(), schemaDir);
        } else {
            if (options.providerCommand() == null) {
                throw new IllegalArgumentException("manufacture requires --fixture <bundle> or --provider <command>.");
            }
            if (registry == null) throw new IllegalArgumentException("Live manufacture requires --registry.");
            registryContext = registry.discoveryContext(request.identitySeed(), options.catalogLimit());
            registryContextHash = Hashes.canonicalJson(registryContext);
            provider = new RegistryAwareCommandProvider(options.providerCommand(), request, schemaDir,
                    Duration.ofSeconds(options.timeoutSeconds()), registryContext, options.registry());
        }

        FoundryPipeline pipeline = registry == null
                ? new FoundryPipeline(schemaDir, options.workDir(), options.distDir(), options.repositoryCommit())
                : new FoundryPipeline(schemaDir, options.workDir(), options.distDir(), options.repositoryCommit(),
                        options.registry(), preIndex);
        PipelineResult result = pipeline.manufacture(request, provider, false);

        Map<String,Object> reuse = null;
        if (registry != null) {
            ReuseAnalyzer analyzer = new ReuseAnalyzer(schemaDir);
            reuse = analyzer.analyze(preIndex, options.registry(), result.packagePath(),
                    registryContextHash == null ? Hashes.canonicalJson(preIndex) : registryContextHash);
            new SchemaValidator().validate(reuse, schemaDir.resolve("reuse-report.schema.json")).requireValid("Reuse report");
        }

        String admission = "NOT_REQUESTED";
        if (options.register()) {
            if (registry == null) throw new IllegalArgumentException("--register requires --registry.");
            // Admission is gated on the package's own publication decision, so an ineligible
            // package is reported as refused rather than silently skipped.
            try {
                registry.register(result.packagePath());
                admission = "REGISTERED";
            } catch (IllegalArgumentException ex) {
                admission = "REFUSED: " + ex.getMessage();
            }
        }

        // ADR-0006: reuse evidence is run evidence. It goes beside the registry, never inside the
        // content-addressed package, so repeated manufacture of identical material stays
        // byte-identical and admission stays idempotent.
        String runId = null;
        if (registry != null) {
            String status = !result.verificationPassed() ? RunRecord.VERIFICATION_FAILED
                    : admission.startsWith("REFUSED") ? RunRecord.ADMISSION_REFUSED
                    : RunRecord.COMPLETED;
            List<String> usiIds = new ArrayList<>();
            for (Object raw : list(FileOps.readJson(result.packagePath().resolve("canonical-identities.json")))) {
                usiIds.add(String.valueOf(map(raw).get("uid")));
            }
            RunStore store = options.runStore() != null
                    ? new RunStore(options.runStore()) : RunStore.besideRegistry(options.registry());
            RunRecord run = RunRecord.create(seed, options.context(),
                    options.fixture() != null ? "fixture" : "command", status,
                    String.valueOf(map(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("packageId")),
                    usiIds, Hashes.canonicalJson(preIndex), Hashes.canonicalJson(registry.index()),
                    reuse, startedAt, options.clock(), null, null);
            runId = store.record(run).runId();
        }

        Report report = Report.of(result, reuse, registry, admission, seed, options, runId);
        if (options.json()) out.println(Json.canonical(report.toMap()));
        else report.print(out);
        return report.verificationPassed() ? 0 : 4;
    }

    // ------------------------------------------------------------------ discovery

    private int search(Options options) {
        String query = options.single("search requires exactly one query.");
        FoundryRegistry registry = requireRegistry(options);
        List<Object> matches = registry.search(query);
        if (options.json()) {
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("query", query);
            response.put("matches", matches);
            out.println(Json.canonical(response));
            return matches.isEmpty() ? 4 : 0;
        }
        out.println(RULE);
        out.printf("  UAO FOUNDRY — SEARCH  %s%n", query);
        out.println(RULE);
        if (matches.isEmpty()) {
            out.println("  no registered identity matched.");
        } else {
            for (Object raw : matches) {
                Map<String,Object> hit = map(raw);
                Map<String,Object> identity = map(hit.get("identity"));
                out.printf("  %-18s %s%n", identity.get("uid"), identity.get("resolutionKey"));
                out.printf("  %-18s %s%n", "matched by", String.join(", ", strings(hit.get("matchKinds"))));
                out.printf("  %-18s %s%n", "labels", String.join(", ", strings(identity.get("canonicalLabels"))));
                out.printf("  %-18s %s / %s%n", "state",
                        identity.get("lifecycleState"), identity.get("semanticVariantStatus"));
                out.printf("  %-18s %d%n", "occurrences", list(identity.get("occurrences")).size());
                out.println();
            }
        }
        out.println(RULE);
        return matches.isEmpty() ? 4 : 0;
    }

    private int identity(Options options) {
        String value = options.single("identity requires exactly one reference.");
        FoundryRegistry registry = requireRegistry(options);
        Map<String,Object> record = registry.identityRecord(reference(value));
        Map<String,Object> resolution = map(record.get("resolution"));
        boolean resolved = "SAME".equals(resolution.get("decision"));
        if (options.json()) {
            out.println(Json.canonical(record));
            return resolved ? 0 : 4;
        }
        out.println(RULE);
        out.printf("  UAO FOUNDRY — IDENTITY  %s%n", value);
        out.println(RULE);
        out.printf("  %-24s %s%n", "decision", resolution.get("decision"));
        out.printf("  %-24s %s%n", "reasons", String.join(", ", strings(resolution.get("reasonCodes"))));
        if (resolved) {
            Map<String,Object> identity = map(record.get("identity"));
            out.printf("  %-24s %s%n", "uid", identity.get("uid"));
            out.printf("  %-24s %s%n", "resolution key", identity.get("resolutionKey"));
            out.printf("  %-24s %s%n", "semantic type", String.valueOf(identity.get("semanticType")));
            out.printf("  %-24s %s%n", "lifecycle", identity.get("lifecycleState"));
            out.printf("  %-24s %s%n", "semantic variants", identity.get("semanticVariantStatus"));
            out.printf("  %-24s %d%n", "state versions", list(identity.get("stateVersions")).size());
            out.printf("  %-24s %d%n", "occurrences", list(identity.get("occurrences")).size());
            out.printf("  %-24s %d%n", "identity decisions", list(identity.get("decisionHistory")).size());
            out.printf("  %-24s %d%n", "relationship bindings", list(identity.get("relationshipBindings")).size());
        } else {
            out.printf("  %-24s %d%n", "candidates shown", list(record.get("candidates")).size());
            for (Object raw : list(record.get("candidates"))) {
                out.printf("    %s  %s%n", map(raw).get("uid"), map(raw).get("resolutionKey"));
            }
        }
        out.println(RULE);
        return resolved ? 0 : 4;
    }

    private int status(Options options) {
        FoundryRegistry registry = requireRegistry(options);
        FoundryRegistry.VerificationResult verification = registry.verify();
        Map<String,Object> index = registry.index();
        int unreconciled = 0;
        int nonActive = 0;
        for (Object raw : list(index.get("identities"))) {
            Map<String,Object> identity = map(raw);
            if (SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(identity.get("semanticVariantStatus"))) unreconciled++;
            if (identity.get("lifecycleState") != null && !"ACTIVE".equals(identity.get("lifecycleState"))) nonActive++;
        }
        if (options.json()) {
            Map<String,Object> response = new LinkedHashMap<>(verification.toMap());
            response.put("unreconciledIdentities", java.math.BigDecimal.valueOf(unreconciled));
            response.put("nonActiveIdentities", java.math.BigDecimal.valueOf(nonActive));
            response.put("identityOperations", index.get("identityOperations"));
            out.println(Json.canonical(response));
            return verification.passed() ? 0 : 5;
        }
        out.println(RULE);
        out.println("  UAO FOUNDRY — REGISTRY STATUS");
        out.println(RULE);
        out.printf("  %-28s %s%n", "registry", options.registry());
        out.printf("  %-28s %s%n", "verification", verification.passed() ? "PASS" : "FAIL");
        out.printf("  %-28s %d%n", "packages", verification.packageCount());
        out.printf("  %-28s %d%n", "identities", verification.identityCount());
        out.printf("  %-28s %d%n", "unreconciled identities", unreconciled);
        out.printf("  %-28s %d%n", "non-active identities", nonActive);
        out.printf("  %-28s %d%n", "identity operations", list(index.get("identityOperations")).size());
        for (String error : verification.errors()) out.printf("  ! %s%n", error);
        out.println(RULE);
        return verification.passed() ? 0 : 5;
    }

    // ------------------------------------------------------------------ report

    /** The operator-facing summary of one manufacture, read back from artefacts rather than tallied. */
    private record Report(String seed, String context, String registryPath, String packageId, String packagePath,
                          String publicationStatus, boolean verificationPassed, String admission, String runId,
                          int reusedIdentities, int newIdentities, int newSources, int reusedRegistrySources,
                          int unresolvedRelationships, int unreconciledVariants, boolean registryConsulted) {

        static Report of(PipelineResult result, Map<String,Object> reuse, FoundryRegistry registry,
                         String admission, String seed, Options options, String runId) {
            Map<String,Object> manifest = map(FileOps.readJson(result.packagePath().resolve("manifest.json")));
            int unresolved = list(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).size();

            int reused = 0, created = 0, newSources = 0, registrySources = 0;
            if (reuse != null) {
                Map<String,Object> counts = map(reuse.get("counts"));
                reused = number(counts.get("reusedUaoCount"));
                created = number(counts.get("newUaoCount"));
                newSources = number(counts.get("newSourceCount"));
                registrySources = number(counts.get("registrySourceCount"));
            } else {
                created = list(FileOps.readJson(result.packagePath().resolve("canonical-identities.json"))).size();
                newSources = list(map(FileOps.readJson(result.packagePath().resolve("source-registry.json"))).get("sources")).size();
            }

            // Counted from the verified registry index rather than assumed zero: an identity this
            // package touches may already be in dispute from an earlier occurrence.
            int unreconciled = 0;
            if (registry != null) {
                Set<String> touched = new LinkedHashSet<>();
                for (Object raw : list(FileOps.readJson(result.packagePath().resolve("canonical-identities.json")))) {
                    touched.add(String.valueOf(map(raw).get("uid")));
                }
                for (Object raw : list(registry.index().get("identities"))) {
                    Map<String,Object> identity = map(raw);
                    if (touched.contains(String.valueOf(identity.get("uid")))
                            && SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(identity.get("semanticVariantStatus"))) {
                        unreconciled++;
                    }
                }
            }

            return new Report(seed, options.context(), options.registry() == null ? null : options.registry().toString(),
                    String.valueOf(manifest.get("packageId")), result.packagePath().toString(),
                    result.publicationStatus(), result.verificationPassed(), admission, runId,
                    reused, created, newSources, registrySources, unresolved, unreconciled, registry != null);
        }

        void print(PrintStream out) {
            out.println(RULE);
            out.println("  UAO FOUNDRY — PERSISTENT IDENTITY MANUFACTURE");
            out.println(RULE);
            out.printf("  %-30s %s%n", "Identity / Topic", seed);
            out.printf("  %-30s %s%n", "Context / keywords", context == null ? "(none)" : context);
            out.printf("  %-30s %s%n", "Registry", registryPath == null ? "(not consulted)" : registryPath);
            out.println();
            out.printf("  %-30s %d%n", "Existing identities reused", reusedIdentities);
            out.printf("  %-30s %d%n", "New identities manufactured", newIdentities);
            out.printf("  %-30s %d%n", "New sources", newSources);
            out.printf("  %-30s %d%n", "Registry sources reused", reusedRegistrySources);
            out.printf("  %-30s %d%n", "Unresolved relationships", unresolvedRelationships);
            out.printf("  %-30s %d%n", "Semantic variants", unreconciledVariants);
            out.println();
            out.printf("  %-30s %s%n", "Verification", verificationPassed ? "PASS" : "FAIL");
            out.printf("  %-30s %s%n", "Publication status", publicationStatus);
            out.printf("  %-30s %s%n", "Package ID", packageId);
            out.printf("  %-30s %s%n", "Registry admission", admission);
            if (runId != null) out.printf("  %-30s %s%n", "Run record", runId);
            out.printf("  %-30s %s%n", "Package path", packagePath);
            if (!registryConsulted) {
                out.println();
                out.println("  note: no registry was consulted, so every identity is reported as new.");
                out.println("        that is not evidence that none existed.");
            }
            if (unresolvedRelationships > 0) {
                out.println();
                out.printf("  note: %d relationship candidate(s) retained unresolved pending 17th2nd/ASA#29.%n", unresolvedRelationships);
                out.println("        canonical URO publication remains fail-closed.");
            }
            out.println(RULE);
        }

        Map<String,Object> toMap() {
            Map<String,Object> counts = new LinkedHashMap<>();
            counts.put("existingIdentitiesReused", java.math.BigDecimal.valueOf(reusedIdentities));
            counts.put("newIdentitiesManufactured", java.math.BigDecimal.valueOf(newIdentities));
            counts.put("newSources", java.math.BigDecimal.valueOf(newSources));
            counts.put("registrySourcesReused", java.math.BigDecimal.valueOf(reusedRegistrySources));
            counts.put("unresolvedRelationships", java.math.BigDecimal.valueOf(unresolvedRelationships));
            counts.put("semanticVariants", java.math.BigDecimal.valueOf(unreconciledVariants));
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("identityExpression", seed);
            out.put("context", context);
            out.put("registry", registryPath);
            out.put("registryConsulted", registryConsulted);
            out.put("counts", counts);
            out.put("verification", verificationPassed ? "PASS" : "FAIL");
            out.put("publicationStatus", publicationStatus);
            out.put("packageId", packageId);
            out.put("packagePath", packagePath);
            out.put("registryAdmission", admission);
            out.put("runId", runId);
            return out;
        }
    }

    // ------------------------------------------------------------------ plumbing

    private FoundryRegistry requireRegistry(Options options) {
        if (options.registry() == null) throw new IllegalArgumentException("This command requires --registry <path>.");
        return new FoundryRegistry(options.registry(), options.schemaDir());
    }

    /** Reads the verified index, treating an absent registry directory as an empty one. */
    private static Map<String,Object> readIndex(FoundryRegistry registry, Path root) {
        if (!Files.isDirectory(root)) {
            try { Files.createDirectories(root); }
            catch (Exception ex) { throw new IllegalArgumentException("Unable to create registry: " + ex.getMessage(), ex); }
        }
        return registry.index();
    }

    private static IdentityReference reference(String value) {
        if (value.matches("uao-[a-f0-9]{12}")) return IdentityReference.uid(value);
        if (value.startsWith("foundry:") || value.startsWith("fixture:") || value.startsWith("ext:")) {
            return IdentityReference.resolutionKey(value);
        }
        if (value.matches("[a-z][a-z0-9._-]*:\\S+")) {
            int split = value.indexOf(':');
            return IdentityReference.externalIdentifier(value.substring(0, split), value.substring(split + 1));
        }
        return IdentityReference.alias(value);
    }

    private void usage() {
        out.println("UAO Foundry — persistent identity manufacturing console");
        out.println();
        out.println("  manufacture <identity expression> [--registry <path>] [--fixture <bundle> | --provider <command>]");
        out.println("                                   [--register] [--context <keywords>] [--json]");
        out.println("  search      <query>              --registry <path> [--json]");
        out.println("  identity    <uid|key|scheme:id|alias>  --registry <path> [--json]");
        out.println("  status                           --registry <path> [--json]");
        out.println();
        out.println("Exit codes: 0 success · 2 usage/error · 4 not resolved or not publishable · 5 registry verification failed");
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) {
        if (!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("Expected an object.");
        return (Map<String,Object>) m;
    }
    private static List<Object> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> l)) throw new IllegalArgumentException("Expected an array.");
        return new ArrayList<>(l);
    }
    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        for (Object item : list(value)) out.add(String.valueOf(item));
        return out;
    }
    private static int number(Object value) {
        return value instanceof java.math.BigDecimal n ? n.intValue() : 0;
    }
}
