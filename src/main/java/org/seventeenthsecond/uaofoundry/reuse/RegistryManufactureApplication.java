package org.seventeenthsecond.uaofoundry.reuse;

import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry-aware live manufacture entry point with Foundry-computed reuse/delta reporting. */
public final class RegistryManufactureApplication {
    private final PrintStream out;
    private final PrintStream err;

    public RegistryManufactureApplication() { this(System.out, System.err); }
    RegistryManufactureApplication(PrintStream out, PrintStream err) { this.out = out; this.err = err; }

    public static void main(String[] args) {
        int exit = new RegistryManufactureApplication().run(args);
        if (exit != 0) System.exit(exit);
    }

    int run(String[] args) {
        try {
            Parsed parsed = parse(args);
            Path schemaDir = parsed.schemaDir().toAbsolutePath().normalize();
            FoundryRegistry registry = new FoundryRegistry(parsed.registry(), schemaDir);
            initialiseOrVerifyRegistry(parsed.registry(), registry);
            Map<String,Object> preIndex = registry.index();

            RequestLoader loader = new RequestLoader(schemaDir.resolve("manufacturing-request.schema.json"));
            ManufacturingRequest request = loader.fromSeed(parsed.identity(), parsed.language(), parsed.profile(), "live");
            Map<String,Object> registryContext = registry.discoveryContext(request.identitySeed(), parsed.catalogLimit());
            String registryContextHash = Hashes.canonicalJson(registryContext);
            RegistryAwareCommandProvider provider = new RegistryAwareCommandProvider(
                    parsed.providerCommand(), request, schemaDir, Duration.ofSeconds(parsed.timeoutSeconds()), registryContext, parsed.registry());
            if (!registryContextHash.equals(provider.registryContextHash())) throw new IllegalArgumentException("Registry context hash changed before provider acquisition.");

            FoundryPipeline pipeline = new FoundryPipeline(schemaDir, parsed.workDir(), parsed.distDir(), parsed.repositoryCommit(), parsed.registry(), preIndex);
            PipelineResult result = pipeline.manufacture(request, provider, false);
            ReuseAnalyzer analyzer = new ReuseAnalyzer(schemaDir);
            Map<String,Object> report = analyzer.analyze(preIndex, parsed.registry(), result.packagePath(), registryContextHash);
            new SchemaValidator().validate(report, schemaDir.resolve("reuse-report.schema.json")).requireValid("Reuse report");
            analyzer.attachAndVerify(result.packagePath(), report);

            Map<String,Object> response = new LinkedHashMap<>();
            response.put("phase", "REGISTRY_AWARE_PACKAGE_MANUFACTURED");
            response.put("jobId", result.jobId());
            response.put("packagePath", result.packagePath().toString());
            response.put("publicationStatus", result.publicationStatus());
            response.put("rootUaoId", result.rootUaoId());
            response.put("verificationPassed", result.verificationPassed());
            response.put("registryContextHash", registryContextHash);
            response.put("reuse", report);
            if (parsed.register()) {
                FoundryRegistry.RegistrationResult registration = registry.register(result.packagePath());
                response.put("registryRegistration", registration.toMap());
            }
            out.println(Json.canonical(response));
            return result.verificationPassed() && isEligibleStatus(result.publicationStatus()) ? 0 : 4;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }
    }

    private void initialiseOrVerifyRegistry(Path root, FoundryRegistry registry) {
        Path absolute = root.toAbsolutePath().normalize();
        Path index = absolute.resolve("index.json");
        Path packages = absolute.resolve("packages");
        if (!Files.exists(index)) {
            boolean hasPackages = false;
            if (Files.isDirectory(packages)) {
                try (var stream = Files.list(packages)) { hasPackages = stream.findAny().isPresent(); }
                catch (Exception ex) { throw new IllegalArgumentException("Unable to inspect registry packages: " + ex.getMessage(), ex); }
            }
            if (hasPackages) throw new IllegalArgumentException("Registry has packages but no index; run RegistryApplication rebuild explicitly.");
            registry.rebuildAndPersist();
            return;
        }
        FoundryRegistry.VerificationResult verification = registry.verify();
        if (!verification.passed()) throw new IllegalArgumentException("Registry verification failed before manufacture: " + String.join("; ", verification.errors()));
    }

    private Parsed parse(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0])) {
            throw new IllegalArgumentException("Usage: RegistryManufactureApplication <identity> --provider-command <executable> [--registry .uao-registry] [--register]");
        }
        String identity = args[0];
        Path provider = null, registry = Path.of(".uao-registry"), schemaDir = Path.of("schemas"), workDir = Path.of("work"), distDir = Path.of("dist");
        String language = "en", profile = "experimental", repositoryCommit = environmentCommit();
        long timeout = 300;
        int catalogLimit = 5000;
        boolean register = false;
        for (int i=1;i<args.length;i++) {
            String token = args[i];
            if ("--register".equals(token)) { register = true; continue; }
            if (!token.startsWith("--") || i+1 >= args.length) throw new IllegalArgumentException("Option requires a value: " + token);
            String value = args[++i];
            switch (token) {
                case "--provider-command" -> provider = Path.of(value);
                case "--registry" -> registry = Path.of(value);
                case "--schema-dir" -> schemaDir = Path.of(value);
                case "--work-dir" -> workDir = Path.of(value);
                case "--dist-dir" -> distDir = Path.of(value);
                case "--language" -> language = value;
                case "--profile" -> profile = value;
                case "--repository-commit" -> repositoryCommit = value;
                case "--provider-timeout-seconds" -> timeout = boundedLong(value, 1, 3600, token);
                case "--catalog-limit" -> catalogLimit = Math.toIntExact(boundedLong(value, 1, 100000, token));
                default -> throw new IllegalArgumentException("Unknown option: " + token);
            }
        }
        if (provider == null) throw new IllegalArgumentException("--provider-command is required for registry-aware live manufacture.");
        return new Parsed(identity, provider, registry, schemaDir, workDir, distDir, language, profile, repositoryCommit, timeout, catalogLimit, register);
    }

    private static long boundedLong(String value, long min, long max, String option) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < min || parsed > max) throw new IllegalArgumentException(option + " must be between " + min + " and " + max + ".");
            return parsed;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException(option + " must be an integer."); }
    }

    private static String environmentCommit() {
        String sha = System.getenv("GITHUB_SHA");
        return sha == null || sha.isBlank() ? "UNPINNED" : sha;
    }
    private static boolean isEligibleStatus(String status) { return "EXPERIMENTAL".equals(status) || "STRUCTURALLY_COMPLETE".equals(status) || "VALIDATED_RELEASE".equals(status); }

    private record Parsed(String identity, Path providerCommand, Path registry, Path schemaDir, Path workDir, Path distDir,
                          String language, String profile, String repositoryCommit, long timeoutSeconds, int catalogLimit, boolean register) {}
}
