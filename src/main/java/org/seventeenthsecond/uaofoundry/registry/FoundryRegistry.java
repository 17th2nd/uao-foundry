package org.seventeenthsecond.uaofoundry.registry;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Foundry-owned immutable package registry. It indexes already-verified manufactured
 * packages for discovery/reuse without creating new ASA semantic authority.
 */
public final class FoundryRegistry {
    public static final String REGISTRY_VERSION = "0.1.0";

    private final Path root;
    private final Path packageRoot;
    private final Path indexPath;
    private final PackageVerifier verifier;

    public FoundryRegistry(Path root, Path schemaDir) {
        this.root = root.toAbsolutePath().normalize();
        this.packageRoot = this.root.resolve("packages");
        this.indexPath = this.root.resolve("index.json");
        this.verifier = new PackageVerifier(schemaDir);
    }

    public RegistrationResult register(Path packageDir) {
        packageDir = packageDir.toAbsolutePath().normalize();
        requireReusablePackage(packageDir);
        Map<String,Object> manifest = object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest");
        String packageId = string(manifest.get("packageId"), "manifest.packageId");
        String digest = FileOps.treeHash(packageDir);
        Path destination = packageRoot.resolve(packageId).normalize();
        if (!destination.startsWith(packageRoot)) throw new IllegalArgumentException("Package id escapes registry package root.");

        Map<String,Object> before = index(); // verified read; tampered indexes fail before mutation
        validateIdentityContinuity(packageDir, before);
        boolean alreadyPresent = Files.isDirectory(destination);
        if (alreadyPresent) {
            String existingDigest = FileOps.treeHash(destination);
            if (!digest.equals(existingDigest)) {
                throw new IllegalArgumentException("Registry package-id collision with different immutable content: " + packageId);
            }
        } else {
            FileOps.copyTree(packageDir, destination);
        }

        try {
            Map<String,Object> rebuilt = rebuildIndex();
            FileOps.writeJson(indexPath, rebuilt);
            return new RegistrationResult(packageId, digest, destination, alreadyPresent,
                    array(rebuilt.get("packages"), "index packages").size(),
                    array(rebuilt.get("identities"), "index identities").size());
        } catch (RuntimeException ex) {
            if (!alreadyPresent) FileOps.deleteTree(destination);
            throw ex;
        }
    }

    public VerificationResult verify() {
        List<String> errors = new ArrayList<>();
        Map<String,Object> rebuilt;
        try {
            rebuilt = buildIndex();
        } catch (IllegalArgumentException ex) {
            return new VerificationResult(false, List.of(ex.getMessage()), 0, 0);
        }
        if (!Files.isRegularFile(indexPath)) {
            errors.add("Registry index is missing: " + indexPath);
        } else {
            try {
                Object stored = FileOps.readJson(indexPath);
                if (!Json.canonical(stored).equals(Json.canonical(rebuilt))) {
                    errors.add("Registry index does not match verified immutable package contents.");
                }
            } catch (IllegalArgumentException ex) {
                errors.add("Registry index is invalid JSON: " + ex.getMessage());
            }
        }
        return new VerificationResult(errors.isEmpty(), List.copyOf(errors),
                array(rebuilt.get("packages"), "packages").size(),
                array(rebuilt.get("identities"), "identities").size());
    }

    public Map<String,Object> index() {
        if (!Files.isRegularFile(indexPath)) {
            if (Files.isDirectory(packageRoot)) {
                try (var stream = Files.list(packageRoot)) {
                    if (stream.findAny().isPresent()) throw new IllegalArgumentException("Registry has packages but no verified index; rebuild explicitly.");
                } catch (java.io.IOException ex) { throw new IllegalArgumentException("Unable to inspect registry packages: " + ex.getMessage(), ex); }
            }
            return emptyIndex();
        }
        Map<String,Object> stored = object(FileOps.readJson(indexPath), "registry index");
        Map<String,Object> rebuilt = buildIndex();
        if (!Json.canonical(stored).equals(Json.canonical(rebuilt))) {
            throw new IllegalArgumentException("Registry index does not match verified immutable package contents.");
        }
        return stored;
    }

