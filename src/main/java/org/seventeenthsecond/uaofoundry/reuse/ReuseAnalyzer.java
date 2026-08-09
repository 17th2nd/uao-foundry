package org.seventeenthsecond.uaofoundry.reuse;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Computes and attaches the identity/source delta against a pre-manufacture verified registry snapshot. */
public final class ReuseAnalyzer {
    public static final String REPORT_VERSION = "0.1.0";
    private final Path schemaDir;

    public ReuseAnalyzer(Path schemaDir) { this.schemaDir = schemaDir.toAbsolutePath().normalize(); }

    public Map<String,Object> analyze(Map<String,Object> registryIndex, Path registryRoot, Path packageDir, String registryContextHash) {
        Map<String,Map<String,Object>> existing = new TreeMap<>();
        for (Object raw : array(registryIndex.get("identities"), "registry identities")) {
            Map<String,Object> identity = object(raw, "registry identity");
            existing.put(string(identity.get("uid"), "uid"), identity);
        }

        List<Object> reused = new ArrayList<>();
        List<Object> created = new ArrayList<>();
        for (Object raw : array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities")) {
            Map<String,Object> uao = object(raw, "canonical UAO");
            String uid = string(uao.get("uid"), "uid");
            Map<String,Object> prior = existing.get(uid);
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("uid", uid);
            Map<String,Object> foundryIdentity = object(object(uao.get("internal_state"), "internal_state").get("foundry_identity"), "foundry_identity");
            item.put("resolutionKey", foundryIdentity.get("resolution_key"));
            item.put("canonicalLabel", foundryIdentity.get("canonical_label"));
            if (prior != null) {
                item.put("priorOccurrences", deepCopy(prior.get("occurrences")));
                reused.add(item);
            } else {
                created.add(item);
            }
        }
        reused.sort(Comparator.comparing(v -> string(object(v, "reused identity").get("uid"), "uid")));
        created.sort(Comparator.comparing(v -> string(object(v, "new identity").get("uid"), "uid")));

        List<Object> reusedSources = new ArrayList<>();
        List<Object> newSources = new ArrayList<>();
        Map<String,Object> registry = object(FileOps.readJson(packageDir.resolve("source-registry.json")), "source registry");
        for (Object raw : array(registry.get("sources"), "sources")) {
            Map<String,Object> source = object(raw, "source record");
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("sourceId", source.get("sourceId"));
            item.put("locator", source.get("locator"));
            String locator = string(source.get("locator"), "source locator");
            if (locator.startsWith("registry://")) {
                RegistrySource resolved = resolveRegistrySource(locator, registryRoot, registryIndex);
                String expected = string(source.get("sha256"), "source sha256");
                String actual;
                try { actual = Hashes.sha256(Files.readAllBytes(resolved.path())); }
                catch (Exception ex) { throw new IllegalArgumentException("Unable to hash registry evidence " + locator + ": " + ex.getMessage(), ex); }
                if (!expected.equals(actual)) throw new IllegalArgumentException("Registry evidence hash mismatch for " + locator);
                item.put("registryPackageId", resolved.packageId());
                item.put("registryPath", resolved.relativePath());
                item.put("sha256", actual);
                reusedSources.add(item);
            } else newSources.add(item);
        }
        Comparator<Object> bySourceId = Comparator.comparing(v -> string(object(v, "source delta").get("sourceId"), "sourceId"));
        reusedSources.sort(bySourceId); newSources.sort(bySourceId);

        Map<String,Object> counts = new LinkedHashMap<>();
        counts.put("reusedUaoCount", java.math.BigDecimal.valueOf(reused.size()));
        counts.put("newUaoCount", java.math.BigDecimal.valueOf(created.size()));
        counts.put("registrySourceCount", java.math.BigDecimal.valueOf(reusedSources.size()));
        counts.put("newSourceCount", java.math.BigDecimal.valueOf(newSources.size()));

        Map<String,Object> report = new LinkedHashMap<>();
        report.put("reportVersion", REPORT_VERSION);
        report.put("registryContextHash", registryContextHash);
        report.put("registryIndexHash", Hashes.canonicalJson(registryIndex));
        report.put("reusedUaos", reused);
        report.put("newUaos", created);
        report.put("registrySources", reusedSources);
        report.put("newSources", newSources);
        report.put("counts", counts);
        return report;
    }

    public void attachAndVerify(Path packageDir, Map<String,Object> report) {
        FileOps.writeJson(packageDir.resolve("reuse-report.json"), report);
        Map<String,Object> manifest = object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        Set<String> files = new LinkedHashSet<>();
        for (Object raw : array(manifest.get("files"), "manifest files")) files.add(string(raw, "manifest file"));
        files.add("reuse-report.json");
        manifest.put("files", files.stream().sorted().toList());
        FileOps.writeJson(packageDir.resolve("manifest.json"), manifest);
        writeChecksums(packageDir);
        PackageVerifier.Result result = new PackageVerifier(schemaDir).verify(packageDir);
        if (!result.passed()) throw new IllegalArgumentException("Reuse-augmented package failed verification: " + String.join("; ", result.errors()));
    }

    private RegistrySource resolveRegistrySource(String locator, Path registryRoot, Map<String,Object> registryIndex) {
        String remainder = locator.substring("registry://".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0 || slash == remainder.length() - 1) throw new IllegalArgumentException("Invalid registry source locator: " + locator);
        String packageId = remainder.substring(0, slash);
        String relative = remainder.substring(slash + 1);
        boolean knownPackage = false;
        for (Object raw : array(registryIndex.get("packages"), "registry packages")) {
            if (packageId.equals(object(raw, "registry package").get("packageId"))) { knownPackage = true; break; }
        }
        if (!knownPackage) throw new IllegalArgumentException("Registry source references unknown package: " + packageId);
        Path packageRoot = registryRoot.toAbsolutePath().normalize().resolve("packages").resolve(packageId).normalize();
        Path path = packageRoot.resolve(relative).normalize();
        if (!path.startsWith(packageRoot)) throw new IllegalArgumentException("Registry source path escapes immutable package: " + locator);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Registry source file does not exist: " + locator);
        return new RegistrySource(packageId, relative, path);
    }

    private record RegistrySource(String packageId, String relativePath, Path path) {}

    private void writeChecksums(Path packageDir) {
        StringBuilder out = new StringBuilder();
        try (var stream = Files.walk(packageDir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relative = packageDir.relativize(file).toString().replace('\\','/');
                if ("checksums.sha256".equals(relative)) continue;
                out.append(Hashes.sha256(Files.readAllBytes(file))).append("  ").append(relative).append('\n');
            }
        } catch (Exception ex) { throw new IllegalArgumentException("Unable to rewrite package checksums: " + ex.getMessage(), ex); }
        FileOps.writeText(packageDir.resolve("checksums.sha256"), out.toString());
    }

    private static Object deepCopy(Object value) { return Json.parse(Json.canonical(value)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value, String label) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException(label + " must be an object.");
        return (Map<String,Object>) map;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be an array.");
        return (List<Object>) list;
    }
    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException(label + " must be a non-blank string.");
    }
}
