package org.seventeenthsecond.uaofoundry.reuse;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.provider.FoundryProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Live provider adapter that supplies a verified Foundry registry snapshot before acquisition. */
public final class RegistryAwareCommandProvider implements FoundryProvider {
    public static final String PROTOCOL_VERSION = "0.1.0";
    private static final long MAX_STDOUT_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_STDERR_BYTES = 256L * 1024L;

    private final Path source;
    private final Map<String,Object> bundle;
    private final String hash;
    private final String name;
    private final String registryContextHash;

    public RegistryAwareCommandProvider(Path command, ManufacturingRequest request, Path schemaDir,
                                        Duration timeout, Map<String,Object> registryContext, Path registryRoot) {
        this.source = command.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Provider command is not a regular file: " + source);
        if (!Files.isExecutable(source)) throw new IllegalArgumentException("Provider command is not executable: " + source);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Provider timeout must be between 1 second and 1 hour.");
        }
        this.registryContextHash = Hashes.canonicalJson(registryContext);

        Path tempDir;
        try { tempDir = Files.createTempDirectory("uao-foundry-registry-provider-"); }
        catch (Exception ex) { throw new IllegalArgumentException("Unable to create provider scratch directory: " + ex.getMessage(), ex); }
        Path stdout = tempDir.resolve("stdout.json");
        Path stderr = tempDir.resolve("stderr.txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(source.toString());
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            builder.environment().put("UAO_FOUNDRY_PROVIDER_PROTOCOL", PROTOCOL_VERSION);
            builder.environment().put("UAO_FOUNDRY_REGISTRY_ROOT", registryRoot.toAbsolutePath().normalize().toString());
            Process process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write((Json.canonical(protocolRequest(request, registryContext)) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalArgumentException("Provider command exceeded timeout of " + timeout.toSeconds() + " seconds.");
            }
            if (Files.exists(stdout) && Files.size(stdout) > MAX_STDOUT_BYTES) {
                throw new IllegalArgumentException("Provider stdout exceeded " + MAX_STDOUT_BYTES + " bytes.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("Provider command exited " + process.exitValue() + ": " + boundedText(stderr));
            }
            if (!Files.isRegularFile(stdout) || Files.size(stdout) == 0) throw new IllegalArgumentException("Provider command produced no JSON response.");
            Object parsed = Json.parse(Files.readString(stdout, StandardCharsets.UTF_8));
            SchemaValidator validator = new SchemaValidator();
            validator.validate(parsed, schemaDir.resolve("fixture-bundle.schema.json")).requireValid("Registry-aware provider response bundle");
            Map<String,Object> mutable = Json.object(Json.parse(Json.canonical(parsed)), "Registry-aware provider response bundle");
            Map<String,Object> strategy = Json.object(mutable.get("sourceStrategy"), "sourceStrategy");
            List<Object> notes = new ArrayList<>();
            Object rawNotes = strategy.get("authorityNotes");
            if (rawNotes instanceof List<?> list) notes.addAll(list);
            notes.add("Foundry registry context sha256=" + registryContextHash);
            strategy.put("authorityNotes", notes);
            validator.validate(mutable, schemaDir.resolve("fixture-bundle.schema.json")).requireValid("Registry-bound provider response bundle");
            this.bundle = mutable;
            this.hash = Hashes.canonicalJson(bundle);
            this.name = "registry-command:" + source.getFileName();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Registry-aware provider command failed: " + ex.getMessage(), ex);
        } finally {
            FileOps.deleteTree(tempDir);
        }
    }

    public String registryContextHash() { return registryContextHash; }
    public String name() { return name; }
    public String kind() { return "registry-command"; }
    public String executionMode() { return "live"; }
    public String hash() { return hash; }
    public Path source() { return source; }
    public Map<String,Object> snapshot() { return Json.object(Json.parse(Json.canonical(bundle)), "Registry provider snapshot"); }
    public String identitySeed() { return string("identitySeed"); }
    public String fixedClock() { return string("fixedClock"); }
    public String knowledgeHorizon() { return string("knowledgeHorizon"); }
    @SuppressWarnings("unchecked") public List<Object> interpretations() { return (List<Object>) bundle.get("interpretations"); }
    public Map<String,Object> scopeResolution() { return Json.object(bundle.get("scopeResolution"), "scopeResolution"); }
    public Map<String,Object> manufacturingPlan() { return Json.object(bundle.get("manufacturingPlan"), "manufacturingPlan"); }
    public Map<String,Object> sourceStrategy() { return Json.object(bundle.get("sourceStrategy"), "sourceStrategy"); }
    @SuppressWarnings("unchecked") public List<Object> sources() { return (List<Object>) bundle.get("sources"); }
    public Map<String,Object> candidates() { return Json.object(bundle.get("candidates"), "candidates"); }
    public Map<String,Object> coverageAnswers() { return Json.object(bundle.get("coverageAnswers"), "coverageAnswers"); }

    private Map<String,Object> protocolRequest(ManufacturingRequest request, Map<String,Object> registryContext) {
        Map<String,Object> constraints = new LinkedHashMap<>();
        constraints.put("canonicalWriteAllowed", false);
        constraints.put("responseSchema", "fixture-bundle.schema.json");
        constraints.put("responseRole", "INTERMEDIATE_PROVIDER_BUNDLE_ONLY");
        constraints.put("reusePreference", "REUSE_VERIFIED_REGISTRY_IDENTITIES_BEFORE_NEW_ACQUISITION");
        Map<String,Object> envelope = new LinkedHashMap<>();
        envelope.put("protocolVersion", PROTOCOL_VERSION);
        envelope.put("request", request.toMap());
        envelope.put("registryContext", registryContext);
        envelope.put("constraints", constraints);
        return envelope;
    }

    private String string(String key) {
        Object value = bundle.get(key);
        if (value instanceof String s) return s;
        throw new IllegalArgumentException("Provider field must be string: " + key);
    }

    private static String boundedText(Path path) {
        try {
            if (!Files.isRegularFile(path)) return "<no stderr>";
            int cap = Math.toIntExact(MAX_STDERR_BYTES);
            byte[] bytes;
            try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(cap + 1); }
            boolean truncated = bytes.length > cap;
            String text = new String(bytes, 0, Math.min(bytes.length, cap), StandardCharsets.UTF_8).strip();
            return truncated ? text + " …<truncated>" : text;
        } catch (Exception ex) { return "<stderr unavailable: " + ex.getMessage() + ">"; }
    }
}
