package org.seventeenthsecond.uaofoundry.pipeline;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.significance.SignificanceBoundary;
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

/** Shared deterministic pipeline state, checkpointing, hashing and contract helpers. */
class PipelineBase {
    protected static final String VERSION = "0.1.0";
    protected static final List<String> STAGES = List.of(
            "01_JOB_INITIALISATION", "02_SEED_NORMALISATION", "03_IDENTITY_INTERPRETATION",
            "04_SCOPE_RESOLUTION", "05_MANUFACTURING_PLANNING", "06_SOURCE_STRATEGY",
            "07_SOURCE_ACQUISITION", "08_KNOWLEDGE_EXTRACTION", "09_CANDIDATE_VALIDATION",
            "10_IDENTITY_RESOLUTION", "11_RELATIONSHIP_CONSTRUCTION", "12_CANONICAL_BUILD",
            "13_COMPLETENESS_ANALYSIS", "14_VERIFICATION", "15_PUBLICATION_DECISION",
            "16_PACKAGE_MANUFACTURE"
    );
    protected final Path schemaDir;
    protected final Path workDir;
    protected final Path distDir;
    protected final String repositoryCommit;
    protected final SchemaValidator validator = new SchemaValidator();
    protected Path jobDir;
    protected String jobId;
    protected Map<String, Object> checkpoint;
    protected int resumedStages;
    protected List<String> invalidatedStages;
    protected final Path registryRoot;
    protected final Map<String,Object> registryIndex;
    /** Optional RTR-format relationship type edition. Null keeps stage 11 on the ASA#29 fail-closed path. */
    protected org.seventeenthsecond.uaofoundry.relationship.RelationshipTypeEdition relationshipEdition;
    protected PipelineBase(Path schemaDir, Path workDir, Path distDir, String repositoryCommit) {
        this(schemaDir, workDir, distDir, repositoryCommit, null, null);
    }
    protected PipelineBase(Path schemaDir, Path workDir, Path distDir, String repositoryCommit, Path registryRoot, Map<String,Object> registryIndex) {
        this.schemaDir = schemaDir.toAbsolutePath().normalize();
        this.workDir = workDir.toAbsolutePath().normalize();
        this.distDir = distDir.toAbsolutePath().normalize();
        this.repositoryCommit = repositoryCommit == null || repositoryCommit.isBlank() ? "UNPINNED" : repositoryCommit;
        this.registryRoot = registryRoot == null ? null : registryRoot.toAbsolutePath().normalize();
        this.registryIndex = registryIndex == null ? null : deepCopyMap(registryIndex);
    }

