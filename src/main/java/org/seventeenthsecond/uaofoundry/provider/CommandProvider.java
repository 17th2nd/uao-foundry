package org.seventeenthsecond.uaofoundry.provider;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Vendor-neutral live provider adapter. The configured executable receives one protocol
 * request as JSON on stdin and must emit one validated provider bundle on stdout.
 * ProcessBuilder is used directly; no shell interpolation is performed.
 */
public final class CommandProvider implements FoundryProvider {
    public static final String PROTOCOL_VERSION = "0.1.0";
    public static final long MAX_STDOUT_BYTES = 16L * 1024L * 1024L;
    public static final long MAX_STDERR_BYTES = 256L * 1024L;

    private final Path source;
    private final Map<String, Object> bundle;
    private final String hash;
    private final String name;

    public CommandProvider(Path command, ManufacturingRequest request, Path schemaDir, Duration timeout) {
        this.source = command.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Provider command is not a regular file: " + source);
        if (!Files.isExecutable(source)) throw new IllegalArgumentException("Provider command is not executable: " + source);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Provider timeout must be between 1 second and 1 hour.");
        }

        Path tempDir;
        try { tempDir = Files.createTempDirectory("uao-foundry-provider-"); }
        catch (Exception ex) { throw new IllegalArgumentException("Unable to create provider scratch directory: " + ex.getMessage(), ex); }
        Path stdout = tempDir.resolve("stdout.json");
        Path stderr = tempDir.resolve("stderr.txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(source.toString());
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            builder.environment().put("UAO_FOUNDRY_PROVIDER_PROTOCOL", PROTOCOL_VERSION);
            Process process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write((Json.canonical(protocolRequest(request)) + "\n").getBytes(StandardCharsets.UTF_8));
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
                throw new IllegalArgumentException("Provider command exited " + process.exitValue() + ": " + boundedText(stderr, MAX_STDERR_BYTES));
            }
            if (!Files.isRegularFile(stdout) || Files.size(stdout) == 0) throw new IllegalArgumentException("Provider command produced no JSON response.");
            Object parsed = Json.parse(Files.readString(stdout, StandardCharsets.UTF_8));
            new SchemaValidator().validate(parsed, schemaDir.resolve("fixture-bundle.schema.json"))
                    .requireValid("Command provider response bundle");
            this.bundle = Json.object(parsed, "Command provider response bundle");
            this.hash = Hashes.canonicalJson(bundle);
            this.name = "command:" + source.getFileName();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Provider command failed: " + ex.getMessage(), ex);
        } finally {
            FileOps.deleteTree(tempDir);
        }
    }

    public String name() { return name; }
    public String kind() { return "command"; }
    public String executionMode() { return "live"; }
    public String hash() { return hash; }
    public Path source() { return source; }
    public Map<String, Object> snapshot() { return Json.object(Json.parse(Json.canonical(bundle)), "Command provider snapshot"); }
    public String identitySeed() { return string("identitySeed"); }
    public String fixedClock() { return string("fixedClock"); }
    public String knowledgeHorizon() { return string("knowledgeHorizon"); }
    @SuppressWarnings("unchecked") public List<Object> interpretations() { return (List<Object>) bundle.get("interpretations"); }
    public Map<String, Object> scopeResolution() { return Json.object(bundle.get("scopeResolution"), "scopeResolution"); }
    public Map<String, Object> manufacturingPlan() { return Json.object(bundle.get("manufacturingPlan"), "manufacturingPlan"); }
    public Map<String, Object> sourceStrategy() { return Json.object(bundle.get("sourceStrategy"), "sourceStrategy"); }
    @SuppressWarnings("unchecked") public List<Object> sources() { return (List<Object>) bundle.get("sources"); }
    public Map<String, Object> candidates() { return Json.object(bundle.get("candidates"), "candidates"); }
    public Map<String, Object> coverageAnswers() { return Json.object(bundle.get("coverageAnswers"), "coverageAnswers"); }

    private Map<String, Object> protocolRequest(ManufacturingRequest request) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("canonicalWriteAllowed", false);
        constraints.put("responseSchema", "fixture-bundle.schema.json");
        constraints.put("responseRole", "INTERMEDIATE_PROVIDER_BUNDLE_ONLY");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("protocolVersion", PROTOCOL_VERSION);
        envelope.put("request", request.toMap());
        envelope.put("constraints", constraints);
        return envelope;
    }

    private String string(String key) {
        Object value = bundle.get(key);
        if (value instanceof String s) return s;
        throw new IllegalArgumentException("Provider field must be string: " + key);
    }

    private static String boundedText(Path path, long maxBytes) {
        try {
            if (!Files.isRegularFile(path)) return "<no stderr>";
            int cap = Math.toIntExact(Math.min(maxBytes, Integer.MAX_VALUE - 1L));
            byte[] bytes;
            try (var input = Files.newInputStream(path)) {
                bytes = input.readNBytes(cap + 1);
            }
            boolean truncated = bytes.length > cap;
            int length = Math.min(bytes.length, cap);
            String text = new String(bytes, 0, length, StandardCharsets.UTF_8).strip();
            return truncated ? text + " …<truncated>" : text;
        } catch (Exception ex) {
            return "<stderr unavailable: " + ex.getMessage() + ">";
        }
    }
}
