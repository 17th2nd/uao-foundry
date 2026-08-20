package org.seventeenthsecond.uaofoundry.staging;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Non-canonical staging store for identity-bound relationship candidates (directive §18).
 *
 * <h2>The problem this exists to study</h2>
 *
 * The Persistent Identity Alpha established that {@code 17th2nd/ASA#29} blocks more than
 * publication: a package carrying a relationship candidate is {@code EVIDENCE_INCOMPLETE} and
 * therefore <b>inadmissible to the registry</b>, so relationship bindings never accumulate and a
 * later session has nothing to reuse. That made benchmark hypotheses H1 and H2 <em>not testable</em>
 * rather than disproved.
 *
 * <p>This store lets those candidates be retained across manufactures so persistent relationship
 * reconstruction can be studied, without claiming any governance the Foundry does not have.
 *
 * <h2>What it is not</h2>
 *
 * <b>Candidate relationship memory, not a certified relationship graph.</b> Every record carries
 * {@code status: NON_CANONICAL_CANDIDATE_MEMORY}, {@code authorityStatus:
 * URO_TYPE_AUTHORITY_UNAVAILABLE} and {@code certifying: false} as schema constants, so a consumer
 * cannot read one as governed. Nothing here:
 *
 * <ul>
 *   <li>creates a URO or any ASA authority;</li>
 *   <li>changes a publication decision — a relationship-bearing package remains
 *       {@code EVIDENCE_INCOMPLETE} and inadmissible;</li>
 *   <li>enters the registry index, which stays derived from packages and identity operations only;</li>
 *   <li>is consulted by manufacture, verification or admission.</li>
 * </ul>
 *
 * <p>It is a <b>separate store</b> for exactly that reason: keeping it outside the registry root
 * makes "this is not registry content" structural rather than a convention someone must remember.
 *
 * <p>Records are content-addressed and append-preserving, matching the discipline of identity
 * operations and run records.
 */
public final class StagedRelationshipStore {
    public static final String RECORD_VERSION = "0.1.0";
    public static final String STATUS = "NON_CANONICAL_CANDIDATE_MEMORY";
    public static final String AUTHORITY_STATUS = "URO_TYPE_AUTHORITY_UNAVAILABLE";

    private final Path root;

    public StagedRelationshipStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** The conventional staging store for a registry: a sibling, never a child. */
    public static StagedRelationshipStore besideRegistry(Path registryRoot) {
        Path registry = registryRoot.toAbsolutePath().normalize();
        Path parent = registry.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Registry path has no parent to place a staging store beside: " + registry);
        }
        return new StagedRelationshipStore(parent.resolve("staged-relationships"));
    }

    public Path root() { return root; }

    /**
     * Stages every relationship candidate a manufactured package retained.
     *
     * <p>Reads {@code unresolved-items.json}, which already holds the candidates bound to
     * persistent uids by the relationship-construction stage. Staging copies that evidence; it does
     * not re-derive or improve it.
     *
     * @return the staged records, in content-address order
     */
    public List<Map<String,Object>> stageFrom(Path packageDir, String recordedAt) {
        Path unresolved = packageDir.resolve("unresolved-items.json");
        if (!Files.isRegularFile(unresolved)) return List.of();
        String packageId = String.valueOf(Json.object(
                FileOps.readJson(packageDir.resolve("manifest.json")), "manifest").get("packageId"));

        List<Map<String,Object>> staged = new ArrayList<>();
        for (Object raw : Json.array(FileOps.readJson(unresolved), "unresolved items")) {
            Map<String,Object> item = Json.object(raw, "unresolved item");
            if (!(item.get("participants") instanceof List<?>)) continue;   // not a relationship finding
            staged.add(record(item, packageId, recordedAt));
        }
        staged.sort(Comparator.comparing(r -> String.valueOf(r.get("stagedId"))));
        return staged;
    }

    private Map<String,Object> record(Map<String,Object> item, String packageId, String recordedAt) {
        Map<String,Object> projection = new LinkedHashMap<>();
        projection.put("recordVersion", RECORD_VERSION);
        projection.put("typeVersion", item.get("typeVersion"));
        projection.put("participants", Json.parse(Json.canonical(item.get("participants"))));
        projection.put("identityBindingStatus", item.get("identityBindingStatus"));
        projection.put("identityLiterals", Json.parse(Json.canonical(item.get("identityLiterals"))));
        projection.put("contextualBindings", Json.parse(Json.canonical(item.get("contextualBindings"))));
        projection.put("sourceRefs", Json.parse(Json.canonical(item.get("sourceRefs"))));
        projection.put("packageId", packageId);
        projection.put("candidateId", item.get("candidateId"));
        projection.put("recordedAt", recordedAt);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("stagedId", StableIdentifiers.forJson("stg", 16, projection));
        out.put("status", STATUS);
        out.put("authorityStatus", AUTHORITY_STATUS);
        out.put("certifying", Boolean.FALSE);
        projection.forEach((k, v) -> { if (!"recordVersion".equals(k)) out.put(k, v); });

        Path destination = root.resolve(out.get("stagedId") + ".json").normalize();
        if (!destination.startsWith(root)) throw new IllegalArgumentException("Staged id escapes the staging root.");
        if (Files.isRegularFile(destination)) {
            if (!Json.canonical(FileOps.readJson(destination)).equals(Json.canonical(out))) {
                throw new IllegalArgumentException("Staged relationship id collision with different content: " + out.get("stagedId"));
            }
        } else {
            FileOps.writeJson(destination, out);
        }
        return out;
    }

    /** Every staged candidate, re-deriving each content address on the way in. */
    public List<Map<String,Object>> list() {
        List<Map<String,Object>> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                Map<String,Object> record = Json.object(FileOps.readJson(file), "staged relationship");
                requireNonCanonical(record);
                if (!name.equals(record.get("stagedId") + ".json")) {
                    throw new IllegalArgumentException("Staged relationship file name does not match its id: " + name);
                }
                out.add(record);
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Unable to read the staging store: " + ex.getMessage(), ex);
        }
        out.sort(Comparator.comparing(r -> String.valueOf(r.get("stagedId"))));
        return out;
    }

    /**
     * The candidate relationship graph reachable from one identity, across every package that ever
     * mentioned it. This is what a persistent relationship substrate would provide — and the point
     * of staging is to make it measurable while it remains explicitly uncertified.
     */
    public Map<String,Object> neighbourhood(String uid) {
        List<Object> edges = new ArrayList<>();
        Set<String> neighbours = new LinkedHashSet<>();
        Set<String> packages = new LinkedHashSet<>();
        for (Map<String,Object> record : list()) {
            List<Object> participants = Json.array(record.get("participants"), "participants");
            boolean mentions = participants.stream()
                    .map(p -> Json.object(p, "participant").get("uaoId"))
                    .anyMatch(uid::equals);
            if (!mentions) continue;
            packages.add(String.valueOf(record.get("packageId")));
            for (Object raw : participants) {
                Map<String,Object> participant = Json.object(raw, "participant");
                if (participant.get("uaoId") instanceof String other && !other.equals(uid)) neighbours.add(other);
            }
            edges.add(Json.parse(Json.canonical(record)));
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("uid", uid);
        out.put("status", STATUS);
        out.put("certifying", Boolean.FALSE);
        out.put("authorityStatus", AUTHORITY_STATUS);
        out.put("edges", edges);
        out.put("neighbourUids", new ArrayList<>(neighbours));
        out.put("packagesContributing", new ArrayList<>(packages));
        out.put("caveat", "Candidate relationship memory only. These edges are asserted, not governed. "
                + "No canonical URO exists and none is implied; publication remains fail-closed pending 17th2nd/ASA#29.");
        return out;
    }

    /** Fail closed if a stored record has lost the labels that mark it non-canonical. */
    private static void requireNonCanonical(Map<String,Object> record) {
        if (!STATUS.equals(record.get("status"))
                || !AUTHORITY_STATUS.equals(record.get("authorityStatus"))
                || !Boolean.FALSE.equals(record.get("certifying"))) {
            throw new IllegalArgumentException(
                    "Staged relationship record has lost its non-canonical labelling: " + record.get("stagedId"));
        }
    }
}