    protected Object stage(String stageName, String fileName, Supplier<Object> compute) {
        Path path = jobDir.resolve(fileName);
        Object computed = compute.get();
        Map<String, Object> completed = map(checkpoint.get("completed"));
        boolean hadRecord = completed.get(stageName) instanceof Map<?, ?>;
        Object cached = cachedStage(stageName, path);
        if (cached != null) {
            try {
                if (Json.canonical(cached).equals(Json.canonical(computed))) {
                    resumedStages++;
                    return cached;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through and replace the invalid cached projection
            }
            invalidatedStages.add(stageName + ": cached projection differs from deterministic re-derivation");
        } else if (hadRecord) {
            invalidatedStages.add(stageName + ": checkpoint hash/file validation failed; stage recomputed");
        }
        FileOps.writeJson(path, computed);
        markStage(stageName, path);
        return computed;
    }

    protected Object cachedStage(String stageName, Path path) {
        Map<String, Object> completed = map(checkpoint.get("completed"));
        Object raw = completed.get(stageName);
        if (!(raw instanceof Map<?, ?>)) return null;
        Map<String, Object> record = map(raw);
        if (!Files.isRegularFile(path)) return null;
        String expected = string(record.get("sha256"), "checkpoint sha256");
        String actual = Hashes.sha256(FileOps.readText(path).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(actual)) return null;
        return FileOps.readJson(path);
    }

    protected void markStage(String stageName, Path path) {
        Map<String, Object> completed = map(checkpoint.get("completed"));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("file", jobDir.relativize(path).toString().replace('\\','/'));
        record.put("sha256", Hashes.sha256(FileOps.readText(path).getBytes(StandardCharsets.UTF_8)));
        completed.put(stageName, record);
        FileOps.writeJson(jobDir.resolve("checkpoint.json"), checkpoint);
    }

    protected Map<String, Object> loadCheckpoint(boolean resume) {
        Path path = jobDir.resolve("checkpoint.json");
        if (resume && Files.isRegularFile(path)) {
            Map<String, Object> cp = map(FileOps.readJson(path));
            if (!jobId.equals(cp.get("jobId"))) throw new IllegalArgumentException("Checkpoint belongs to another job.");
            if (!(cp.get("completed") instanceof Map<?, ?>)) throw new IllegalArgumentException("Checkpoint completed map missing.");
            return cp;
        }
        Map<String, Object> cp = new LinkedHashMap<>();
        cp.put("jobId", jobId);
        cp.put("completed", new LinkedHashMap<String, Object>());
        return cp;
    }

    protected byte[] verifiedRegistryBytes(String locator) {
        if (registryRoot == null || registryIndex == null) {
            throw new IllegalArgumentException("registry:// evidence requires a verified registry-aware manufacture context: " + locator);
        }
        if (!locator.startsWith("registry://")) throw new IllegalArgumentException("Not a registry locator: " + locator);
        String remainder = locator.substring("registry://".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0 || slash == remainder.length() - 1) throw new IllegalArgumentException("Invalid registry source locator: " + locator);
        String packageId = remainder.substring(0, slash);
        String relative = remainder.substring(slash + 1);
        Map<String,Object> packageRecord = null;
        for (Object raw : list(registryIndex.get("packages"), "registry packages")) {
            Map<String,Object> record = map(raw);
            if (packageId.equals(record.get("packageId"))) { packageRecord = record; break; }
        }
        if (packageRecord == null) throw new IllegalArgumentException("Registry source references unknown package: " + packageId);
        Path packageRoot = registryRoot.resolve("packages").resolve(packageId).normalize();
        if (!packageRoot.startsWith(registryRoot)) throw new IllegalArgumentException("Registry package path escapes registry root: " + packageId);
        String expectedDigest = string(packageRecord.get("packageDigest"), "registry packageDigest");
        if (!expectedDigest.equals(FileOps.treeHash(packageRoot))) throw new IllegalArgumentException("Registry package digest changed before evidence custody: " + packageId);
        Path path = packageRoot.resolve(relative).normalize();
        if (!path.startsWith(packageRoot)) throw new IllegalArgumentException("Registry source path escapes immutable package: " + locator);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Registry source file does not exist: " + locator);
        try { return Files.readAllBytes(path); }
        catch (Exception ex) { throw new IllegalArgumentException("Unable to read registry evidence " + locator + ": " + ex.getMessage(), ex); }
    }

    protected void validate(Object value, String schemaFile, String label) {
        validator.validate(value, schemaDir.resolve(schemaFile)).requireValid(label);
    }

    protected void rejectForbiddenFields(Object value, String path) {
        List<String> errors = new ArrayList<>();
        collectForbiddenFields(value, path, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    }

    protected void collectForbiddenFields(Object value, String path, List<String> errors) {
        SignificanceBoundary.collect(value, path, errors);
    }

    protected Set<String> sourceIds(Map<String, Object> registry) {
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : list(registry.get("sources"), "sources")) ids.add(string(map(raw).get("sourceId"), "sourceId"));
        return ids;
    }

    protected void writeChecksums(Path packageDir) {
        List<String> files = packageFiles(packageDir, false);
        StringBuilder out = new StringBuilder();
        for (String relative : files) {
            if ("checksums.sha256".equals(relative)) continue;
            Path file = packageDir.resolve(relative);
            try { out.append(Hashes.sha256(Files.readAllBytes(file))).append("  ").append(relative).append('\n'); }
            catch (Exception ex) { throw new IllegalArgumentException("Unable to checksum " + file + ": " + ex.getMessage(), ex); }
        }
        FileOps.writeText(packageDir.resolve("checksums.sha256"), out.toString());
    }

    protected List<String> packageFiles(Path packageDir, boolean includeChecksum) {
        try (var stream = Files.walk(packageDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(packageDir::relativize).map(Path::toString).map(v -> v.replace('\\','/'))
                    .filter(v -> includeChecksum || !"checksums.sha256".equals(v))
                    .sorted().toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to inventory package: " + ex.getMessage(), ex);
        }
    }

    protected static String normalise(String input) {
        return input.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    protected static String slug(String label) {
        String slug = label.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "UNRESOLVED" : slug;
    }

    protected static void ensureUniqueIds(List<Object> records, String field, String label) {
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : records) {
            String id = string(map(raw).get(field), field);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate " + label + " id: " + id);
        }
    }

    protected static List<String> prefix(List<String> errors, String prefix) {
        return errors.stream().map(v -> prefix + v).toList();
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> m)) throw new IllegalArgumentException("Expected JSON object, got " + (value == null ? "null" : value.getClass().getSimpleName()));
        return (Map<String, Object>) m;
    }
    @SuppressWarnings("unchecked")
    protected static List<Object> list(Object value, String label) {
        if (!(value instanceof List<?> l)) throw new IllegalArgumentException(label + " must be an array.");
        return (List<Object>) l;
    }
    @SuppressWarnings("unchecked")
    protected static List<Object> listOrEmpty(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> l)) throw new IllegalArgumentException("Expected array.");
        return (List<Object>) l;
    }
    protected static String string(Object value, String label) {
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(label + " must be a string.");
    }
    protected static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        throw new IllegalArgumentException("Expected boolean.");
    }
    protected static Object deepCopy(Object value) { return Json.parse(Json.canonical(value)); }
    protected static Map<String, Object> deepCopyMap(Map<String, Object> value) { return map(deepCopy(value)); }
    protected static List<Object> deepCopyList(List<Object> value) { return list(deepCopy(value), "copied list"); }
}
