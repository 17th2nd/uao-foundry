package org.seventeenthsecond.uaofoundry;

import org.seventeenthsecond.uaofoundry.cli.Arguments;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.validation.ValidationResult;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FoundryApplication {
    private static final String FOUNDRY_VERSION = FoundryPipeline.FOUNDRY_VERSION;

    private final PrintStream out;
    private final PrintStream err;

    public FoundryApplication() { this(System.out, System.err); }
    FoundryApplication(PrintStream out, PrintStream err) { this.out = out; this.err = err; }

    public static void main(String[] args) {
        int exitCode = new FoundryApplication().run(args);
        if (exitCode != 0) System.exit(exitCode);
    }

    int run(String[] args) {
        if (args.length == 0) { printUsage(); return 2; }
        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        return switch (command) {
            case "manufacture" -> manufacture(commandArgs, false);
            case "validate-request" -> validateRequest(commandArgs);
            case "interpret" -> interpret(commandArgs);
            case "status" -> status(commandArgs);
            case "resume" -> resume(commandArgs);
            case "verify" -> verify(commandArgs);
            case "inspect" -> inspect(commandArgs);
            case "help", "--help", "-h" -> { printUsage(); yield 0; }
            default -> { err.println("Unknown command: " + command); printUsage(); yield 2; }
        };
    }

    private int manufacture(String[] args, boolean resume) {
        try {
            Arguments parsed = Arguments.parse(args);
            Path schemaDir = path(parsed, "--schema-dir", "schemas");
            Path workDir = path(parsed, "--work-dir", "work");
            Path distDir = path(parsed, "--dist-dir", "dist");
            RequestLoader loader = new RequestLoader(schemaDir.resolve("manufacturing-request.schema.json"));
            ManufacturingRequest request;
            if (parsed.optional("--request").isPresent()) {
                if (!parsed.positionals().isEmpty() || parsed.optional("--identity").isPresent()) throw new IllegalArgumentException("--request cannot be combined with an identity seed.");
                request = loader.fromFile(Path.of(parsed.required("--request")));
            } else {
                request = loader.fromSeed(parsed.identitySeed(), parsed.optional("--language").orElse(null), parsed.optional("--profile").orElse(null));
            }
            String fixtureArg = parsed.optional("--fixture").orElseThrow(() -> new IllegalArgumentException(
                    "Full v0.1 manufacture requires a provider. Use --fixture <bundle.json>. Live/AI adapters are intentionally fail-closed until configured."));
            FixtureProvider provider = new FixtureProvider(Path.of(fixtureArg), schemaDir);
            FoundryPipeline pipeline = new FoundryPipeline(schemaDir, workDir, distDir, repositoryCommit(parsed));
            PipelineResult result = pipeline.manufacture(request, provider, resume);
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("foundryVersion", FOUNDRY_VERSION);
            response.put("phase", "PACKAGE_MANUFACTURED");
            response.putAll(result.toMap());
            out.println(Json.canonical(response));
            return successfulStatus(result.publicationStatus()) && result.verificationPassed() ? 0 : 4;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }
    }

    private int validateRequest(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            if (parsed.positionals().size() != 1) throw new IllegalArgumentException("validate-request requires exactly one request file path.");
            Path schemaDir = path(parsed, "--schema-dir", "schemas");
            RequestLoader loader = new RequestLoader(schemaDir.resolve("manufacturing-request.schema.json"));
            ValidationResult result = loader.validateRaw(Path.of(parsed.positionals().getFirst()));
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("valid", result.valid()); response.put("errors", new ArrayList<>(result.errors()));
            out.println(Json.canonical(response));
            return result.valid() ? 0 : 3;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private int interpret(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            String seed = parsed.identitySeed();
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("identitySeed", seed);
            response.put("normalisedExpression", seed.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT));
            if (parsed.optional("--fixture").isEmpty()) {
                response.put("status", "PROVIDER_REQUIRED");
                response.put("interpretations", List.of());
                response.put("publicationStatus", "NOT_PUBLISHED");
            } else {
                Path schemaDir = path(parsed, "--schema-dir", "schemas");
                FixtureProvider provider = new FixtureProvider(Path.of(parsed.required("--fixture")), schemaDir);
                response.put("status", "INTERPRETATIONS_AVAILABLE");
                response.put("provider", provider.name());
                response.put("interpretations", provider.interpretations());
                response.put("scopeResolution", provider.scopeResolution());
                response.put("publicationStatus", "NOT_PUBLISHED");
            }
            out.println(Json.canonical(response));
            return 0;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private int status(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            if (parsed.positionals().size() != 1) throw new IllegalArgumentException("status requires exactly one job id.");
            Path jobDir = path(parsed, "--work-dir", "work").resolve(parsed.positionals().getFirst());
            Path checkpoint = jobDir.resolve("checkpoint.json");
            if (!Files.isRegularFile(checkpoint)) throw new IllegalArgumentException("Job checkpoint not found: " + checkpoint);
            Map<String,Object> cp = Json.object(FileOps.readJson(checkpoint), "checkpoint");
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("jobId", cp.get("jobId"));
            Object completed = cp.get("completed");
            response.put("completedStages", completed instanceof Map<?,?> m ? new java.math.BigDecimal(m.size()) : java.math.BigDecimal.ZERO);
            Path packageStage = jobDir.resolve("16-package-manufacture.json");
            if (Files.isRegularFile(packageStage)) response.put("package", FileOps.readJson(packageStage));
            out.println(Json.canonical(response));
            return 0;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private int resume(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            if (parsed.positionals().size() != 1) throw new IllegalArgumentException("resume requires exactly one job id.");
            String requestedJobId = parsed.positionals().getFirst();
            Path schemaDir = path(parsed, "--schema-dir", "schemas");
            Path workDir = path(parsed, "--work-dir", "work");
            Path distDir = path(parsed, "--dist-dir", "dist");
            Map<String,Object> cp = Json.object(FileOps.readJson(workDir.resolve(requestedJobId).resolve("checkpoint.json")), "checkpoint");
            Map<String,Object> requestMap = Json.object(cp.get("request"), "checkpoint request");
            RequestLoader loader = new RequestLoader(schemaDir.resolve("manufacturing-request.schema.json"));
            ManufacturingRequest request = loader.fromObject(requestMap);
            String providerSource = requiredString(cp.get("providerSource"), "checkpoint providerSource");
            FixtureProvider provider = new FixtureProvider(Path.of(providerSource), schemaDir);
            String originalCommit = cp.get("repositoryCommit") instanceof String saved ? saved : "UNPINNED";
            String resumeCommit = parsed.optional("--repository-commit").orElse(originalCommit);
            FoundryPipeline pipeline = new FoundryPipeline(schemaDir, workDir, distDir, resumeCommit);
            PipelineResult result = pipeline.manufacture(request, provider, true);
            if (!requestedJobId.equals(result.jobId())) throw new IllegalArgumentException("Checkpoint job id does not match recomputed deterministic job id.");
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("foundryVersion", FOUNDRY_VERSION); response.put("phase", "JOB_RESUMED"); response.putAll(result.toMap());
            out.println(Json.canonical(response));
            return successfulStatus(result.publicationStatus()) && result.verificationPassed() ? 0 : 4;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private int verify(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            if (parsed.positionals().size() != 1) throw new IllegalArgumentException("verify requires exactly one package path.");
            Path schemaDir = path(parsed, "--schema-dir", "schemas");
            PackageVerifier.Result result = new PackageVerifier(schemaDir).verify(Path.of(parsed.positionals().getFirst()));
            out.println(Json.canonical(result.toMap()));
            return result.passed() ? 0 : 5;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private int inspect(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            if (parsed.positionals().size() != 1) throw new IllegalArgumentException("inspect requires exactly one package path.");
            Path packageDir = Path.of(parsed.positionals().getFirst());
            Map<String,Object> manifest = Json.object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
            Map<String,Object> coverage = Json.object(FileOps.readJson(packageDir.resolve("coverage-report.json")), "coverage");
            Map<String,Object> verification = Json.object(FileOps.readJson(packageDir.resolve("verification-report.json")), "verification");
            Object unresolved = FileOps.readJson(packageDir.resolve("unresolved-items.json"));
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("packageId", manifest.get("packageId")); response.put("rootUaoId", manifest.get("rootUaoId"));
            response.put("publicationStatus", manifest.get("publicationStatus")); response.put("coverage", coverage);
            response.put("verification", verification); response.put("unresolvedItems", unresolved);
            out.println(Json.canonical(response));
            return 0;
        } catch (IllegalArgumentException ex) { err.println(ex.getMessage()); return 2; }
    }

    private String repositoryCommit(Arguments parsed) {
        return parsed.optional("--repository-commit").orElseGet(() -> {
            String sha = System.getenv("GITHUB_SHA");
            return sha == null || sha.isBlank() ? "UNPINNED" : sha;
        });
    }

    private static Path path(Arguments parsed, String option, String fallback) { return Path.of(parsed.optional(option).orElse(fallback)); }
    private static String requiredString(Object value, String label) { if (value instanceof String s && !s.isBlank()) return s; throw new IllegalArgumentException(label + " must be a non-blank string."); }
    private static boolean successfulStatus(String status) { return "EXPERIMENTAL".equals(status) || "STRUCTURALLY_COMPLETE".equals(status) || "VALIDATED_RELEASE".equals(status); }

    private void printUsage() {
        out.println("UAO Foundry " + FOUNDRY_VERSION);
        out.println("Usage:");
        out.println("  uao-foundry manufacture <identity-seed> --fixture <bundle.json> [--work-dir work] [--dist-dir dist]");
        out.println("  uao-foundry manufacture --request <request.json> --fixture <bundle.json>");
        out.println("  uao-foundry validate-request <request.json>");
        out.println("  uao-foundry interpret <identity-seed> [--fixture <bundle.json>]");
        out.println("  uao-foundry status <job-id> [--work-dir work]");
        out.println("  uao-foundry resume <job-id> [--work-dir work] [--dist-dir dist]");
        out.println("  uao-foundry verify <package-path>");
        out.println("  uao-foundry inspect <package-path>");
        out.println("Common: --schema-dir <dir> --repository-commit <sha>");
    }
}
