package org.seventeenthsecond.uaofoundry.verifier;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
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
        Object canonicalIdentities = readValue(packageDir.resolve("canonical-identities.json"), "canonical-identities", errors);
        Object canonicalRelationships = readValue(packageDir.resolve("canonical-relationships.json"), "canonical-relationships", errors);
        Map<String, Object> request = readObject(packageDir.resolve("manufacturing-request.json"), "manufacturing-request", errors);
        Map<String, Object> publication = readObject(packageDir.resolve("publication-decision.json"), "publication-decision", errors);
        Map<String, Object> verification = readObject(packageDir.resolve("verification-report.json"), "verification-report", errors);

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

        verifyCrossFileConsistency(manifest, manufactured, canonicalIdentities, canonicalRelationships, request, publication, verification, errors);
        checks.add("PACKAGE_CROSS_FILE_CONSISTENCY");
        verifyCanonicalIdentityDerivations(manifest, canonicalIdentities, errors);
        checks.add("UAO_IDENTITY_DERIVATION");

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

        verifySourceSnapshots(packageDir, errors);
        checks.add("SOURCE_SNAPSHOT_HASHES");
        return new Result(errors.isEmpty(), List.copyOf(errors), List.copyOf(checks));
    }

    private void verifyCrossFileConsistency(
            Map<String, Object> manifest,
            Map<String, Object> manufactured,
            Object canonicalIdentities,
            Object canonicalRelationships,
            Map<String, Object> request,
            Map<String, Object> publication,
            Map<String, Object> verification,
            List<String> errors) {
        if (manufactured == null) return;

        compareJson("manufactured-package.uaos", manufactured.get("uaos"), "canonical-identities.json", canonicalIdentities, errors);
        compareJson("manufactured-package.uros", manufactured.get("uros"), "canonical-relationships.json", canonicalRelationships, errors);
        compareJson("manufactured-package.request", manufactured.get("request"), "manufacturing-request.json", request, errors);
        compareJson("manufactured-package.publicationDecision", manufactured.get("publicationDecision"), "publication-decision.json", publication, errors);
        compareJson("manufactured-package.verification", manufactured.get("verification"), "verification-report.json", verification, errors);

        if (manifest != null) {
            Object manifestRoot = manifest.get("rootUaoId");
            Object manufacturedRoot = manufactured.get("rootUaoId");
            if (!java.util.Objects.equals(manifestRoot, manufacturedRoot)) errors.add("Manifest rootUaoId differs from manufactured package.");
            if (publication != null && !java.util.Objects.equals(manifest.get("publicationStatus"), publication.get("status"))) {
                errors.add("Manifest publication status differs from publication decision.");
            }
        }
    }

    private void compareJson(String leftLabel, Object left, String rightLabel, Object right, List<String> errors) {
        if (left == null || right == null) return;
        try {
            if (!Json.canonical(left).equals(Json.canonical(right))) {
                errors.add(leftLabel + " differs from " + rightLabel + ".");
            }
        } catch (IllegalArgumentException ex) {
            errors.add("Unable to compare " + leftLabel + " with " + rightLabel + ": " + ex.getMessage());
        }
    }

    private void verifyCanonicalIdentityDerivations(Map<String, Object> manifest, Object rawIdentities, List<String> errors) {
        if (!(rawIdentities instanceof List<?> identities)) {
            if (rawIdentities != null) errors.add("canonical-identities.json is not an array.");
            return;
        }
        Set<String> seenUids = new LinkedHashSet<>();
        Map<String, String> uidToResolutionKey = new LinkedHashMap<>();
        boolean rootPresent = false;
        String manifestRoot = manifest != null && manifest.get("rootUaoId") instanceof String s ? s : null;

        for (int i = 0; i < identities.size(); i++) {
            Object item = identities.get(i);
            if (!(item instanceof Map<?, ?> raw)) {
                errors.add("canonical-identities[" + i + "] is not an object.");
                continue;
            }
            @SuppressWarnings("unchecked") Map<String, Object> uao = (Map<String, Object>) raw;
            Object uidRaw = uao.get("uid");
            if (!(uidRaw instanceof String uid)) {
                errors.add("canonical-identities[" + i + "].uid is not a string.");
                continue;
            }
            if (!seenUids.add(uid)) errors.add("Duplicate canonical UAO uid: " + uid);
            if (uid.equals(manifestRoot)) rootPresent = true;

            Map<String, Object> internal = object(uao.get("internal_state"), "canonical-identities[" + i + "].internal_state", errors);
            if (internal == null) continue;
            Map<String, Object> foundryIdentity = object(internal.get("foundry_identity"), "canonical-identities[" + i + "].internal_state.foundry_identity", errors);
            if (foundryIdentity == null) continue;
            Object keyRaw = foundryIdentity.get("resolution_key");
            if (!(keyRaw instanceof String resolutionKey) || resolutionKey.isBlank()) {
                errors.add("canonical-identities[" + i + "] has no non-blank foundry resolution_key.");
                continue;
            }

            String expected = StableIdentifiers.forText("uao", 12, resolutionKey);
            if (!uid.equals(expected)) {
                errors.add("Canonical UAO uid does not match deterministic resolution_key derivation: " + uid + " expected " + expected + ".");
            }
            String previous = uidToResolutionKey.putIfAbsent(uid, resolutionKey);
            if (previous != null && !previous.equals(resolutionKey)) {
                errors.add("Canonical UAO uid collision maps different resolution keys: " + uid + ".");
            }
        }
        if (manifestRoot != null && !rootPresent) errors.add("Manifest rootUaoId is absent from canonical-identities.json.");
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

    private Object readValue(Path path, String label, List<String> errors) {
        if (!Files.isRegularFile(path)) { errors.add(label + " file is missing: " + path.getFileName()); return null; }
        try { return FileOps.readJson(path); }
        catch (IllegalArgumentException ex) { errors.add(label + ": " + ex.getMessage()); return null; }
    }

    private Map<String,Object> readObject(Path path, String label, List<String> errors) {
        Object value = readValue(path, label, errors);
        if (value == null) return null;
        return object(value, label, errors);
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