    public Map<String,Object> rebuildAndPersist() {
        Map<String,Object> index = rebuildIndex();
        FileOps.writeJson(indexPath, index);
        return index;
    }

    public List<Object> search(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) throw new IllegalArgumentException("Registry search query must not be blank.");
        List<SearchHit> hits = new ArrayList<>();
        for (Object raw : array(index().get("identities"), "registry identities")) {
            Map<String,Object> identity = object(raw, "registry identity");
            Set<String> kinds = new LinkedHashSet<>();
            String uid = string(identity.get("uid"), "uid");
            String resolutionKey = string(identity.get("resolutionKey"), "resolutionKey");
            List<String> labels = strings(identity.get("canonicalLabels"), "canonicalLabels");
            List<String> aliases = strings(identity.get("aliases"), "aliases");

            if (normalize(uid).equals(normalized)) kinds.add("UID");
            if (normalize(resolutionKey).equals(normalized)) kinds.add("RESOLUTION_KEY");
            if (labels.stream().map(FoundryRegistry::normalize).anyMatch(normalized::equals)) kinds.add("LABEL");
            if (aliases.stream().map(FoundryRegistry::normalize).anyMatch(normalized::equals)) kinds.add("ALIAS");
            if (kinds.isEmpty() && tokenMatch(normalized, uid, resolutionKey, labels, aliases)) kinds.add("TOKEN");
            if (!kinds.isEmpty()) hits.add(new SearchHit(priority(kinds), uid, kinds, identity));
        }
        hits.sort(Comparator.comparingInt(SearchHit::priority).thenComparing(SearchHit::uid));
        List<Object> out = new ArrayList<>();
        for (SearchHit hit : hits) {
            Map<String,Object> record = new LinkedHashMap<>();
            record.put("matchKinds", new ArrayList<>(hit.kinds()));
            record.put("identity", deepCopy(hit.identity()));
            out.add(record);
        }
        return out;
    }

    /** Provider-safe discovery material. No package source content or credentials are exposed here. */
    public Map<String,Object> discoveryContext(String query, int catalogLimit) {
        if (catalogLimit < 1) throw new IllegalArgumentException("catalogLimit must be positive.");
        Map<String,Object> current = index();
        List<Object> identities = array(current.get("identities"), "identities");
        List<Object> catalog = new ArrayList<>();
        int count = Math.min(catalogLimit, identities.size());
        for (int i = 0; i < count; i++) catalog.add(deepCopy(identities.get(i)));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("query", query);
        out.put("matches", search(query));
        out.put("catalog", catalog);
        out.put("totalIdentities", new java.math.BigDecimal(identities.size()));
        out.put("catalogTruncated", identities.size() > catalogLimit);
        return out;
    }

    private void validateIdentityContinuity(Path packageDir, Map<String,Object> currentIndex) {
        Map<String,Set<String>> knownNames = new LinkedHashMap<>();
        for (Object raw : array(currentIndex.get("identities"), "registry identities")) {
            Map<String,Object> identity = object(raw, "registry identity");
            String key = string(identity.get("resolutionKey"), "resolutionKey");
            Set<String> names = new LinkedHashSet<>();
            strings(identity.get("canonicalLabels"), "canonicalLabels").forEach(v -> names.add(normalize(v)));
            strings(identity.get("aliases"), "aliases").forEach(v -> names.add(normalize(v)));
            knownNames.put(key, names);
        }
        for (Object raw : array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities")) {
            Map<String,Object> uao = object(raw, "canonical UAO");
            Map<String,Object> fi = object(object(uao.get("internal_state"), "internal_state").get("foundry_identity"), "foundry_identity");
            String key = string(fi.get("resolution_key"), "resolution_key");
            Set<String> existing = knownNames.get(key);
            if (existing == null || existing.isEmpty()) continue;
            Set<String> proposed = new LinkedHashSet<>();
            proposed.add(normalize(string(fi.get("canonical_label"), "canonical_label")));
            strings(fi.get("aliases"), "aliases").forEach(v -> proposed.add(normalize(v)));
            if (java.util.Collections.disjoint(existing, proposed)) {
                throw new IllegalArgumentException("Stable resolutionKey has no lexical name continuity with registered identity; refusing semantic merge: " + key);
            }
        }
    }

    private void requireReusablePackage(Path packageDir) {
        PackageVerifier.Result result = verifier.verify(packageDir);
        if (!result.passed()) throw new IllegalArgumentException("Package verification failed; registry admission denied: " + String.join("; ", result.errors()));
        Map<String,Object> decision = object(FileOps.readJson(packageDir.resolve("publication-decision.json")), "publication decision");
        Object eligible = decision.get("eligible");
        if (!(eligible instanceof Boolean allowed) || !allowed) {
            throw new IllegalArgumentException("Package publication decision is not reuse-eligible.");
        }
    }

    private Map<String,Object> rebuildIndex() { return buildIndex(); }

    private Map<String,Object> buildIndex() {
        Map<String,PackageRecord> packages = new TreeMap<>();
        Map<String,IdentityAggregate> identities = new TreeMap<>();
        if (Files.isDirectory(packageRoot)) {
            try (var stream = Files.list(packageRoot)) {
                for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                    requireReusablePackage(dir);
                    Map<String,Object> manifest = object(FileOps.readJson(dir.resolve("manifest.json")), "manifest");
                    String packageId = string(manifest.get("packageId"), "packageId");
                    if (!dir.getFileName().toString().equals(packageId)) {
                        throw new IllegalArgumentException("Registry package directory does not equal manifest packageId: " + dir);
                    }
                    String digest = FileOps.treeHash(dir);
                    PackageRecord previous = packages.putIfAbsent(packageId, new PackageRecord(
                            packageId,
                            string(manifest.get("rootUaoId"), "rootUaoId"),
                            string(manifest.get("publicationStatus"), "publicationStatus"),
                            digest,
                            "packages/" + packageId));
                    if (previous != null) throw new IllegalArgumentException("Duplicate package id in registry: " + packageId);

                    for (Object raw : array(FileOps.readJson(dir.resolve("canonical-identities.json")), "canonical identities")) {
                        Map<String,Object> uao = object(raw, "canonical UAO");
                        String uid = string(uao.get("uid"), "uid");
                        Map<String,Object> internal = object(uao.get("internal_state"), "internal_state");
                        Map<String,Object> foundryIdentity = object(internal.get("foundry_identity"), "foundry_identity");
                        String label = string(foundryIdentity.get("canonical_label"), "canonical_label");
                        String resolutionKey = string(foundryIdentity.get("resolution_key"), "resolution_key");
                        List<String> aliases = strings(foundryIdentity.get("aliases"), "aliases");
                        identities.computeIfAbsent(uid, ignored -> new IdentityAggregate(uid, resolutionKey))
                                .addOccurrence(label, aliases, packageId, "packages/" + packageId + "/canonical-identities.json");
                    }
                }
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("Unable to scan registry packages: " + ex.getMessage(), ex);
            }
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("packages", packages.values().stream().map(PackageRecord::toMap).toList());
        out.put("identities", identities.values().stream().map(IdentityAggregate::toMap).toList());
        return out;
    }

    private Map<String,Object> emptyIndex() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("packages", List.of());
        out.put("identities", List.of());
        return out;
    }

    private static boolean tokenMatch(String query, String uid, String resolutionKey, List<String> labels, List<String> aliases) {
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) return false;
        Set<String> candidate = new LinkedHashSet<>();
        candidate.addAll(tokens(uid));
        candidate.addAll(tokens(resolutionKey));
        labels.forEach(v -> candidate.addAll(tokens(v)));
        aliases.forEach(v -> candidate.addAll(tokens(v)));
        return candidate.containsAll(queryTokens) || queryTokens.stream().allMatch(q -> candidate.stream().anyMatch(c -> c.contains(q) || q.contains(c)));
    }

    private static Set<String> tokens(String value) {
        Set<String> out = new LinkedHashSet<>();
        for (String token : normalize(value).split("[^a-z0-9]+")) if (!token.isBlank()) out.add(token);
        return out;
    }

    private static int priority(Set<String> kinds) {
        if (kinds.contains("UID")) return 0;
        if (kinds.contains("RESOLUTION_KEY")) return 1;
        if (kinds.contains("LABEL")) return 2;
        if (kinds.contains("ALIAS")) return 3;
        return 4;
    }

    private static String normalize(String value) { return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static Object deepCopy(Object value) { return Json.parse(Json.canonical(value)); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value, String label) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException(label + " must be an object.");
        return (Map<String,Object>) map;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be an array.");
        return (List<Object>) list;
    }
    private static List<String> strings(Object value, String label) {
        List<String> out = new ArrayList<>();
        for (Object item : array(value, label)) out.add(string(item, label + " item"));
        return out;
    }
    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException(label + " must be a non-blank string.");
    }

    private record SearchHit(int priority, String uid, Set<String> kinds, Map<String,Object> identity) {}

    private record PackageRecord(String packageId, String rootUaoId, String publicationStatus, String packageDigest, String path) {
        Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("packageId", packageId); out.put("rootUaoId", rootUaoId); out.put("publicationStatus", publicationStatus);
            out.put("packageDigest", packageDigest); out.put("path", path); return out;
        }
    }

    private static final class IdentityAggregate {
        private final String uid;
        private final String resolutionKey;
        private final Set<String> labels = new LinkedHashSet<>();
        private final Set<String> aliases = new LinkedHashSet<>();
        private final List<Occurrence> occurrences = new ArrayList<>();
        private IdentityAggregate(String uid, String resolutionKey) { this.uid = uid; this.resolutionKey = resolutionKey; }
        private void addOccurrence(String label, List<String> aliasValues, String packageId, String path) {
            Set<String> priorNames = new LinkedHashSet<>();
            labels.forEach(v -> priorNames.add(normalize(v))); aliases.forEach(v -> priorNames.add(normalize(v)));
            Set<String> nextNames = new LinkedHashSet<>(); nextNames.add(normalize(label)); aliasValues.forEach(v -> nextNames.add(normalize(v)));
            if (!priorNames.isEmpty() && java.util.Collections.disjoint(priorNames, nextNames)) {
                throw new IllegalArgumentException("Stable UAO name-continuity conflict for resolutionKey " + resolutionKey + " (uid " + uid + ")");
            }
            labels.add(label); aliases.addAll(aliasValues); occurrences.add(new Occurrence(packageId, path));
            occurrences.sort(Comparator.comparing(Occurrence::packageId));
        }
        private Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("uid", uid); out.put("resolutionKey", resolutionKey);
            out.put("canonicalLabels", labels.stream().sorted().toList()); out.put("aliases", aliases.stream().sorted().toList());
            out.put("occurrences", occurrences.stream().map(Occurrence::toMap).toList()); return out;
        }
    }

    private record Occurrence(String packageId, String path) {
        Map<String,Object> toMap() { return Map.of("packageId", packageId, "canonicalPath", path); }
    }

    public record RegistrationResult(String packageId, String packageDigest, Path registryPath, boolean alreadyPresent,
                                     int packageCount, int identityCount) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("packageId", packageId); out.put("packageDigest", packageDigest); out.put("registryPath", registryPath.toString());
            out.put("alreadyPresent", alreadyPresent); out.put("packageCount", new java.math.BigDecimal(packageCount));
            out.put("identityCount", new java.math.BigDecimal(identityCount)); return out;
        }
    }

    public record VerificationResult(boolean passed, List<String> errors, int packageCount, int identityCount) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>(); out.put("passed", passed); out.put("errors", new ArrayList<>(errors));
            out.put("packageCount", new java.math.BigDecimal(packageCount)); out.put("identityCount", new java.math.BigDecimal(identityCount)); return out;
        }
    }
}
