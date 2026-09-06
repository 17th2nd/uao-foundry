package org.seventeenthsecond.uaofoundry.identity;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An append-preserving identity lifecycle operation.
 *
 * <h2>Why these are not UAO fields</h2>
 *
 * {@code uid} is a pure function of {@code resolution_key}. Two uids therefore can never become one
 * by rewriting anything, and an immutable content-addressed package cannot be edited in any case.
 * Merge and split can only exist as a <em>mapping layer above</em> the derivation, which is what
 * this record is.
 *
 * <p>{@code SUPERSEDE} and {@code RETIRE} are different in kind: ASA already governs both through
 * {@code lifecycle_status} and {@code successor_identity_ref}. This journal records the governed
 * decision; it does not amend, and never rewrites, any package that was manufactured before it.
 *
 * <h2>What a recorded operation does and does not do</h2>
 *
 * It does <b>not</b> redirect resolution. After {@code SUPERSEDE A → B}, asking for {@code A} does
 * not silently yield {@code B} — it yields {@code UNRESOLVED} naming {@code B} as the successor.
 * Silent redirection would change what a later manufacture produces without anyone asking for the
 * change, which is exactly the destructive rewrite §10 of the programme forbids. The caller learns
 * what happened and decides.
 *
 * <p>Every prior determination stays inspectable and every prior reference stays resolvable — you
 * can still look up a merged identity and discover its fate. Nothing is deleted.
 */
