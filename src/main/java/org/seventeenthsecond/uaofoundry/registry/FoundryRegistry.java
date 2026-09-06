package org.seventeenthsecond.uaofoundry.registry;

import org.seventeenthsecond.uaofoundry.identity.ExternalIdentifiers;
import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolution;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolver;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.significance.SignificanceInputs;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
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
    private final Path operationRoot;
    private final Path indexPath;
    private final PackageVerifier verifier;

    public FoundryRegistry(Path root, Path schemaDir) {
        this.root = root.toAbsolutePath().normalize();
        this.packageRoot = this.root.resolve("packages");
        this.operationRoot = this.root.resolve("identity-operations");
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

    /**
     * Records an identity lifecycle operation in the append-preserving journal.
     *
     * <p>The journal sits beside {@code packages/} as a second immutable content-addressed store.
     * Operations cannot live inside packages — a package is manufactured from provider evidence,
     * whereas an operation is a governed decision about relations between already-registered
     * identities — and they cannot live in {@code index.json}, which is fully derived and rebuilt
     * on every read. A separate immutable store keeps the derived-index invariant intact.
     *
     * <p>Nothing is ever deleted or edited. Recording {@code SUPERSEDE A → B} leaves every package
     * that mentions {@code A} byte-identical; only the derived lifecycle view changes.
     *
     * <p>Admission is transactional and fail-closed. The rebuilt index is what validates the
     * operation against everything already recorded, so a contradictory operation is rejected and
     * its file removed before it can influence anything.
     */
    public OperationResult applyIdentityOperation(IdentityOperation operation) {
        Map<String,Object> before = index(); // verified read; a tampered registry fails before mutation
        Set<String> registered = new LinkedHashSet<>();
        for (Object raw : array(before.get("identities"), "registry identities")) {
            registered.add(string(object(raw, "registry identity").get("uid"), "uid"));
        }
        for (String uid : operation.subjects()) {
            if (!registered.contains(uid)) throw new IllegalArgumentException("Identity operation subject is not a registered identity: " + uid);
        }
        for (String uid : operation.targets()) {
            if (!registered.contains(uid)) throw new IllegalArgumentException("Identity operation target is not a registered identity: " + uid);
        }

        Path destination = operationRoot.resolve(operation.operationId() + ".json").normalize();
        if (!destination.startsWith(operationRoot)) throw new IllegalArgumentException("Operation id escapes the journal root.");
        boolean alreadyPresent = Files.isRegularFile(destination);
        if (alreadyPresent) {
            // Content-addressed: an identical id means identical bytes, so re-recording is a no-op.
            if (!Json.canonical(FileOps.readJson(destination)).equals(Json.canonical(operation.toMap()))) {
                throw new IllegalArgumentException("Identity operation id collision with different content: " + operation.operationId());
            }
        } else {
            FileOps.writeJson(destination, operation.toMap());
        }

        try {
            Map<String,Object> rebuilt = rebuildIndex();
            FileOps.writeJson(indexPath, rebuilt);
            return new OperationResult(operation.operationId(), operation.operation().name(), alreadyPresent,
                    array(rebuilt.get("identityOperations"), "index identityOperations").size());
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
            if (Files.isDirectory(operationRoot)) {
                try (var stream = Files.list(operationRoot)) {
                    if (stream.findAny().isPresent()) throw new IllegalArgumentException("Registry has identity operations but no verified index; rebuild explicitly.");
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
            if (externalIdentifierTokens(identity).contains(query.strip())) kinds.add("EXTERNAL_IDENTIFIER");
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

    /**
     * Resolves one reference to a single registered identity and returns its complete record:
     * kernel material, semantic-variant state, every state version, every package occurrence and
     * the full decision history.
     *
     * <p>This is the persistent-identity addressing surface. {@link #search(String)} ranks possible
     * matches for discovery, including by loose token overlap; this method instead answers "give me
     * <em>that</em> identity" and therefore refuses anything short of an exact, unambiguous address.
     *
     * <p>The resolution itself is delegated to {@link IdentityResolver}, so a lookup obeys exactly
     * the same evidence rules as a manufacture-time decision: an alias never resolves, an ambiguous
     * external identifier never picks a winner, and an identity with unreconciled semantic variants
     * refuses to resolve at all. The unresolved outcome is returned as data, not thrown, because a
     * caller asking "do you know this?" is entitled to a considered "not well enough".
     */
    public Map<String,Object> identityRecord(IdentityReference reference) {
        Map<String,Object> current = index();
        IdentityResolution resolution = new IdentityResolver(current).resolve(reference);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("resolution", resolution.toMap());
        if (resolution.isSame()) {
            for (Object raw : array(current.get("identities"), "registry identities")) {
                Map<String,Object> identity = object(raw, "registry identity");
                if (resolution.uid().equals(identity.get("uid"))) { out.put("identity", deepCopy(identity)); break; }
            }
        } else {
            List<Object> candidates = new ArrayList<>();
            for (Object raw : array(current.get("identities"), "registry identities")) {
                Map<String,Object> identity = object(raw, "registry identity");
                if (resolution.candidateUids().contains(String.valueOf(identity.get("uid")))) candidates.add(deepCopy(identity));
            }
            out.put("candidates", candidates);
        }
        return out;
    }

    /**
     * Exports the durable {@code A_x} / {@code R_x} inputs for one identity.
     *
     * <p>The Foundry supplies inputs to significance and never computes it. Assertions are gathered
     * from the identity's immutable package occurrences, which is safe only because the export
     * refuses an identity carrying unreconciled semantic variants — otherwise {@code A_x} would be
     * assembled by silently unioning mutually inconsistent accounts of one object.
     */
    public Map<String,Object> significanceInputs(IdentityReference reference) {
        Map<String,Object> current = index();
        IdentityResolution resolution = new IdentityResolver(current).resolve(reference);
        if (!resolution.isSame()) {
            throw new IllegalArgumentException("Significance inputs require an exactly resolved identity; resolution was "
                    + resolution.decision() + " (" + String.join(", ", resolution.reasonCodes()) + ").");
        }
        Map<String,Object> identity = null;
        for (Object raw : array(current.get("identities"), "registry identities")) {
            Map<String,Object> candidate = object(raw, "registry identity");
            if (resolution.uid().equals(candidate.get("uid"))) { identity = candidate; break; }
        }
        if (identity == null) throw new IllegalArgumentException("Resolved identity is absent from the registry index: " + resolution.uid());

        List<Object> assertions = List.of();
        // An enriched identity keeps its superseded occurrences as history; only the current variant's
        // assertions are the identity's state (ADR-0007). Without enrichment every occurrence shares one
        // variant and any of them is representative.
        Object currentVariant = identity.get("currentVariant");
        for (Object raw : array(identity.get("occurrences"), "registry identity occurrences")) {
            Map<String,Object> occurrence = object(raw, "registry identity occurrence");
            if (currentVariant instanceof String cv && !cv.equals(occurrence.get("semanticVariantDigest"))) continue;
            Path canonical = root.resolve(string(occurrence.get("canonicalPath"), "canonicalPath")).normalize();
            if (!canonical.startsWith(packageRoot)) throw new IllegalArgumentException("Occurrence path escapes the registry package root.");
            for (Object rawUao : array(FileOps.readJson(canonical), "canonical identities")) {
                Map<String,Object> uao = object(rawUao, "canonical UAO");
                if (resolution.uid().equals(uao.get("uid"))) {
                    assertions = array(uao.get("assertions"), "canonical UAO assertions");
                    break;
                }
            }
            if (!assertions.isEmpty()) break;
        }
        return SignificanceInputs.export(identity, assertions);
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
        Map<String,RelationshipAggregate> relationships = new TreeMap<>();
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

                    Map<String,List<Map<String,Object>>> decisionsByUid = new LinkedHashMap<>();
                    Path resolutionFile = dir.resolve("identity-resolution.json");
                    if (Files.isRegularFile(resolutionFile)) {
                        Object recorded = object(FileOps.readJson(resolutionFile), "identity resolution").get("identityDecisions");
                        if (recorded instanceof List<?> list) {
                            for (Object rawDecision : list) {
                                Map<String,Object> decision = object(rawDecision, "identity decision");
                                decisionsByUid.computeIfAbsent(string(decision.get("uaoId"), "decision uaoId"),
                                        ignored -> new ArrayList<>()).add(decision);
                            }
                        }
                    }
                    Map<String,List<Map<String,Object>>> bindingsByUid = new LinkedHashMap<>();
                    Path unresolvedFile = dir.resolve("unresolved-items.json");
                    if (Files.isRegularFile(unresolvedFile)) {
                        for (Object rawItem : array(FileOps.readJson(unresolvedFile), "unresolved items")) {
                            Map<String,Object> item = object(rawItem, "unresolved item");
                            if (!(item.get("participants") instanceof List<?> participants)) continue;
                            for (Object rawParticipant : participants) {
                                Map<String,Object> participant = object(rawParticipant, "unresolved participant");
                                if (!(participant.get("uaoId") instanceof String participantUid)) continue;
                                bindingsByUid.computeIfAbsent(participantUid, ignored -> new ArrayList<>())
                                        .add(Map.of(
                                                "candidateId", String.valueOf(item.get("candidateId")),
                                                "typeVersion", String.valueOf(item.get("typeVersion")),
                                                "role", String.valueOf(participant.get("role")),
                                                "status", String.valueOf(item.get("identityBindingStatus"))));
                            }
                        }
                    }
                    Path experimentalFile = dir.resolve("experimental-relationships.json");
                    if (Files.isRegularFile(experimentalFile)) {
                        // Experimental typed relationships (Experiment 002) are indexed like identities:
                        // fully derived from immutable packages, addressed by a deterministic id, and
                        // never mistaken for canonical UROs (the record's own labels say so).
                        for (Object rawRecord : array(FileOps.readJson(experimentalFile), "experimental relationships")) {
                            Map<String,Object> record = object(rawRecord, "experimental relationship");
                            String relationshipId = string(record.get("relationshipId"), "relationshipId");
                            RelationshipAggregate aggregate = relationships.computeIfAbsent(relationshipId,
                                    ignored -> new RelationshipAggregate(relationshipId, record));
                            aggregate.addOccurrence(packageId, "packages/" + packageId + "/experimental-relationships.json", record);
                        }
                    }
                    for (Object raw : array(FileOps.readJson(dir.resolve("canonical-identities.json")), "canonical identities")) {
                        Map<String,Object> uao = object(raw, "canonical UAO");
                        String uid = string(uao.get("uid"), "uid");
                        Map<String,Object> internal = object(uao.get("internal_state"), "internal_state");
                        Map<String,Object> foundryIdentity = object(internal.get("foundry_identity"), "foundry_identity");
                        String label = string(foundryIdentity.get("canonical_label"), "canonical_label");
                        String resolutionKey = string(foundryIdentity.get("resolution_key"), "resolution_key");
                        List<String> aliases = strings(foundryIdentity.get("aliases"), "aliases");
                        IdentityAggregate aggregate = identities.computeIfAbsent(uid, ignored -> new IdentityAggregate(uid, resolutionKey));
                        aggregate.setSemanticType(foundryIdentity.get("semantic_type") instanceof String t ? t : null);
                        aggregate.addExternalIdentifiers(ExternalIdentifiers.requireCanonical(
                                foundryIdentity.get("external_identifiers"), "Registered identity external_identifiers"));
                        for (Map<String,Object> decision : decisionsByUid.getOrDefault(uid, List.of())) {
                            aggregate.addDecision(packageId, decision);
                        }
                        for (Map<String,Object> binding : bindingsByUid.getOrDefault(uid, List.of())) {
                            aggregate.addRelationshipBinding(packageId, binding.get("candidateId").toString(),
                                    binding.get("typeVersion").toString(), binding.get("role").toString(),
                                    binding.get("status").toString());
                        }
                        aggregate.addOccurrence(label, aliases, packageId,
                                        "packages/" + packageId + "/canonical-identities.json",
                                        SemanticVariants.digest(uao),
                                        string(foundryIdentity.get("state_version"), "state_version"));
                    }
                }
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("Unable to scan registry packages: " + ex.getMessage(), ex);
            }
        }
        List<IdentityOperation> operations = readOperations();
        applyLifecycle(identities, operations);
        applyEnrichment(identities, operations);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("packages", packages.values().stream().map(PackageRecord::toMap).toList());
        out.put("identities", identities.values().stream().map(IdentityAggregate::toMap).toList());
        out.put("identityOperations", operations.stream().map(IdentityOperation::toMap).toList());
        // Present only when at least one package carries typed relationships, so registries built
        // before Experiment 002 keep verifying byte-for-byte without an explicit rebuild.
        if (!relationships.isEmpty()) {
            for (RelationshipAggregate aggregate : relationships.values()) {
                for (String uid : aggregate.participantUids()) {
                    if (!identities.containsKey(uid)) throw new IllegalArgumentException("Relationship " + aggregate.relationshipId + " binds an identity absent from the registry: " + uid);
                }
            }
            out.put("relationships", relationships.values().stream().map(RelationshipAggregate::toMap).toList());
        }
        return out;
    }

    /**
     * Typed-relationship neighbourhood of one exactly resolved identity, from the verified index.
     * Edges are experimental records (see {@code ExperimentalRelationships}); the caveat travels
     * with the answer so no consumer can read it as a governed graph.
     */
    public Map<String,Object> relationshipNeighbourhood(String uid) {
        Map<String,Object> index = index();
        List<Object> edges = new ArrayList<>();
        Set<String> neighbours = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        for (Object raw : array(index.getOrDefault("relationships", List.of()), "index relationships")) {
            Map<String,Object> relationship = object(raw, "index relationship");
            List<Object> participants = array(relationship.get("participants"), "participants");
            boolean mentions = participants.stream().map(p -> object(p, "participant").get("uaoId")).anyMatch(uid::equals);
            if (!mentions) continue;
            for (Object p : participants) {
                Object other = object(p, "participant").get("uaoId");
                if (other instanceof String s && !s.equals(uid)) neighbours.add(s);
            }
            for (Object occurrence : array(relationship.get("occurrences"), "occurrences")) packages.add(String.valueOf(object(occurrence, "occurrence").get("packageId")));
            edges.add(relationship);
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("uid", uid);
        out.put("status", "EXPERIMENTAL_TYPED_RELATIONSHIPS");
        out.put("certifying", Boolean.FALSE);
        out.put("edges", edges);
        out.put("neighbourUids", new ArrayList<>(neighbours));
        out.put("packagesContributing", new ArrayList<>(packages));
        out.put("caveat", "Experimental typed relationships validated against a Foundry relationship type edition that is not ASA-admitted. "
                + "No canonical URO exists and none is implied (ASA-SPEC-0006 §10.3 AU-1, outcome undetermined).");
        return out;
    }

    /** Whole-registry graph export: identities as nodes, experimental typed relationships as edges. */
    public Map<String,Object> graph() {
        Map<String,Object> index = index();
        List<Object> nodes = new ArrayList<>();
        for (Object raw : array(index.get("identities"), "index identities")) {
            Map<String,Object> identity = object(raw, "index identity");
            Map<String,Object> node = new LinkedHashMap<>();
            node.put("uid", identity.get("uid"));
            node.put("resolutionKey", identity.get("resolutionKey"));
            node.put("labels", identity.get("canonicalLabels"));
            node.put("aliases", identity.get("aliases"));
            node.put("externalIdentifiers", identity.get("externalIdentifiers"));
            node.put("lifecycleState", identity.get("lifecycleState"));
            nodes.add(node);
        }
        List<Object> edges = new ArrayList<>();
        for (Object raw : array(index.getOrDefault("relationships", List.of()), "index relationships")) {
            Map<String,Object> relationship = object(raw, "index relationship");
            Map<String,Object> edge = new LinkedHashMap<>();
            edge.put("relationshipId", relationship.get("relationshipId"));
            edge.put("typeId", relationship.get("typeId"));
            edge.put("typeName", relationship.get("typeName"));
            edge.put("symmetric", relationship.get("symmetric"));
            edge.put("participants", relationship.get("participants"));
            edge.put("statement", relationship.get("statement"));
            edge.put("basis", relationship.get("basis"));
            edge.put("outcome", relationship.get("outcome"));
            edge.put("occurrenceCount", new java.math.BigDecimal(array(relationship.get("occurrences"), "occurrences").size()));
            edges.add(edge);
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("graphVersion", "0.1.0");
        out.put("status", "EXPERIMENTAL_TYPED_RELATIONSHIPS");
        out.put("certifying", Boolean.FALSE);
        out.put("nodes", nodes);
        out.put("edges", edges);
        out.put("registryIndexHash", Hashes.canonicalJson(index));
        return out;
    }

    /** Reads the append-preserving journal, re-deriving every content address on the way in. */
    private List<IdentityOperation> readOperations() {
        List<IdentityOperation> operations = new ArrayList<>();
        if (!Files.isDirectory(operationRoot)) return operations;
        try (var stream = Files.list(operationRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) throw new IllegalArgumentException("Unexpected file in the identity-operation journal: " + name);
                IdentityOperation operation = IdentityOperation.fromMap(object(FileOps.readJson(file), "identity operation"));
                if (!name.equals(operation.operationId() + ".json")) {
                    throw new IllegalArgumentException("Identity operation file name does not match its content address: " + name);
                }
                operations.add(operation);
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Unable to read the identity-operation journal: " + ex.getMessage(), ex);
        }
        operations.sort(Comparator.comparing(IdentityOperation::operationId));
        return operations;
    }

    /**
     * Derives each identity's lifecycle state from the journal.
     *
     * <p>An identity may be the subject of at most one terminal operation. A second would mean the
     * registry held two contradictory accounts of the same identity's fate with no rule for
     * choosing between them, so the index build fails closed rather than picking one. Chains are
     * still expressible — {@code A → B} then {@code B → C} names {@code B} as subject only once.
     *
     * <p>Cycles are refused for the same reason: a cycle is a history that cannot have happened.
     */
    private void applyLifecycle(Map<String,IdentityAggregate> identities, List<IdentityOperation> operations) {
        Map<String,IdentityOperation> terminal = new LinkedHashMap<>();
        for (IdentityOperation operation : operations) {
            for (String subject : operation.subjects()) {
                // A MERGE names every participant as a subject, including the one that survives,
                // because the record is about all of them. The survivor is not itself merged away,
                // so it keeps its active state and is not a link in any supersession chain.
                if (operation.targets().contains(subject)) continue;
                IdentityOperation previous = terminal.putIfAbsent(subject, operation);
                if (previous != null) {
                    throw new IllegalArgumentException("Identity " + subject + " is the subject of two lifecycle operations ("
                            + previous.operationId() + ", " + operation.operationId() + "); the registry has no rule for choosing between them.");
                }
            }
        }
        for (Map.Entry<String,IdentityOperation> entry : terminal.entrySet()) {
            Set<String> visited = new LinkedHashSet<>();
            String current = entry.getKey();
            while (current != null && visited.add(current)) {
                IdentityOperation operation = terminal.get(current);
                current = operation == null || operation.targets().size() != 1 ? null : operation.targets().getFirst();
                if (current != null && visited.contains(current)) {
                    throw new IllegalArgumentException("Identity lifecycle operations form a cycle involving " + current + ".");
                }
            }
        }
        terminal.forEach((subject, operation) -> {
            IdentityAggregate aggregate = identities.get(subject);
            if (aggregate != null) aggregate.setLifecycle(operation.subjectState(), operation.targets(), operation.operationId());
        });
    }

    /**
     * Applies {@code ENRICH} operations: within one uid, one semantic variant is declared the successor
     * state of another. Nothing about the packages changes; the derived view gains a current variant.
     *
     * <p>Every claim the operation makes is re-checked from immutable package bytes on every build, so
     * the journal cannot assert an enrichment the packages do not support: both variants must be
     * occurrences of the subject, the named package must carry the newer one, and the newer assertion
     * set must contain every older assertion verbatim plus at least one more. A fork (two enrichments
     * leaving the same variant) or a cycle is a history that cannot have happened and fails closed.
     */
    private void applyEnrichment(Map<String,IdentityAggregate> identities, List<IdentityOperation> operations) {
        for (IdentityOperation operation : operations) {
            if (operation.operation() != IdentityOperation.Kind.ENRICH) continue;
            String uid = operation.subjects().getFirst();
            IdentityAggregate aggregate = identities.get(uid);
            if (aggregate == null) throw new IllegalArgumentException("ENRICH " + operation.operationId() + " names an identity absent from the registry: " + uid);
            String from = string(operation.enrichment().get("fromVariant"), "enrichment.fromVariant");
            String to = string(operation.enrichment().get("toVariant"), "enrichment.toVariant");
            String toPackage = string(operation.enrichment().get("toPackageId"), "enrichment.toPackageId");
            Occurrence older = aggregate.occurrences.stream().filter(o -> o.semanticVariantDigest().equals(from)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ENRICH " + operation.operationId() + ": fromVariant is not an occurrence of " + uid + "."));
            Occurrence newer = aggregate.occurrences.stream().filter(o -> o.packageId().equals(toPackage)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ENRICH " + operation.operationId() + ": toPackageId " + toPackage + " holds no occurrence of " + uid + "."));
            if (!newer.semanticVariantDigest().equals(to)) {
                throw new IllegalArgumentException("ENRICH " + operation.operationId() + ": package " + toPackage + " carries variant "
                        + newer.semanticVariantDigest() + " for " + uid + ", not the declared toVariant.");
            }
            String defect = enrichmentDefect(assertionsOf(older, uid), assertionsOf(newer, uid));
            if (defect != null) throw new IllegalArgumentException("ENRICH " + operation.operationId() + " refused for " + uid + ": " + defect);
            aggregate.addEnrichment(from, to, toPackage, operation.operationId());
        }
    }

    /** Canonical-JSON forms of one occurrence's assertions, read from the immutable package. */
    private Set<String> assertionsOf(Occurrence occurrence, String uid) {
        Path file = root.resolve(occurrence.path()).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Occurrence path escapes the registry: " + occurrence.path());
        for (Object raw : array(FileOps.readJson(file), "canonical identities")) {
            Map<String,Object> uao = object(raw, "canonical UAO");
            if (uid.equals(uao.get("uid"))) {
                Set<String> out = new LinkedHashSet<>();
                for (Object assertion : array(uao.get("assertions"), "canonical UAO assertions")) out.add(Json.canonical(assertion));
                return out;
            }
        }
        throw new IllegalArgumentException("Occurrence " + occurrence.packageId() + " no longer carries " + uid + ".");
    }

    /**
     * The enrichment law, stated once: the newer assertion set is a strict superset of the older one.
     * Returns a defect description, or {@code null} when the law holds.
     */
    public static String enrichmentDefect(Set<String> older, Set<String> newer) {
        List<String> missing = older.stream().filter(a -> !newer.contains(a)).toList();
        if (!missing.isEmpty()) {
            return "the newer variant drops or re-words " + missing.size() + " of " + older.size()
                    + " prior assertion(s); enrichment must restate every prior assertion verbatim. First: " + abbreviate(missing.getFirst());
        }
        if (newer.size() == older.size()) return "the newer variant adds no assertion; nothing is enriched.";
        return null;
    }

    private static String abbreviate(String value) { return value.length() <= 160 ? value : value.substring(0, 157) + "..."; }

    /**
     * Admits an enriching package and records the {@code ENRICH} operation as one fail-closed step.
     *
     * <p>The superset law is checked against the candidate package <em>before</em> anything is
     * written, so a package that does not enrich never enters the registry as a stray unreconciled
     * variant. If recording the operation still fails after admission, a package this call copied in
     * is removed again and the index restored, leaving the registry byte-identical.
     */
    public EnrichmentResult enrich(Path packageDir, String uid, List<String> reasonCodes, String justification,
                                   String authority, String recordedAt) {
        packageDir = packageDir.toAbsolutePath().normalize();
        Map<String,Object> before = index();
        Map<String,Object> prior = null;
        for (Object raw : array(before.get("identities"), "registry identities")) {
            Map<String,Object> identity = object(raw, "registry identity");
            if (uid.equals(identity.get("uid"))) { prior = identity; break; }
        }
        if (prior == null) throw new IllegalArgumentException("ENRICH subject is not a registered identity: " + uid);
        if (!IdentityOperation.ACTIVE.equals(prior.get("lifecycleState"))) {
            throw new IllegalArgumentException("ENRICH refused: " + uid + " is " + prior.get("lifecycleState") + ", not ACTIVE.");
        }
        if (!SemanticVariants.SINGLE_VARIANT.equals(prior.get("semanticVariantStatus"))) {
            throw new IllegalArgumentException("ENRICH refused: " + uid + " has unreconciled variants; reconcile before enriching.");
        }
        String from = prior.get("currentVariant") instanceof String cv ? cv
                : string(object(array(prior.get("occurrences"), "occurrences").getFirst(), "occurrence").get("semanticVariantDigest"), "semanticVariantDigest");
        Set<String> older = null;
        for (Object raw : array(prior.get("occurrences"), "occurrences")) {
            Map<String,Object> occurrence = object(raw, "occurrence");
            if (from.equals(occurrence.get("semanticVariantDigest"))) {
                older = assertionsOf(new Occurrence(string(occurrence.get("packageId"), "packageId"), string(occurrence.get("canonicalPath"), "canonicalPath"), from, string(occurrence.get("stateVersion"), "stateVersion")), uid);
                break;
            }
        }
        if (older == null) throw new IllegalArgumentException("ENRICH refused: current variant of " + uid + " has no readable occurrence.");

        Map<String,Object> candidateUao = null;
        for (Object raw : array(FileOps.readJson(packageDir.resolve("canonical-identities.json")), "canonical identities")) {
            Map<String,Object> uao = object(raw, "canonical UAO");
            if (uid.equals(uao.get("uid"))) { candidateUao = uao; break; }
        }
        if (candidateUao == null) throw new IllegalArgumentException("ENRICH refused: the candidate package carries no occurrence of " + uid + ".");
        String to = SemanticVariants.digest(candidateUao);
        if (to.equals(from)) throw new IllegalArgumentException("ENRICH refused: the candidate package restates " + uid + " unchanged; nothing to enrich.");
        Set<String> newer = new LinkedHashSet<>();
        for (Object assertion : array(candidateUao.get("assertions"), "canonical UAO assertions")) newer.add(Json.canonical(assertion));
        String defect = enrichmentDefect(older, newer);
        if (defect != null) throw new IllegalArgumentException("ENRICH refused for " + uid + ": " + defect);

        // The operation record is built — and its metadata validated — BEFORE anything is written, so a
        // blank justification or empty reason list is refused while the registry is still untouched.
        String packageId = string(object(FileOps.readJson(packageDir.resolve("manifest.json")), "manifest").get("packageId"), "manifest.packageId");
        IdentityOperation operation = IdentityOperation.enrich(uid, from, to, packageId, reasonCodes, justification, authority, recordedAt);

        RegistrationResult registration = register(packageDir);
        try {
            if (!registration.packageId().equals(packageId)) throw new IllegalArgumentException("Registered package id differs from the candidate manifest: " + registration.packageId());
            OperationResult recorded = applyIdentityOperation(operation);
            return new EnrichmentResult(registration, recorded, from, to, newer.size() - older.size());
        } catch (RuntimeException ex) {
            // Anything after admission fails closed: a package this call copied in is removed and the
            // index restored, so the registry is byte-identical to its state before the call.
            if (!registration.alreadyPresent()) {
                FileOps.deleteTree(registration.registryPath());
                FileOps.writeJson(indexPath, before);
            }
            throw ex;
        }
    }

    private Map<String,Object> emptyIndex() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", REGISTRY_VERSION);
        out.put("packages", List.of());
        out.put("identities", List.of());
        out.put("identityOperations", List.of());
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
        if (kinds.contains("EXTERNAL_IDENTIFIER")) return 2;
        if (kinds.contains("LABEL")) return 3;
        if (kinds.contains("ALIAS")) return 4;
        return 5;
    }

    /** Exact {@code scheme:identifier} tokens. Case-sensitive in the identifier half, matching the ext: key discipline. */
    private static Set<String> externalIdentifierTokens(Map<String,Object> identity) {
        Set<String> out = new LinkedHashSet<>();
        if (identity.get("externalIdentifiers") instanceof Map<?,?> map) {
            map.forEach((k, v) -> out.add(ExternalIdentifiers.token(String.valueOf(k), String.valueOf(v))));
        }
        return out;
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
        private final Map<String,String> externalIdentifiers = new TreeMap<>();
        private String semanticType;
        private boolean semanticTypeSeen;
        private final List<Occurrence> occurrences = new ArrayList<>();
        private final List<Object> decisionHistory = new ArrayList<>();
        private final List<Object> relationshipBindings = new ArrayList<>();
        private String lifecycleState = IdentityOperation.ACTIVE;
        private List<String> successorUids = List.of();
        private String lifecycleOperationId;
        private final List<Map<String,Object>> enrichments = new ArrayList<>();
        private IdentityAggregate(String uid, String resolutionKey) { this.uid = uid; this.resolutionKey = resolutionKey; }

        /** Records one verified ENRICH edge between two of this identity's variants. */
        private void addEnrichment(String from, String to, String packageId, String operationId) {
            for (Map<String,Object> existing : enrichments) {
                if (from.equals(existing.get("fromVariant")) && !operationId.equals(existing.get("operationId"))) {
                    throw new IllegalArgumentException("Identity " + uid + " has two enrichments leaving variant " + from
                            + " (" + existing.get("operationId") + ", " + operationId + "); the registry has no rule for choosing between them.");
                }
            }
            Map<String,Object> edge = new LinkedHashMap<>();
            edge.put("fromVariant", from); edge.put("toVariant", to); edge.put("packageId", packageId); edge.put("operationId", operationId);
            enrichments.add(edge);
            enrichments.sort(Comparator.comparing(Json::canonical));
            // A cycle is a history that cannot have happened.
            Map<String,String> next = new LinkedHashMap<>();
            enrichments.forEach(e -> next.put(String.valueOf(e.get("fromVariant")), String.valueOf(e.get("toVariant"))));
            for (String start : next.keySet()) {
                Set<String> seen = new LinkedHashSet<>(); String current = start;
                while (current != null) {
                    if (!seen.add(current)) throw new IllegalArgumentException("Identity " + uid + " enrichment history forms a cycle at variant " + current + ".");
                    current = next.get(current);
                }
            }
        }

        /**
         * Durable external identity is aggregated across occurrences and must not disagree. Two
         * immutable packages naming one uid under contradicting third-party identifiers is a
         * genuine conflict, not a semantic variant, and fails the index build closed.
         */
        private void addExternalIdentifiers(Map<String,String> declared) {
            for (Map.Entry<String,String> entry : declared.entrySet()) {
                String previous = externalIdentifiers.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new IllegalArgumentException("EXTERNAL_IDENTIFIER_CONTRADICTION: registry occurrences for uid "
                            + uid + " declare conflicting " + entry.getKey() + " identifiers.");
                }
            }
        }

        /**
         * Accumulates the identity decisions recorded by each immutable package occurrence. This is
         * the identity's history: every determination ever made about it, in package order, none
         * of them revisable. Later entries do not supersede earlier ones — they sit beside them.
         */
        private void addDecision(String packageId, Map<String,Object> decision) {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("packageId", packageId);
            entry.put("decision", decision.get("decision"));
            entry.put("reasonCodes", decision.get("reasonCodes"));
            entry.put("reference", decision.get("reference"));
            entry.put("candidateUids", decision.get("candidateUids"));
            entry.put("sourceRefs", decision.get("sourceRefs"));
            decisionHistory.add(entry);
            decisionHistory.sort(Comparator.comparing(Json::canonical));
        }

        /**
         * Records that a retained relationship candidate names this identity in a role.
         *
         * <p>These are <em>not</em> canonical UROs and never become them here — canonical URO
         * publication stays fail-closed pending 17th2nd/ASA#29. What this gives is traceability:
         * once participants are bound to persistent uids, a relationship stated in one package
         * remains findable from the identity it mentions, in any later package. Before binding, a
         * relationship pointed only at bundle-local handles and was unfindable outside its own
         * package.
         */
        private void addRelationshipBinding(String packageId, String candidateId, String typeVersion, String role, String bindingStatus) {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("packageId", packageId);
            entry.put("relationshipCandidateId", candidateId);
            entry.put("typeVersion", typeVersion);
            entry.put("role", role);
            entry.put("identityBindingStatus", bindingStatus);
            entry.put("canonicalUroPublished", Boolean.FALSE);
            entry.put("blockedBy", "URO_TYPE_AUTHORITY_UNAVAILABLE");
            relationshipBindings.add(entry);
            relationshipBindings.sort(Comparator.comparing(Json::canonical));
        }

        private void setLifecycle(String state, List<String> successors, String operationId) {
            this.lifecycleState = state;
            this.successorUids = List.copyOf(successors);
            this.lifecycleOperationId = operationId;
        }

        private void setSemanticType(String value) {
            if (semanticTypeSeen && !java.util.Objects.equals(semanticType, value)) {
                throw new IllegalArgumentException("Registry occurrences for uid " + uid + " declare conflicting semantic types.");
            }
            semanticType = value; semanticTypeSeen = true;
        }

        private void addOccurrence(String label, List<String> aliasValues, String packageId, String path, String semanticVariantDigest, String stateVersion) {
            Set<String> priorNames = new LinkedHashSet<>();
            labels.forEach(v -> priorNames.add(normalize(v))); aliases.forEach(v -> priorNames.add(normalize(v)));
            Set<String> nextNames = new LinkedHashSet<>(); nextNames.add(normalize(label)); aliasValues.forEach(v -> nextNames.add(normalize(v)));
            if (!priorNames.isEmpty() && java.util.Collections.disjoint(priorNames, nextNames)) {
                throw new IllegalArgumentException("Stable UAO name-continuity conflict for resolutionKey " + resolutionKey + " (uid " + uid + ")");
            }
            labels.add(label); aliases.addAll(aliasValues); occurrences.add(new Occurrence(packageId, path, semanticVariantDigest, stateVersion));
            occurrences.sort(Comparator.comparing(Occurrence::packageId));
        }
        private Map<String,Object> toMap() {
            Set<String> variants = new LinkedHashSet<>();
            occurrences.forEach(v -> variants.add(v.semanticVariantDigest()));
            Set<String> stateVersions = new java.util.TreeSet<>();
            occurrences.forEach(v -> stateVersions.add(v.stateVersion()));
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("uid", uid); out.put("resolutionKey", resolutionKey);
            out.put("semanticType", semanticType);
            out.put("canonicalLabels", labels.stream().sorted().toList()); out.put("aliases", aliases.stream().sorted().toList());
            out.put("externalIdentifiers", new LinkedHashMap<String,Object>(externalIdentifiers));
            // Variants that an ENRICH operation has declared superseded are not unreconciled: they are
            // history. The identity is a single variant when exactly one current variant remains.
            Set<String> superseded = new LinkedHashSet<>();
            enrichments.forEach(e -> superseded.add(String.valueOf(e.get("fromVariant"))));
            Set<String> current = new LinkedHashSet<>(variants); current.removeAll(superseded);
            out.put("semanticVariantStatus", current.size() == 1
                    ? SemanticVariants.SINGLE_VARIANT : SemanticVariants.MULTIPLE_UNRECONCILED_VARIANTS);
            // Present only when the identity has been enriched, so registries without ENRICH
            // operations keep verifying byte-for-byte without an explicit rebuild.
            if (!enrichments.isEmpty()) {
                if (current.size() == 1) out.put("currentVariant", current.iterator().next());
                out.put("variantHistory", new ArrayList<>(enrichments));
            }
            out.put("stateVersions", new ArrayList<>(stateVersions));
            out.put("lifecycleState", lifecycleState);
            out.put("successorUids", new ArrayList<>(successorUids));
            out.put("lifecycleOperationId", lifecycleOperationId);
            out.put("decisionHistory", new ArrayList<>(decisionHistory));
            out.put("relationshipBindings", new ArrayList<>(relationshipBindings));
            out.put("occurrences", occurrences.stream().map(Occurrence::toMap).toList()); return out;
        }
    }

    /** One deterministic relationship id across every immutable package that states it. */
    private static final class RelationshipAggregate {
        private final String relationshipId;
        private final Map<String,Object> first;
        private final List<Map<String,Object>> occurrences = new ArrayList<>();
        private final Set<String> stateVersions = new java.util.TreeSet<>();
        private final Set<String> bases = new java.util.TreeSet<>();
        private RelationshipAggregate(String relationshipId, Map<String,Object> first) {
            this.relationshipId = relationshipId; this.first = Json.object(Json.parse(Json.canonical(first)), "relationship record");
        }
        private void addOccurrence(String packageId, String path, Map<String,Object> record) {
            // The identity-bearing content of a relationship is fixed by its id; occurrences may
            // differ only in evidence, basis and diagnostics. Anything else is a derivation defect.
            if (!Json.canonical(first.get("typeId")).equals(Json.canonical(record.get("typeId")))
                    || !Json.canonical(participantsOf(first)).equals(Json.canonical(participantsOf(record)))) {
                throw new IllegalArgumentException("Relationship id " + relationshipId + " is bound to different participants or types across packages.");
            }
            Map<String,Object> occurrence = new LinkedHashMap<>();
            occurrence.put("packageId", packageId);
            occurrence.put("canonicalPath", path);
            occurrence.put("stateVersion", record.get("stateVersion"));
            occurrence.put("sourceRefs", record.get("sourceRefs"));
            occurrence.put("basis", record.get("basis"));
            occurrences.add(occurrence);
            occurrences.sort(Comparator.comparing(v -> String.valueOf(v.get("packageId"))));
            if (record.get("stateVersion") instanceof String s) stateVersions.add(s);
            if (record.get("basis") instanceof String b) bases.add(b);
        }
        private static Map<String,Object> participantsOf(Map<String,Object> record) {
            Map<String,Object> out = new TreeMap<>();
            for (Object raw : Json.array(record.get("participants"), "participants")) {
                Map<String,Object> p = Json.object(raw, "participant");
                out.merge(String.valueOf(p.get("role")), new ArrayList<>(List.of(String.valueOf(p.get("uaoId")))), (a, b) -> { List<Object> m = new ArrayList<>((List<?>) a); m.addAll((List<?>) b); m.sort(Comparator.comparing(String::valueOf)); return m; });
            }
            return out;
        }
        private Set<String> participantUids() {
            Set<String> out = new LinkedHashSet<>();
            for (Object raw : Json.array(first.get("participants"), "participants")) out.add(String.valueOf(Json.object(raw, "participant").get("uaoId")));
            return out;
        }
        private Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("relationshipId", relationshipId);
            out.put("status", first.get("status"));
            out.put("certifying", Boolean.FALSE);
            out.put("typeId", first.get("typeId"));
            out.put("typeName", first.get("typeName"));
            out.put("typeEdition", first.get("typeEdition"));
            out.put("symmetric", first.get("symmetric"));
            out.put("participants", first.get("participants"));
            out.put("identityLiterals", first.get("identityLiterals"));
            out.put("statement", first.get("statement"));
            out.put("basis", bases.size() == 1 ? bases.iterator().next() : "MIXED");
            out.put("outcome", first.get("outcome"));
            out.put("stateVersions", new ArrayList<>(stateVersions));
            out.put("occurrences", new ArrayList<>(occurrences));
            return out;
        }
    }

    private record Occurrence(String packageId, String path, String semanticVariantDigest, String stateVersion) {
        Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("packageId", packageId);
            out.put("canonicalPath", path);
            out.put("semanticVariantDigest", semanticVariantDigest);
            out.put("stateVersion", stateVersion);
            return out;
        }
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

    public record OperationResult(String operationId, String operation, boolean alreadyPresent, int operationCount) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("operationId", operationId); out.put("operation", operation);
            out.put("alreadyPresent", alreadyPresent);
            out.put("operationCount", new java.math.BigDecimal(operationCount));
            return out;
        }
    }

    public record EnrichmentResult(RegistrationResult registration, OperationResult operation, String fromVariant, String toVariant, int assertionsAdded) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("registration", registration.toMap()); out.put("operation", operation.toMap());
            out.put("fromVariant", fromVariant); out.put("toVariant", toVariant); out.put("assertionsAdded", assertionsAdded);
            return out;
        }
    }

    public record VerificationResult(boolean passed, List<String> errors, int packageCount, int identityCount) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>(); out.put("passed", passed); out.put("errors", new ArrayList<>(errors));
            out.put("packageCount", new java.math.BigDecimal(packageCount)); out.put("identityCount", new java.math.BigDecimal(identityCount)); return out;
        }
    }
}
