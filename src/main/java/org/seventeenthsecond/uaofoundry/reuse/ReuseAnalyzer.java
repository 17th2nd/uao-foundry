package org.seventeenthsecond.uaofoundry.reuse;

import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;
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
        return analyze(registryIndex, registryRoot, packageDir, registryContextHash, Set.of());
    }

    /**
     * {@code enrichmentOf} names registered uids for which a differing variant is accepted <em>if and
     * only if</em> its assertion set is a strict superset of the current one (ADR-0007). Such an identity
     * is reported under {@code enrichedUaos}, not {@code reusedUaos}; the registry's {@code enrich}
     * admission re-derives the same law from bytes before anything is recorded.
     */
    public Map<String,Object> analyze(Map<String,Object> registryIndex, Path registryRoot, Path packageDir, String registryContextHash, Set<String> enrichmentOf) {
        Map<String,Map<String,Object>> existing = new TreeMap<>();
        for (Object raw : array(registryIndex.get("identities"), "registry identities")) {
            Map<String,Object> identity = object(raw, "registry identity");
            existing.put(string(identity.get("uid"), "uid"), identity);
        }

        List<Object> reused = new ArrayList<>();
        List<Object> created = new ArrayList<>();
        List<Object> enriched = new ArrayList<>();
        for (Object raw : array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities")) {
            Map<String,Object> uao = object(raw, "canonical UAO");
            String uid = string(uao.get("uid"), "uid");
            Map<String,Object> prior = existing.get(uid);
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("uid", uid);
            Map<String,Object> foundryIdentity = object(object(uao.get("internal_state"), "internal_state").get("foundry_identity"), "foundry_identity");
            String resolutionKey = string(foundryIdentity.get("resolution_key"), "resolution_key");
            String semanticVariantDigest = SemanticVariants.digest(uao);
            item.put("resolutionKey", resolutionKey);
            item.put("canonicalLabel", foundryIdentity.get("canonical_label"));
            item.put("semanticVariantDigest", semanticVariantDigest);
            if (prior != null) {
                String priorKey = string(prior.get("resolutionKey"), "registry identity resolutionKey");
                if (!resolutionKey.equals(priorKey)) {
                    throw new IllegalArgumentException("REGISTRY_IDENTITY_MISMATCH: stable UAO " + uid
                            + " has resolutionKey " + priorKey + " but candidate package uses " + resolutionKey + ".");
                }
                // An identity that has been superseded, retired, merged or split is no longer
                // automatically reusable. Reusing one would let a governed lifecycle decision be
                // undone by the next manufacture that happened to propose the same key.
                Object lifecycle = prior.get("lifecycleState");
                if (lifecycle != null && !IdentityOperation.ACTIVE.equals(lifecycle)) {
                    throw new IllegalArgumentException("IDENTITY_LIFECYCLE_NOT_ACTIVE: automatic reuse refused for uid "
                            + uid + " resolutionKey " + resolutionKey + "; recorded lifecycle state is " + lifecycle + ".");
                }
                String status = string(prior.get("semanticVariantStatus"), "registry identity semanticVariantStatus");
                if (SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS.equals(status)) {
                    throw new IllegalArgumentException("MULTIPLE_UNRECONCILED_VARIANTS: automatic reuse refused for uid "
                            + uid + " resolutionKey " + resolutionKey + ".");
                }
                Set<String> priorVariants = new LinkedHashSet<>();
                for (Object occurrenceRaw : array(prior.get("occurrences"), "registry identity occurrences")) {
                    Map<String,Object> occurrence = object(occurrenceRaw, "registry identity occurrence");
                    String digest = string(occurrence.get("semanticVariantDigest"), "occurrence semanticVariantDigest");
                    if (!digest.matches("[a-f0-9]{64}")) {
                        throw new IllegalArgumentException("REGISTRY_VARIANT_INDEX_INVALID: invalid semantic variant digest for uid " + uid + ".");
                    }
                    priorVariants.add(digest);
                }
                // An enriched identity carries superseded variants as history; only its current
                // variant is the state a re-observation must match.
                if (prior.get("currentVariant") instanceof String currentVariant) {
                    if (!priorVariants.contains(currentVariant)) {
                        throw new IllegalArgumentException("REGISTRY_VARIANT_INDEX_INVALID: current variant is not an occurrence for uid " + uid + ".");
                    }
                    priorVariants = new LinkedHashSet<>(List.of(currentVariant));
                }
                if (!SemanticVariants.SINGLE_VARIANT.equals(status) || priorVariants.size() != 1) {
                    throw new IllegalArgumentException("REGISTRY_VARIANT_INDEX_INVALID: inconsistent semantic variant status for uid " + uid + ".");
                }
                if (!priorVariants.contains(semanticVariantDigest) && enrichmentOf.contains(uid)) {
                    String priorVariant = priorVariants.iterator().next();
                    Set<String> older = assertionsOf(registryRoot, prior, priorVariant, uid);
                    Set<String> newer = new LinkedHashSet<>();
                    for (Object assertion : array(uao.get("assertions"), "canonical UAO assertions")) newer.add(Json.canonical(assertion));
                    String defect = org.seventeenthsecond.uaofoundry.registry.FoundryRegistry.enrichmentDefect(older, newer);
                    if (defect != null) {
                        throw new IllegalArgumentException("ENRICHMENT_NOT_SUPERSET: enrichment refused for uid " + uid + " resolutionKey " + resolutionKey + "; " + defect);
                    }
                    item.put("fromVariant", priorVariant);
                    item.put("assertionsAdded", java.math.BigDecimal.valueOf(newer.size() - older.size()));
                    item.put("priorOccurrences", deepCopy(prior.get("occurrences")));
                    enriched.add(item);
                    continue;
                }
                if (!priorVariants.contains(semanticVariantDigest)) {
                    throw new IllegalArgumentException("SEMANTIC_VARIANT_DIVERGENCE: automatic reuse refused for uid "
                            + uid + " resolutionKey " + resolutionKey + "; candidatePackage="
                            + packageDir.toAbsolutePath().normalize()
                            + "; explicit registry admission may preserve the new immutable occurrence for future reconciliation.");
                }
                item.put("priorOccurrences", deepCopy(prior.get("occurrences")));
                reused.add(item);
            } else {
                created.add(item);
            }
        }
        reused.sort(Comparator.comparing(v -> string(object(v, "reused identity").get("uid"), "uid")));
        enriched.sort(Comparator.comparing(v -> string(object(v, "enriched identity").get("uid"), "uid")));
        for (String uid : enrichmentOf) {
            if (enriched.stream().noneMatch(v -> uid.equals(object(v, "enriched identity").get("uid")))) {
                throw new IllegalArgumentException("ENRICHMENT_TARGET_ABSENT: --enrich named " + uid + " but the package does not enrich it (absent, or restated unchanged).");
            }
        }
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
        if (!enriched.isEmpty()) counts.put("enrichedUaoCount", java.math.BigDecimal.valueOf(enriched.size()));

        Map<String,Object> report = new LinkedHashMap<>();
        report.put("reportVersion", REPORT_VERSION);
        report.put("registryContextHash", registryContextHash);
        report.put("registryIndexHash", Hashes.canonicalJson(registryIndex));
        report.put("reusedUaos", reused);
        report.put("newUaos", created);
        if (!enriched.isEmpty()) report.put("enrichedUaos", enriched);
        report.put("registrySources", reusedSources);
        report.put("newSources", newSources);
        report.put("counts", counts);
        return report;
    }

    /**
     * Finding P9-1 (ADR-0006): the reuse report is no longer attached to the package.
     *
     * <p>It embeds {@code registryIndexHash}, {@code registryContextHash} and prior occurrences,
     * all of which move as the registry grows, while being excluded from the content digest that
     * determines {@code packageId}. Two manufactures of semantically identical material against a
     * moved-on registry therefore produced the same package id with different bytes, and the
     * collision guards correctly refused the second — 69 of 114 cumulative manufactures in the
     * Alpha measurement.
     *
     * <p>The report is now stored in a {@link org.seventeenthsecond.uaofoundry.runs.RunStore}
     * beside the registry. The guards are unchanged: the defect is resolved by removing the
     * volatile input, not by loosening the check that caught it.
     *
     * @deprecated retained only to document the removal; attaching volatile run evidence to a
     *             content-addressed package reintroduces P9-1.
     */
    @Deprecated
    public void attachAndVerify(Path packageDir, Map<String,Object> report) {
        throw new UnsupportedOperationException(
                "P9-1/ADR-0006: reuse evidence must not be attached to an immutable package. "
                        + "Record it in a RunStore beside the registry instead.");
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

    /** Canonical-JSON assertions of the registered occurrence carrying {@code variant}, read from immutable package bytes. */
    private static Set<String> assertionsOf(Path registryRoot, Map<String,Object> prior, String variant, String uid) {
        for (Object raw : array(prior.get("occurrences"), "registry identity occurrences")) {
            Map<String,Object> occurrence = object(raw, "registry identity occurrence");
            if (!variant.equals(occurrence.get("semanticVariantDigest"))) continue;
            Path file = registryRoot.toAbsolutePath().normalize().resolve(string(occurrence.get("canonicalPath"), "canonicalPath")).normalize();
            for (Object rawUao : array(FileOps.readJson(file), "canonical identities")) {
                Map<String,Object> uao = object(rawUao, "canonical UAO");
                if (!uid.equals(uao.get("uid"))) continue;
                Set<String> out = new LinkedHashSet<>();
                for (Object assertion : array(uao.get("assertions"), "canonical UAO assertions")) out.add(Json.canonical(assertion));
                return out;
            }
        }
        throw new IllegalArgumentException("REGISTRY_VARIANT_INDEX_INVALID: no readable occurrence carries the current variant of uid " + uid + ".");
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
