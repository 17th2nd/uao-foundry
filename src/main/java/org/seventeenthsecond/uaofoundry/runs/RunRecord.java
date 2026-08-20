package org.seventeenthsecond.uaofoundry.runs;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operational evidence for one manufacturing run.
 *
 * <p>A run record answers <em>"what happened when we ran it?"</em>. The package it references
 * answers <em>"what was manufactured?"</em>. Keeping those apart is the resolution of finding
 * P9-1: the reuse report is relative to a registry state at a moment, so embedding it inside a
 * content-addressed package made the package's bytes vary with its surroundings while its
 * {@code packageId} did not.
 *
 * <p>This is <b>not</b> canonical semantic identity and carries no ASA authority. It is
 * append-preserving operational history, in the same discipline as identity operations: a
 * completed run is never edited, and a correction appends a new record referencing the original.
 *
 * <p>Timestamps are supplied by the caller rather than read from the wall clock, so a run is
 * reproducible in a test. The application passes real time.
 */
public record RunRecord(
        String runId,
        String identitySeed,
        String context,
        String provider,
        String status,
        String packageId,
        List<String> usiIds,
        String registryBeforeHash,
        String registryAfterHash,
        Map<String,Object> reuseReport,
        String startedAt,
        String completedAt,
        String supersedesRunId,
        String note) {

    public static final String RECORD_VERSION = "0.1.0";

    /** Terminal statuses. A run that did not complete still leaves a record saying so. */
    public static final String COMPLETED = "COMPLETED";
    public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";
    public static final String ADMISSION_REFUSED = "ADMISSION_REFUSED";
    public static final String PROVIDER_FAILED = "PROVIDER_FAILED";
    public static final String FAILED = "FAILED";

    public RunRecord {
        usiIds = usiIds == null ? List.of() : List.copyOf(usiIds);
    }

    public static RunRecord create(String identitySeed, String context, String provider, String status,
                                   String packageId, List<String> usiIds,
                                   String registryBeforeHash, String registryAfterHash,
                                   Map<String,Object> reuseReport, String startedAt, String completedAt,
                                   String supersedesRunId, String note) {
        requireNonBlank(identitySeed, "identitySeed");
        requireNonBlank(provider, "provider");
        requireNonBlank(status, "status");
        requireNonBlank(startedAt, "startedAt");
        requireNonBlank(completedAt, "completedAt");
        RunRecord draft = new RunRecord(null, identitySeed, context, provider, status, packageId,
                usiIds, registryBeforeHash, registryAfterHash, reuseReport, startedAt, completedAt,
                supersedesRunId, note);
        return new RunRecord(StableIdentifiers.forJson("run", 16, draft.projection()),
                identitySeed, context, provider, status, packageId, usiIds,
                registryBeforeHash, registryAfterHash, reuseReport, startedAt, completedAt,
                supersedesRunId, note);
    }

    /** Reads a stored record back and re-derives its content address. */
    @SuppressWarnings("unchecked")
    public static RunRecord fromMap(Map<String,Object> raw) {
        if (!RECORD_VERSION.equals(raw.get("recordVersion"))) {
            throw new IllegalArgumentException("Unsupported run record recordVersion: " + raw.get("recordVersion"));
        }
        List<String> usiIds = new ArrayList<>();
        if (raw.get("usiIds") instanceof List<?> values) for (Object value : values) usiIds.add(String.valueOf(value));
        RunRecord rebuilt = create(
                str(raw.get("identitySeed")), str(raw.get("context")), str(raw.get("provider")),
                str(raw.get("status")), str(raw.get("packageId")), usiIds,
                str(raw.get("registryBeforeHash")), str(raw.get("registryAfterHash")),
                raw.get("reuseReport") instanceof Map<?,?> m ? (Map<String,Object>) m : null,
                str(raw.get("startedAt")), str(raw.get("completedAt")),
                str(raw.get("supersedesRunId")), str(raw.get("note")));
        if (!rebuilt.runId().equals(raw.get("runId"))) {
            throw new IllegalArgumentException("Run record content address does not match its contents: "
                    + raw.get("runId") + " expected " + rebuilt.runId() + ".");
        }
        return rebuilt;
    }

    /** Meaning-bearing projection the content address is taken over. Excludes the address itself. */
    private Map<String,Object> projection() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("identitySeed", identitySeed);
        if (context != null) out.put("context", context);
        out.put("provider", provider);
        out.put("status", status);
        if (packageId != null) out.put("packageId", packageId);
        out.put("usiIds", new ArrayList<>(usiIds));
        if (registryBeforeHash != null) out.put("registryBeforeHash", registryBeforeHash);
        if (registryAfterHash != null) out.put("registryAfterHash", registryAfterHash);
        if (reuseReport != null) out.put("reuseReport", Json.parse(Json.canonical(reuseReport)));
        out.put("startedAt", startedAt);
        out.put("completedAt", completedAt);
        if (supersedesRunId != null) out.put("supersedesRunId", supersedesRunId);
        if (note != null) out.put("note", note);
        return out;
    }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("runId", runId);
        out.putAll(projection());
        out.remove("recordVersion");
        Map<String,Object> ordered = new LinkedHashMap<>();
        ordered.put("recordVersion", RECORD_VERSION);
        ordered.put("runId", runId);
        projection().forEach((k, v) -> { if (!"recordVersion".equals(k)) ordered.put(k, v); });
        return ordered;
    }

    private static String str(Object value) { return value instanceof String s ? s : null; }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Run record " + label + " must be non-blank.");
    }
}