public record IdentityOperation(
        String operationId,
        Kind operation,
        List<String> subjects,
        List<String> targets,
        List<String> reasonCodes,
        String justification,
        List<Map<String,Object>> evidence,
        String authority,
        String recordedAt,
        Map<String,Object> enrichment) {

    public static final String RECORD_VERSION = "0.1.0";

    public enum Kind {
        /** One identity is replaced by a named successor. ASA-governed concept. */
        SUPERSEDE,
        /** One identity is withdrawn with no successor. ASA-governed concept. */
        RETIRE,
        /** Several identities are determined to denote one object. Foundry-owned mapping. */
        MERGE,
        /** One identity is determined to have conflated several objects. Foundry-owned mapping. */
        SPLIT,
        /**
         * One semantic variant of an identity is declared the successor STATE of another, within the
         * same uid. Foundry-owned mapping over occurrences: the newer variant must restate every
         * assertion of the older one verbatim and add at least one more, which the registry checks
         * from package bytes. The identity stays ACTIVE; only which occurrence counts as current changes.
         */
        ENRICH
    }

    /** Lifecycle state an identity is left in by the operations that name it as a subject. */
    public static final String ACTIVE = "ACTIVE";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String RETIRED = "RETIRED";
    public static final String MERGED = "MERGED";
    public static final String SPLIT_STATE = "SPLIT";

    public IdentityOperation {
        subjects = List.copyOf(subjects);
        targets = List.copyOf(targets);
        reasonCodes = List.copyOf(reasonCodes);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        enrichment = enrichment == null ? null : Json.object(Json.parse(Json.canonical(enrichment)), "enrichment");
    }

    /** Backwards-compatible constructor for the four lifecycle kinds, which carry no enrichment block. */
    public IdentityOperation(String operationId, Kind operation, List<String> subjects, List<String> targets,
                             List<String> reasonCodes, String justification, List<Map<String,Object>> evidence,
                             String authority, String recordedAt) {
        this(operationId, operation, subjects, targets, reasonCodes, justification, evidence, authority, recordedAt, null);
    }

    /**
     * Builds an operation, deriving its content address, and enforces the shape rules that make
     * each kind meaningful.
     */
    public static IdentityOperation create(Kind operation, List<String> subjects, List<String> targets,
                                           List<String> reasonCodes, String justification,
                                           List<Map<String,Object>> evidence, String authority, String recordedAt) {
        return create(operation, subjects, targets, reasonCodes, justification, evidence, authority, recordedAt, null);
    }

    /**
     * Builds an {@code ENRICH} operation. {@code fromVariant} and {@code toVariant} are semantic-variant
     * digests of the same uid; {@code toPackageId} names the registered package whose occurrence carries
     * {@code toVariant}. The registry, not this record, verifies the superset relation between them.
     */
    public static IdentityOperation enrich(String uid, String fromVariant, String toVariant, String toPackageId,
                                           List<String> reasonCodes, String justification, String authority, String recordedAt) {
        Map<String,Object> enrichment = new LinkedHashMap<>();
        enrichment.put("fromVariant", fromVariant);
        enrichment.put("toVariant", toVariant);
        enrichment.put("toPackageId", toPackageId);
        return create(Kind.ENRICH, List.of(uid), List.of(uid), reasonCodes, justification, List.of(), authority, recordedAt, enrichment);
    }

    public static IdentityOperation create(Kind operation, List<String> subjects, List<String> targets,
                                           List<String> reasonCodes, String justification,
                                           List<Map<String,Object>> evidence, String authority, String recordedAt,
                                           Map<String,Object> enrichment) {
        requireNonBlank(justification, "justification");
        requireNonBlank(recordedAt, "recordedAt");
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("An identity operation requires at least one reason code.");
        }
        List<String> subjectList = distinct(subjects, "subjects");
        List<String> targetList = distinct(targets == null ? List.of() : targets, "targets");

        switch (operation) {
            case SUPERSEDE -> {
                if (subjectList.size() != 1 || targetList.size() != 1) {
                    throw new IllegalArgumentException("SUPERSEDE requires exactly one subject and one successor.");
                }
            }
            case RETIRE -> {
                if (subjectList.size() != 1 || !targetList.isEmpty()) {
                    throw new IllegalArgumentException("RETIRE requires exactly one subject and no target; a retirement has no successor.");
                }
            }
            case MERGE -> {
                // Two subjects at minimum: merging one identity into itself is not an operation.
                if (subjectList.size() < 2 || targetList.size() != 1) {
                    throw new IllegalArgumentException("MERGE requires at least two subjects and exactly one surviving identity.");
                }
            }
            case SPLIT -> {
                // The whole content of a split is that one identity becomes several.
                if (subjectList.size() != 1 || targetList.size() < 2) {
                    throw new IllegalArgumentException("SPLIT requires exactly one subject and at least two resulting identities.");
                }
            }
            case ENRICH -> {
                // The identity persists: subject and target are the same uid. What changes is which
                // semantic variant counts as its current state, named in the enrichment block.
                if (subjectList.size() != 1 || !targetList.equals(subjectList)) {
                    throw new IllegalArgumentException("ENRICH requires exactly one subject, named again as its only target.");
                }
                if (enrichment == null) throw new IllegalArgumentException("ENRICH requires an enrichment block (fromVariant, toVariant, toPackageId).");
                String from = string(enrichment.get("fromVariant"), "enrichment.fromVariant");
                String to = string(enrichment.get("toVariant"), "enrichment.toVariant");
                String pkg = string(enrichment.get("toPackageId"), "enrichment.toPackageId");
                if (!from.matches("[a-f0-9]{64}") || !to.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("ENRICH variants must be sha256 hex digests.");
                if (from.equals(to)) throw new IllegalArgumentException("ENRICH from and to variants are identical; nothing is enriched.");
                if (!pkg.matches("pkg-[a-f0-9]{16}")) throw new IllegalArgumentException("ENRICH toPackageId must be a package id.");
                if (enrichment.size() != 3) throw new IllegalArgumentException("ENRICH enrichment block carries exactly fromVariant, toVariant and toPackageId.");
            }
        }
        if (operation != Kind.ENRICH && enrichment != null) {
            throw new IllegalArgumentException(operation + " does not carry an enrichment block.");
        }
        if (operation != Kind.MERGE && operation != Kind.ENRICH) {
            for (String subject : subjectList) {
                if (targetList.contains(subject)) {
                    throw new IllegalArgumentException("An identity may not be both subject and target of " + operation + ": " + subject);
                }
            }
        } else if (!targetList.stream().allMatch(subjectList::contains) && targetList.stream().anyMatch(subjectList::contains)) {
            // Unreachable given a single target, but keeps the intent explicit if the shape changes.
            throw new IllegalArgumentException("MERGE target must be either one of the subjects or a distinct registered identity.");
        }

        IdentityOperation draft = new IdentityOperation(null, operation, subjectList, targetList,
                List.copyOf(reasonCodes), justification, evidence, authority, recordedAt, enrichment);
        String operationId = StableIdentifiers.forJson("idop", 16, draft.projection());
        return new IdentityOperation(operationId, operation, subjectList, targetList,
                List.copyOf(reasonCodes), justification, evidence, authority, recordedAt, enrichment);
    }

    /** Reads an operation back from its stored form and re-derives its content address. */
    public static IdentityOperation fromMap(Map<String,Object> raw) {
        if (!RECORD_VERSION.equals(raw.get("recordVersion"))) {
            throw new IllegalArgumentException("Unsupported identity operation recordVersion: " + raw.get("recordVersion"));
        }
        List<Map<String,Object>> evidence = new ArrayList<>();
        if (raw.get("evidence") instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?,?> map)) throw new IllegalArgumentException("Identity operation evidence must be objects.");
                @SuppressWarnings("unchecked") Map<String,Object> typed = (Map<String,Object>) map;
                evidence.add(typed);
            }
        }
        IdentityOperation rebuilt = create(
                Kind.valueOf(string(raw.get("operation"), "operation")),
                strings(raw.get("subjects"), "subjects"),
                strings(raw.get("targets"), "targets"),
                strings(raw.get("reasonCodes"), "reasonCodes"),
                string(raw.get("justification"), "justification"),
                evidence,
                raw.get("authority") instanceof String s ? s : null,
                string(raw.get("recordedAt"), "recordedAt"),
                raw.get("enrichment") == null ? null : Json.object(raw.get("enrichment"), "enrichment"));
        if (!rebuilt.operationId().equals(raw.get("operationId"))) {
            throw new IllegalArgumentException("Identity operation content address does not match its contents: "
                    + raw.get("operationId") + " expected " + rebuilt.operationId() + ".");
        }
        return rebuilt;
    }

    /** The lifecycle state this operation leaves its subjects in. */
    public String subjectState() {
        return switch (operation) {
            case SUPERSEDE -> SUPERSEDED;
            case RETIRE -> RETIRED;
            case MERGE -> MERGED;
            case SPLIT -> SPLIT_STATE;
            case ENRICH -> ACTIVE;
        };
    }

    /** The meaning-bearing projection the content address is taken over. Excludes the address itself. */
    private Map<String,Object> projection() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("operation", operation.name());
        out.put("subjects", new ArrayList<>(subjects));
        out.put("targets", new ArrayList<>(targets));
        out.put("reasonCodes", new ArrayList<>(reasonCodes));
        out.put("justification", justification);
        out.put("evidence", Json.parse(Json.canonical(evidence)));
        if (authority != null) out.put("authority", authority);
        out.put("recordedAt", recordedAt);
        if (enrichment != null) out.put("enrichment", Json.parse(Json.canonical(enrichment)));
        return out;
    }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("operationId", operationId);
        out.put("operation", operation.name());
        out.put("subjects", new ArrayList<>(subjects));
        out.put("targets", new ArrayList<>(targets));
        out.put("reasonCodes", new ArrayList<>(reasonCodes));
        out.put("justification", justification);
        if (!evidence.isEmpty()) out.put("evidence", Json.parse(Json.canonical(evidence)));
        if (authority != null) out.put("authority", authority);
        out.put("recordedAt", recordedAt);
        if (enrichment != null) out.put("enrichment", Json.parse(Json.canonical(enrichment)));
        return out;
    }

    private static List<String> distinct(List<String> values, String label) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            requireNonBlank(value, label + " entry");
            if (!seen.add(value)) throw new IllegalArgumentException("Identity operation " + label + " contains a duplicate: " + value);
        }
        return List.copyOf(seen);
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identity operation " + label + " must be non-blank.");
    }

    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException("Identity operation " + label + " must be a non-blank string.");
    }

    private static List<String> strings(Object value, String label) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Identity operation " + label + " must be an array.");
        for (Object item : list) out.add(string(item, label + " entry"));
        return out;
    }
}
