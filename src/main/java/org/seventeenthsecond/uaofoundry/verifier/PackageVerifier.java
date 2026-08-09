package org.seventeenthsecond.uaofoundry.verifier;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PackageVerifier {
    private final Path schemaDir;
    private final SchemaValidator validator = new SchemaValidator();

    public PackageVerifier(Path schemaDir) {
        this.schemaDir = schemaDir.toAbsolutePath().normalize();
    }

    public Result verify(Path packageDir) {
        packageDir = packageDir.toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        if (!Files.isDirectory(packageDir)) return new Result(false, List.of("Package directory does not exist: " + packageDir), List.of());

        Path checksumFile = packageDir.resolve("checksums.sha256");
        if (!Files.isRegularFile(checksumFile)) errors.add("checksums.sha256 is missing");
        else {
            checks.add("CHECKSUM_FILE_PRESENT");
            verifyChecksums(packageDir, checksumFile, errors);
        }

        Map<String, Object> manifest = readObject(packageDir.resolve("manifest.json"), "manifest", errors);
        Map<String, Object> manufactured = readObject(packageDir.resolve("manufactured-package.json"), "manufactured-package", errors);
        if (manifest != null) {
            errors.addAll(prefix(validator.validate(manifest, schemaDir.resolve("release-manifest.schema.json")).errors(), "manifest: "));
            checks.add("MANIFEST_SCHEMA");
            verifyManifestInventory(packageDir, manifest, errors);
        }
        if (manufactured != null) {
            errors.addAll(prefix(validator.validate(manufactured, schemaDir.resolve("manufactured-package.schema.json")).errors(), "manufactured-package: "));
            checks.add("MANUFACTURED_PACKAGE_SCHEMA");
            collectForbidden(manufactured, "$", errors);
            checks.add("ASA_FORBIDDEN_FIELDS");
        }
        Path providerSnapshot = packageDir.resolve("provider-snapshot.json");
        if (!Files.isRegularFile(providerSnapshot)) {
            errors.add("provider-snapshot.json is missing");
        } else {
            try {
                Object snapshot = FileOps.readJson(providerSnapshot);
                errors.addAll(prefix(validator.validate(snapshot, schemaDir.resolve("fixture-bundle.schema.json")).errors(), "provider-snapshot: "));
                checks.add("PROVIDER_SNAPSHOT_SCHEMA");
            } catch (IllegalArgumentException ex) {
                errors.add("provider-snapshot: " + ex.getMessage());
            }
        }
        if (manifest != null && manufactured != null) {
            if (!manifest.get("rootUaoId").equals(manufactured.get("rootUaoId"))) errors.add("Manifest rootUaoId differs from manufactured package.");
            Map<String, Object> pub = object(manufactured.get("publicationDecision"), "publicationDecision", errors);
            if (pub != null && !manifest.get("publicationStatus").equals(pub.get("status"))) errors.add("Manifest publication status differs from publication decision.");
        }
        verifySourceSnapshots(packageDir, errors);
        checks.add("SOURCE_SNAPSHOT_HASHES");
        return new Result(errors.isEmpty(), List.copyOf(errors), List.copyOf(checks));
    }

    private void verifyChecksums(Path packageDir, Path checksumFile, List<String> errors) {
        Set<String> listed = new LinkedHashSet<>();
        for (String line : FileOps.readText(checksumFile).split("\\R")) {
            if (line.isBlank()) continue;
            if (line.length() < 67 || !line.substring(0, 64).matches("[a-f0-9]{64}") || !line.substring(64).startsWith("  ")) {
                errors.add("Malformed checksum line: " + line); continue;
            }
            String expected = line.substring(0,64);
            String relative = line.substring(66);
            if (!listed.add(relative)) { errors.add("Duplicate checksum path: " + relative); continue; }
            Path file = packageDir.resolve(relative).normalize();
            if (!file.startsWith(packageDir)) { errors.add("Checksum path escapes package: " + relative); continue; }
            if (!Files.isRegularFile(file)) { errors.add("Checksummed file missing: " + relative); continue; }
            try {
                String actual = Hashes.sha256(Files.readAllBytes(file));
                if (!actual.equals(expected)) errors.add("Checksum mismatch: " + relative);
            } catch (Exception ex) { errors.add("Unable to checksum " + relative + ": " + ex.getMessage()); }
        }
        Set<String> actualFiles = new LinkedHashSet<>(inventory(packageDir));
        actualFiles.remove("checksums.sha256");
        if (!actualFiles.equals(listed)) {
            Set<String> missing = new LinkedHashSet<>(actualFiles); missing.removeAll(listed);
            Set<String> extra = new LinkedHashSet<>(listed); extra.removeAll(actualFiles);
            if (!missing.isEmpty()) errors.add("Files missing from checksum inventory: " + missing);
            if (!extra.isEmpty()) errors.add("Checksum entries without package files: " + extra);
        }
    }

    private void verifyManifestInventory(Path packageDir, Map<String, Object> manifest, List<String> errors) {
        Object raw = manifest.get("files");
        if (!(raw instanceof List<?> list)) { errors.add("Manifest files is not an array."); return; }
        Set<String> expected = new LinkedHashSet<>();
        for (Object item : list) if (item instanceof String s) expected.add(s); else errors.add("Manifest file entry is not a string.");
        Set<String> actual = new LinkedHashSet<>(inventory(packageDir));
        actual.remove("checksums.sha256");
        if (!expected.equals(actual)) {
            Set<String> missing = new LinkedHashSet<>(actual); missing.removeAll(expected);
            Set<String> extra = new LinkedHashSet<>(expected); extra.removeAll(actual);
            if (!missing.isEmpty()) errors.add("Manifest omits package files: " + missing);
            if (!extra.isEmpty()) errors.add("Manifest names missing files: " + extra);
        }
    }

    private void verifySourceSnapshots(Path packageDir, List<String> errors) {
        Path registryPath = packageDir.resolve("source-registry.json");
        if (!Files.isRegularFile(registryPath)) { errors.add("source-registry.json is missing"); return; }
        Object parsed = FileOps.readJson(registryPath);
        Map<String,Object> registry;
        try { registry = Json.object(parsed, "source registry"); }
        catch (IllegalArgumentException ex) { errors.add(ex.getMessage()); return; }
        Object raw = registry.get("sources");
        if (!(raw instanceof List<?> sources)) { errors.add("source registry sources is not an array"); return; }
        for (Object item : sources) {
            if (!(item instanceof Map<?,?>)) { errors.add("source registry entry is not an object"); continue; }
            @SuppressWarnings("unchecked") Map<String,Object> source = (Map<String,Object>) item;
            errors.addAll(prefix(validator.validate(source, schemaDir.resolve("source-record.schema.json")).errors(), "source: "));
            Object pathValue = source.get("snapshotPath"), hashValue = source.get("sha256");
            if (!(pathValue instanceof String relative) || !(hashValue instanceof String expected)) continue;
            Path snapshot = packageDir.resolve(relative).normalize();
            if (!snapshot.startsWith(packageDir)) { errors.add("Source snapshot escapes package: " + relative); continue; }
            if (!Files.isRegularFile(snapshot)) { errors.add("Source snapshot missing: " + relative); continue; }
            try {
                String actual = Hashes.sha256(Files.readAllBytes(snapshot));
                if (!actual.equals(expected)) errors.add("Source snapshot content hash mismatch: " + relative);
            } catch (Exception ex) { errors.add("Unable to hash source snapshot " + relative + ": " + ex.getMessage()); }
        }
    }

    private Map<String,Object> readObject(Path path, String label, List<String> errors) {
        if (!Files.isRegularFile(path)) { errors.add(label + " file is missing: " + path.getFileName()); return null; }
        try { return Json.object(FileOps.readJson(path), label); }
        catch (IllegalArgumentException ex) { errors.add(label + ": " + ex.getMessage()); return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> object(Object value, String label, List<String> errors) {
        if (!(value instanceof Map<?,?> m)) { errors.add(label + " is not an object"); return null; }
        return (Map<String,Object>) m;
    }

    private List<String> inventory(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).map(root::relativize).map(Path::toString)
                    .map(v -> v.replace('\\','/')).sorted().toList();
        } catch (Exception ex) { throw new IllegalArgumentException("Unable to inventory package: " + ex.getMessage(), ex); }
    }

    private void collectForbidden(Object value, String path, List<String> errors) {
        Set<String> forbidden = Set.of("score", "significance_value", "belief", "stance");
        if (value instanceof Map<?,?> m) {
            for (Map.Entry<?,?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (forbidden.contains(key)) errors.add("Forbidden ASA field at " + path + "." + key);
                collectForbidden(e.getValue(), path + "." + key, errors);
            }
        } else if (value instanceof List<?> l) {
            for (int i=0;i<l.size();i++) collectForbidden(l.get(i), path + "[" + i + "]", errors);
        }
    }

    private static List<String> prefix(List<String> errors, String prefix) { return errors.stream().map(e -> prefix + e).toList(); }

    public record Result(boolean passed, List<String> errors, List<String> checks) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("passed", passed); out.put("errors", new ArrayList<>(errors)); out.put("checks", new ArrayList<>(checks));
            return out;
        }
    }
}
