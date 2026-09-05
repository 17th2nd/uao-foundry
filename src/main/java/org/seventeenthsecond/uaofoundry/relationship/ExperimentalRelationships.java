package org.seventeenthsecond.uaofoundry.relationship;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds an <em>experimental typed relationship record</em> — or the unresolved finding that
 * replaces it — from one candidate relationship, the package's candidate→uid map and a loaded
 * {@link RelationshipTypeEdition}.
 *
 * <p>This is the single derivation shared by the manufacturing pipeline (stage 11) and the package
 * verifier, exactly as {@code IdentityKernel} is shared for identities: the verifier reconstructs
 * every record from the package's own candidates and its embedded edition copy and demands
 * byte-equality, so a record cannot be edited, added or elevated after manufacture.
 *
 * <p>What a record is and is not. It is a relationship whose type resolved in a declared,
 * digest-pinned RTR-format edition and whose instance passed RTR §10.1 structural validation, with
 * every participant bound to a persistent uid. It is <b>not</b> a CSS URO: CSS 2026.2
 * {@code uri_versioned} rejects a non-{@code asa.core} type id (ASA-SPEC-0006 §10.3 AU-1), and the
 * edition's domain types are {@code proposed}, not admitted. Both facts travel on the record as
 * {@code undetermined} diagnostics that no aggregation may collapse into pass or fail.
 */
public final class ExperimentalRelationships {
    public static final String RECORD_VERSION = "0.1.0";
    public static final String STATUS = "EXPERIMENTAL_TYPED_RELATIONSHIP";
    public static final String ADMISSION_OVERRIDE = "FOUNDRY_EXPERIMENT_002_OPERATOR_AUTHORISED";

    private ExperimentalRelationships() {}

    /** Outcome of building one candidate: exactly one of {@code record} / {@code unresolved} is non-null. */
    public record Outcome(Map<String,Object> record, Map<String,Object> unresolved) {}

    public static Outcome build(Map<String,Object> candidate, Map<String,Object> candidateToUao,
                                Map<String,String> labelsByUid, RelationshipTypeEdition edition) {
        String candidateId = string(candidate.get("candidateId"), "candidateId");
        String typeVersion = string(candidate.get("typeVersion"), "typeVersion");
        List<Map<String,Object>> participants = new ArrayList<>();
        Map<String,List<String>> roleBindings = new TreeMap<>();
        int bound = 0;
        for (Object raw : list(candidate.get("participants"))) {
            Map<String,Object> participant = map(raw);
            String ref = string(participant.get("candidateIdentityRef"), "candidateIdentityRef");
            String role = string(participant.get("role"), "role");
            Object uaoId = candidateToUao.get(ref);
            Map<String,Object> record = new LinkedHashMap<>();
            record.put("role", role);
            record.put("candidateIdentityRef", ref);
            record.put("binding", uaoId == null ? "UNRESOLVED" : "RESOLVED");
            if (uaoId != null) {
                record.put("uaoId", uaoId);
                bound++;
                roleBindings.computeIfAbsent(role, ignored -> new ArrayList<>()).add(String.valueOf(uaoId));
            }
            participants.add(record);
        }
        String bindingStatus = bound == 0 ? "UNBOUND" : bound == participants.size() ? "ALL_PARTICIPANTS_BOUND" : "PARTIALLY_BOUND";
        Map<String,Object> literals = candidate.get("identityLiterals") instanceof Map<?,?> ? map(candidate.get("identityLiterals")) : Map.of();

        Map<String,Object> typeRecord = edition.resolve(typeVersion);
        if (typeRecord == null) {
            return new Outcome(null, unresolved(candidate, "RTR-TYPE-UNKNOWN",
                    "No record for " + typeVersion + " in relationship type edition " + edition.registryVersion()
                            + " (" + edition.digest() + "). The predicate is not validated and the candidate is retained unresolved.",
                    participants, bindingStatus, List.of(edition.diagnostic("RTR-TYPE-UNKNOWN", typeVersion, null, "no registry record for " + typeVersion))));
        }
        String typeId = string(typeRecord.get("id"), "type id");
        if (bound != participants.size()) {
            return new Outcome(null, unresolved(candidate, "PARTICIPANT_UNBOUND",
                    "One or more participants did not resolve to a persistent identity; a relationship is never completed by inventing a uid.",
                    participants, bindingStatus, List.of()));
        }
        List<Map<String,Object>> diagnostics = edition.validateInstance(typeRecord, roleBindings, literals);
        if (!diagnostics.isEmpty()) {
            return new Outcome(null, unresolved(candidate, "URO-INSTANCE-INVALID",
                    "The instance failed RTR §10.1 structural validation against " + typeId + "; see diagnostics.",
                    participants, bindingStatus, diagnostics));
        }

        Map<String,Object> definition = map(typeRecord.get("definition"));
        Map<String,Object> semantics = map(typeRecord.get("semantics"));
        List<String> identityRoles = strings(semantics.get("identity_roles"));
        List<String> identityLiteralNames = strings(semantics.get("identity_literals"));
        Map<String,Object> identityKey = new LinkedHashMap<>();
        identityKey.put("type", typeId);
        Map<String,Object> keyRoles = new TreeMap<>();
        for (String role : identityRoles) {
            List<String> uids = new ArrayList<>(roleBindings.getOrDefault(role, List.of()));
            uids.sort(String::compareTo);
            keyRoles.put(role, uids);
        }
        identityKey.put("roles", keyRoles);
        Map<String,Object> keyLiterals = new TreeMap<>();
        for (String name : identityLiteralNames) if (literals.containsKey(name)) keyLiterals.put(name, literals.get(name));
        identityKey.put("literals", keyLiterals);
        String relationshipId = StableIdentifiers.forJson("urx", 12, identityKey);

        Map<String,Object> typeEdition = new LinkedHashMap<>();
        typeEdition.put("registryVersion", edition.registryVersion());
        typeEdition.put("digest", edition.digest());
        typeEdition.put("namespace", typeRecord.get("namespace"));
        typeEdition.put("admissionState", RelationshipTypeEdition.admissionState(typeRecord));
        typeEdition.put("admissionOverride", RelationshipTypeEdition.bindable(typeRecord) ? null : ADMISSION_OVERRIDE);

        List<Map<String,Object>> undetermined = new ArrayList<>();
        if (!RelationshipTypeEdition.CSS_ALIAS.matcher(typeId).matches()) {
            Map<String,Object> au1 = new LinkedHashMap<>();
            au1.put("code", "ARCHITECTURAL-UNCERTAINTY");
            au1.put("severity", "error");
            au1.put("outcome", "undetermined");
            au1.put("uncertainty_id", "AU-1");
            au1.put("subject", relationshipId);
            au1.put("type_version", typeId);
            au1.put("detail", "CSS 2026.2 uri_versioned (^asa\\.core/[a-z_]+@\\d+$) rejects this type id and the edition record carries no css_type_version_alias; this record is a Foundry experimental relationship, not a CSS URO.");
            au1.put("deferred_to", "ASA-SPEC-0006 §12 / Council");
            au1.put("authority", "ASA-SPEC-0006 §10.3");
            au1.put("registry_version", edition.registryVersion());
            undetermined.add(au1);
        }
        if (!RelationshipTypeEdition.bindable(typeRecord)) {
            Map<String,Object> notAdmitted = new LinkedHashMap<>();
            notAdmitted.put("code", "RTR-TYPE-NOT-ADMITTED");
            notAdmitted.put("severity", "warning");
            notAdmitted.put("outcome", "undetermined");
            notAdmitted.put("subject", relationshipId);
            notAdmitted.put("type_version", typeId);
            notAdmitted.put("detail", "type " + typeId + " admission state is " + RelationshipTypeEdition.admissionState(typeRecord)
                    + "; bound under " + ADMISSION_OVERRIDE + ", which is an operator experiment decision and not an ASA admission.");
            notAdmitted.put("authority", "ASA-SPEC-0006 §5.2, §9 step 5");
            notAdmitted.put("registry_version", edition.registryVersion());
            undetermined.add(notAdmitted);
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("recordVersion", RECORD_VERSION);
        out.put("relationshipId", relationshipId);
        out.put("status", STATUS);
        out.put("certifying", Boolean.FALSE);
        out.put("typeId", typeId);
        out.put("typeName", typeId.substring(typeId.indexOf('/') + 1, typeId.lastIndexOf('@')));
        out.put("typeEdition", typeEdition);
        out.put("symmetric", Boolean.TRUE.equals(definition.get("symmetric")));
        out.put("participants", participants);
        out.put("identityLiterals", new TreeMap<>(literals));
        out.put("contextualBindings", Json.parse(Json.canonical(candidate.getOrDefault("contextualBindings", List.of()))));
        out.put("sourceRefs", Json.parse(Json.canonical(candidate.get("sourceRefs"))));
        out.put("candidateId", candidateId);
        out.put("basis", candidate.get("basis") instanceof String b ? b : "UNSTATED");
        out.put("statement", statement(typeId, definition, participants, labelsByUid));
        out.put("diagnostics", undetermined);
        out.put("outcome", undetermined.isEmpty() ? "pass" : "undetermined");
        out.put("stateVersion", Hashes.canonicalJson(stateProjection(out)));
        return new Outcome(out, null);
    }

    /** Everything meaning-bearing except the state version itself. */
    public static Map<String,Object> stateProjection(Map<String,Object> record) {
        Map<String,Object> projection = new TreeMap<>(record);
        projection.remove("stateVersion");
        return projection;
    }

    private static String statement(String typeId, Map<String,Object> definition, List<Map<String,Object>> participants, Map<String,String> labelsByUid) {
        String name = typeId.substring(typeId.indexOf('/') + 1, typeId.lastIndexOf('@'));
        if (Boolean.TRUE.equals(definition.get("symmetric"))) {
            List<String> names = new ArrayList<>();
            for (Map<String,Object> p : participants) names.add(label(p, labelsByUid));
            names.sort(String::compareTo);
            return String.join(" — ", names) + " [" + name + "]";
        }
        List<Object> roles = list(definition.get("roles"));
        // Role-named rendering: the first declared role is the subject position by convention of
        // this edition (roles are name-sorted for hashing, so the builder orders by declaration of
        // the semantic pair recorded in the type name: author-of = author → work, etc.).
        String subjectRole = subjectRole(name, roles);
        List<String> subjects = new ArrayList<>(), objects = new ArrayList<>();
        for (Map<String,Object> p : participants) {
            if (subjectRole.equals(p.get("role"))) subjects.add(label(p, labelsByUid)); else objects.add(label(p, labelsByUid));
        }
        subjects.sort(String::compareTo); objects.sort(String::compareTo);
        return String.join(", ", subjects) + " —" + name + "→ " + String.join(", ", objects);
    }

    private static String subjectRole(String typeName, List<Object> roles) {
        Map<String,String> subjects = Map.ofEntries(
                Map.entry("author-of", "author"), Map.entry("co-created", "creator"), Map.entry("created", "creator"),
                Map.entry("developed", "developer"), Map.entry("architect-of", "architect"), Map.entry("presented", "presenter"),
                Map.entry("contributed-to", "contributor"), Map.entry("member-of", "member"), Map.entry("influenced", "influencer"),
                Map.entry("influenced-by", "influenced-party"), Map.entry("about", "work"), Map.entry("instance-of", "instance"),
                Map.entry("subclass-of", "subclass"), Map.entry("part-of", "part"), Map.entry("precursor-to", "precursor"),
                Map.entry("supports", "source"), Map.entry("challenges", "source"), Map.entry("contradicts", "source"),
                Map.entry("supersedes", "source"), Map.entry("stance", "source"));
        String subject = subjects.get(typeName);
        if (subject != null) return subject;
        return String.valueOf(map(roles.getFirst()).get("name"));
    }

    private static String label(Map<String,Object> participant, Map<String,String> labelsByUid) {
        String uid = String.valueOf(participant.get("uaoId"));
        String label = labelsByUid.get(uid);
        return label == null ? uid : label + " (" + uid + ")";
    }

    private static Map<String,Object> unresolved(Map<String,Object> candidate, String code, String description,
                                                 List<Map<String,Object>> participants, String bindingStatus,
                                                 List<Map<String,Object>> diagnostics) {
        Map<String,Object> finding = new LinkedHashMap<>();
        finding.put("candidateId", candidate.get("candidateId"));
        finding.put("code", code);
        finding.put("description", description);
        finding.put("typeVersion", candidate.get("typeVersion"));
        finding.put("participants", participants);
        finding.put("identityBindingStatus", bindingStatus);
        finding.put("identityLiterals", candidate.get("identityLiterals"));
        finding.put("contextualBindings", candidate.get("contextualBindings"));
        finding.put("sourceRefs", candidate.get("sourceRefs"));
        finding.put("diagnostics", diagnostics);
        return finding;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value) {
        if (!(value instanceof Map<?,?> m)) throw new IllegalArgumentException("Expected an object, found " + value);
        return (Map<String,Object>) m;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> l)) throw new IllegalArgumentException("Expected an array, found " + value);
        return (List<Object>) l;
    }
    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException(label + " must be a non-blank string.");
    }
    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        for (Object item : list(value)) out.add(String.valueOf(item));
        return out;
    }
}
