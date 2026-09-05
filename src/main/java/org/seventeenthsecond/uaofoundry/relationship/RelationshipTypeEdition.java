package org.seventeenthsecond.uaofoundry.relationship;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.math.BigDecimal;
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
import java.util.regex.Pattern;

/**
 * A loaded, validated Relationship Type Registry facet in the ASA-SPEC-0006 (RTR) format.
 *
 * <p>The Foundry consumes an <em>edition</em>: a facet file whose records, definition hashes and
 * registry digest it recomputes on load (RTR §8) and refuses whole on any mismatch (§9 step 3).
 * Nothing here creates type authority. Whether a resolved type may bind an instance is a separate
 * question answered by {@link #admissionState} and by the caller's declared override; the edition
 * only reports what the facet says.
 *
 * <p>Validation is programmatic rather than JSON-Schema driven because the checked-in schema
 * validator has no {@code $defs} resolution; the checks mirror the ASA kernel's
 * {@code validate_registry_document} closely enough that the ASA 2026.2 facet loads and its
 * digest recomputes byte-for-byte (see {@code RelationshipEditionTest}).
 */
public final class RelationshipTypeEdition {
    public static final Pattern TYPE_ID = Pattern.compile("^asa:type:[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*@[1-9][0-9]*$");
    public static final Pattern CSS_ALIAS = Pattern.compile("^asa\\.core/[a-z_]+@[0-9]+$");
    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");
    private static final Pattern VERSION = Pattern.compile("^[0-9]{4}\\.[1-9][0-9]*$");
    private static final Pattern SHA = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Set<String> STATES = Set.of("proposed", "admitted", "deprecated", "superseded", "withdrawn");
    private static final Set<String> BINDABLE = Set.of("admitted", "deprecated");
    private static final Set<String> KINDS = Set.of("uao", "uro", "perspective");
    private static final Set<String> RESERVED_ROLES = Set.of("evidence", "claimant", "observer");

    private final Map<String,Object> document;
    private final Map<String,Map<String,Object>> records = new TreeMap<>();
    private final String registryVersion;
    private final String digest;
    private final String source;

    private RelationshipTypeEdition(Map<String,Object> document, String source) {
        this.document = document;
        this.source = source;
        this.registryVersion = string(map(document.get("$meta"), "$meta").get("registry_version"), "$meta.registry_version");
        this.digest = string(document.get("digest"), "digest");
        for (Object raw : list(document.get("types"), "types")) {
            Map<String,Object> record = map(raw, "type record");
            records.put(string(record.get("id"), "type id"), record);
        }
    }

    public static RelationshipTypeEdition load(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException("RTR-SCHEMA: relationship type edition is not a file: " + normalized);
        return fromDocument(map(FileOps.readJson(normalized), "relationship type edition"), normalized.toString());
    }

    public static RelationshipTypeEdition fromDocument(Map<String,Object> document, String source) {
        Map<String,Object> copy = map(Json.parse(Json.canonical(document)), "relationship type edition");
        validateDocument(copy);
        return new RelationshipTypeEdition(copy, source);
    }

    public String registryVersion() { return registryVersion; }
    public String digest() { return digest; }
    public String source() { return source; }
    public Map<String,Object> document() { return map(Json.parse(Json.canonical(document)), "edition copy"); }
    public List<String> typeIds() { return new ArrayList<>(records.keySet()); }

    /** The provenance-bearing summary a package or report carries for this edition. */
    public Map<String,Object> summary() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("registryVersion", registryVersion);
        out.put("digest", digest);
        out.put("recordCount", new BigDecimal(records.size()));
        out.put("provenanceAnchor", map(document.get("$meta"), "$meta").get("provenance_anchor"));
        out.put("specAuthority", map(document.get("$meta"), "$meta").get("spec_authority"));
        return out;
    }

    /** RTR §9 step 4: exact id, else CSS alias, else absent (null). Nothing about admission is decided here. */
    public Map<String,Object> resolve(String typeVersion) {
        Map<String,Object> direct = records.get(typeVersion);
        if (direct != null) return direct;
        for (Map<String,Object> record : records.values()) {
            Object alias = map(record.get("semantics"), "semantics").get("css_type_version_alias");
            if (typeVersion.equals(alias)) return record;
        }
        return null;
    }

    public static String admissionState(Map<String,Object> record) {
        return string(map(record.get("admission"), "admission").get("state"), "admission.state");
    }

    public static boolean bindable(Map<String,Object> record) { return BINDABLE.contains(admissionState(record)); }

    /**
     * RTR §10.1 instance validation V1–V6 and V8 for one resolved record.
     *
     * @param roleBindings role name → participant uids bound under that role
     * @param literals     literal name → value
     * @return diagnostics in RTR §10.2 shape; empty when the instance is structurally valid
     */
    public List<Map<String,Object>> validateInstance(Map<String,Object> record, Map<String,List<String>> roleBindings, Map<String,Object> literals) {
        List<Map<String,Object>> out = new ArrayList<>();
        Map<String,Object> definition = map(record.get("definition"), "definition");
        String typeId = string(record.get("id"), "id");
        Map<String,Map<String,Object>> roles = new LinkedHashMap<>();
        for (Object raw : list(definition.get("roles"), "roles")) { Map<String,Object> role = map(raw, "role"); roles.put(string(role.get("name"), "role name"), role); }
        for (Map.Entry<String,List<String>> bound : roleBindings.entrySet()) {
            String name = bound.getKey();
            if (RESERVED_ROLES.contains(name)) out.add(diagnostic("URO-RESERVED-ROLE", typeId, name, "role '" + name + "' is reserved"));
            Map<String,Object> role = roles.get(name);
            if (role == null) { out.add(diagnostic("URO-ROLE-UNKNOWN", typeId, name, "role '" + name + "' not declared by " + typeId)); continue; }
            List<String> refs = bound.getValue();
            if (new LinkedHashSet<>(refs).size() != refs.size()) out.add(diagnostic("URO-ROLE-CARDINALITY", typeId, name, "role " + name + " binds the same participant twice"));
            int min = integer(role.get("min")), max = integer(role.get("max"));
            if (refs.size() < min || refs.size() > max) out.add(diagnostic("URO-ROLE-CARDINALITY", typeId, name, "role " + name + " expected " + min + ".." + max + ", found " + refs.size()));
            List<String> binds = strings(role.get("binds"));
            for (String ref : refs) {
                String kind = participantKind(ref);
                if (kind == null || !binds.contains(kind)) out.add(diagnostic("URO-PARTICIPANT-KIND", typeId, name, "role " + name + ": " + ref + " kind " + kind + " not in " + binds));
            }
        }
        for (Map<String,Object> role : roles.values()) {
            String name = string(role.get("name"), "role name");
            if (integer(role.get("min")) >= 1 && !roleBindings.containsKey(name)) out.add(diagnostic("URO-ROLE-MISSING", typeId, name, "role " + name + " requires at least " + role.get("min") + " participant(s)"));
        }
        if (Boolean.TRUE.equals(definition.get("symmetric"))) {
            boolean ok = roleBindings.size() == 1 && roleBindings.values().iterator().next().size() == 2
                    && new LinkedHashSet<>(roleBindings.values().iterator().next()).size() == 2;
            if (!ok) out.add(diagnostic("URO-SYMMETRY", typeId, null, "symmetric type requires its single role bound to exactly 2 distinct participants"));
        }
        Map<String,Map<String,Object>> declaredLiterals = new LinkedHashMap<>();
        for (Object raw : list(definition.get("literals"), "literals")) { Map<String,Object> literal = map(raw, "literal"); declaredLiterals.put(string(literal.get("name"), "literal name"), literal); }
        for (Map.Entry<String,Object> literal : literals.entrySet()) {
            Map<String,Object> declared = declaredLiterals.get(literal.getKey());
            if (declared == null) { out.add(diagnostic("URO-LITERAL-UNKNOWN", typeId, null, "literal '" + literal.getKey() + "' not declared by " + typeId)); continue; }
            if (!literalOk(string(declared.get("datatype"), "datatype"), literal.getValue())) out.add(diagnostic("URO-LITERAL-DATATYPE", typeId, null, "literal " + literal.getKey() + " is not a valid " + declared.get("datatype")));
        }
        return out;
    }

    /** RTR §10.2 diagnostic record. */
    public Map<String,Object> diagnostic(String code, String typeId, String role, String detail) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("severity", "error");
        out.put("outcome", "fail");
        out.put("subject", typeId);
        out.put("type_version", typeId);
        if (role != null) out.put("role", role);
        out.put("detail", detail);
        out.put("authority", "ASA-SPEC-0006 §10.1");
        out.put("registry_version", registryVersion);
        return out;
    }

    public static String participantKind(String ref) {
        if (ref == null) return null;
        if (ref.matches("^uao-[a-f0-9]{12}$")) return "uao";
        if (ref.matches("^uro-[a-f0-9]{12}$")) return "uro";
        if (ref.startsWith("asa:perspective:")) return "perspective";
        return null;
    }

    // ------------------------------------------------------------------ document validation (RTR §6 tail, §8)

    private static void validateDocument(Map<String,Object> doc) {
        for (String key : List.of("$meta", "participant_kinds", "types", "digest")) {
            if (!doc.containsKey(key)) throw fail("RTR-SCHEMA", "facet is missing " + key);
        }
        if (doc.size() != 4) throw fail("RTR-SCHEMA", "facet carries fields outside $meta/participant_kinds/types/digest");
        Map<String,Object> meta = map(doc.get("$meta"), "$meta");
        for (String key : List.of("title", "registry_version", "schema", "subordination_clause_ack", "provenance_anchor", "css_schema_version", "spec_authority")) {
            if (!meta.containsKey(key)) throw fail("RTR-SCHEMA", "$meta is missing " + key);
        }
        String version = string(meta.get("registry_version"), "$meta.registry_version");
        if (!VERSION.matcher(version).matches()) throw fail("RTR-SCHEMA", "registry_version notation: " + version);
        if (!Boolean.TRUE.equals(meta.get("subordination_clause_ack"))) throw fail("RTR-SCHEMA", "subordination_clause_ack must be true");
        String anchor = string(meta.get("provenance_anchor"), "$meta.provenance_anchor");
        if (!anchor.matches("^(D-[0-9]{3}|CR-[0-9]{3}|PROPOSED)$")) throw fail("RTR-SCHEMA", "provenance_anchor notation: " + anchor);
        List<String> kinds = strings(doc.get("participant_kinds"));
        if (kinds.isEmpty() || !KINDS.containsAll(kinds) || new LinkedHashSet<>(kinds).size() != kinds.size()) throw fail("RTR-SCHEMA", "participant_kinds invalid");
        String digest = string(doc.get("digest"), "digest");
        if (!SHA.matcher(digest).matches()) throw fail("RTR-SCHEMA", "digest notation");

        List<Object> types = list(doc.get("types"), "types");
        String previousId = null;
        Set<String> ids = new LinkedHashSet<>();
        boolean coreSeen = false;
        for (Object raw : types) {
            Map<String,Object> record = map(raw, "type record");
            for (String key : List.of("id", "namespace", "owner", "admission", "definition", "definition_hash", "semantics", "evolution", "traceability")) {
                if (!record.containsKey(key)) throw fail("RTR-SCHEMA", "record is missing " + key);
            }
            String id = string(record.get("id"), "record id");
            if (!TYPE_ID.matcher(id).matches()) throw fail("RTR-SCHEMA", "type id notation: " + id);
            if (previousId != null && id.compareTo(previousId) <= 0) throw fail("RTR-SCHEMA", "types are not sorted by id at " + id);
            previousId = id;
            if (!ids.add(id)) throw fail("RTR-SCHEMA", "duplicate type id " + id);
            String namespace = string(record.get("namespace"), "namespace");
            if (!id.startsWith("asa:type:" + namespace + "/")) throw fail("RTR-SCHEMA", "namespace does not match id: " + id);
            if ("asa.core".equals(namespace)) coreSeen = true;
            Map<String,Object> admission = map(record.get("admission"), "admission");
            String state = string(admission.get("state"), "admission.state");
            if (!STATES.contains(state)) throw fail("RTR-SCHEMA", "admission state '" + state + "' at " + id);
            if ("proposed".equals(state) && admission.get("decision_ref") != null) throw fail("RTR-SCHEMA", "proposed type carries a decision_ref: " + id);
            if (!"proposed".equals(state) && !(admission.get("decision_ref") instanceof String)) throw fail("RTR-SCHEMA", "admitted type lacks a decision_ref: " + id);
            Map<String,Object> definition = map(record.get("definition"), "definition");
            if (!id.equals(definition.get("id"))) throw fail("RTR-SCHEMA", "definition.id differs from record id at " + id);
            validateDefinition(definition, id);
            String expectedHash = "sha256:" + Hashes.canonicalJson(definition);
            if (!expectedHash.equals(record.get("definition_hash"))) throw fail("RTR-DEFINITION-HASH", "definition_hash does not recompute for " + id);
            map(record.get("semantics"), "semantics");
            map(record.get("evolution"), "evolution");
        }
        if (!coreSeen) throw fail("RTR-SCHEMA", "facet carries no asa.core meta-types");
        String recomputed = registryDigest(doc);
        if (!recomputed.equals(digest)) throw fail("RTR-DIGEST-MISMATCH", "registry digest does not recompute: facet says " + digest + ", recomputed " + recomputed);
    }

    private static void validateDefinition(Map<String,Object> definition, String id) {
        for (String key : List.of("id", "meta", "symmetric", "evidence", "roles", "literals", "provenance_roles")) {
            if (!definition.containsKey(key)) throw fail("RTR-SCHEMA", "definition is missing " + key + " at " + id);
        }
        boolean meta = Boolean.TRUE.equals(definition.get("meta"));
        boolean symmetric = Boolean.TRUE.equals(definition.get("symmetric"));
        String evidence = string(definition.get("evidence"), "evidence");
        if (!Set.of("definitional", "supported").contains(evidence)) throw fail("RTR-SCHEMA", "evidence value at " + id);
        if (meta && !"definitional".equals(evidence)) throw fail("RTR-SCHEMA", "meta-type must be definitional at " + id);
        List<Object> roles = list(definition.get("roles"), "roles");
        String previous = null;
        for (Object raw : roles) {
            Map<String,Object> role = map(raw, "role");
            String name = string(role.get("name"), "role name");
            if (!NAME.matcher(name).matches()) throw fail("RTR-SCHEMA", "role name notation '" + name + "' at " + id);
            if (RESERVED_ROLES.contains(name)) throw fail("RTR-SCHEMA", "reserved role name '" + name + "' at " + id);
            if (previous != null && name.compareTo(previous) <= 0) throw fail("RTR-SCHEMA", "roles are not sorted by name at " + id);
            previous = name;
            if (!Set.of("participant", "contextual").contains(string(role.get("kind"), "role kind"))) throw fail("RTR-SCHEMA", "role kind at " + id);
            List<String> binds = strings(role.get("binds"));
            if (binds.isEmpty() || !KINDS.containsAll(binds)) throw fail("RTR-SCHEMA", "role binds at " + id);
            int min = integer(role.get("min")), max = integer(role.get("max"));
            if (min < 0 || max < min) throw fail("RTR-SCHEMA", "role cardinality at " + id);
            if (!(role.get("identity") instanceof Boolean)) throw fail("RTR-SCHEMA", "role identity flag at " + id);
        }
        if (symmetric) {
            if (roles.size() != 1) throw fail("RTR-SCHEMA", "symmetric type must declare exactly one role at " + id);
            Map<String,Object> only = map(roles.getFirst(), "role");
            if (integer(only.get("min")) != 2 || integer(only.get("max")) != 2 || !"participant".equals(only.get("kind"))) throw fail("RTR-SCHEMA", "symmetric role must be participant 2..2 at " + id);
        }
        String previousLiteral = null;
        for (Object raw : list(definition.get("literals"), "literals")) {
            Map<String,Object> literal = map(raw, "literal");
            String name = string(literal.get("name"), "literal name");
            if (!NAME.matcher(name).matches()) throw fail("RTR-SCHEMA", "literal name notation at " + id);
            if (previousLiteral != null && name.compareTo(previousLiteral) <= 0) throw fail("RTR-SCHEMA", "literals are not sorted by name at " + id);
            previousLiteral = name;
            if (!Set.of("string", "boolean", "integer", "decimal").contains(string(literal.get("datatype"), "datatype"))) throw fail("RTR-SCHEMA", "literal datatype at " + id);
        }
    }

    /** RTR §8.3: digest over registry_version, sorted participant kinds, and {id, admission, definition_hash, evolution} sorted by id. */
    public static String registryDigest(Map<String,Object> doc) {
        Map<String,Object> structure = new LinkedHashMap<>();
        structure.put("registry_version", map(doc.get("$meta"), "$meta").get("registry_version"));
        List<String> kinds = new ArrayList<>(strings(doc.get("participant_kinds")));
        kinds.sort(Comparator.naturalOrder());
        structure.put("participant_kinds", kinds);
        List<Map<String,Object>> records = new ArrayList<>();
        for (Object raw : list(doc.get("types"), "types")) {
            Map<String,Object> record = map(raw, "type record");
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", record.get("id"));
            item.put("admission", record.get("admission"));
            item.put("definition_hash", record.get("definition_hash"));
            item.put("evolution", record.get("evolution"));
            records.add(item);
        }
        records.sort(Comparator.comparing(r -> String.valueOf(r.get("id"))));
        structure.put("records", records);
        return "sha256:" + Hashes.canonicalJson(structure);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean literalOk(String datatype, Object value) {
        return switch (datatype) {
            case "string" -> value instanceof String;
            case "boolean" -> value instanceof Boolean;
            case "integer" -> value instanceof BigDecimal n && n.stripTrailingZeros().scale() <= 0;
            case "decimal" -> value instanceof String s && s.matches("^-?(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?$");
            default -> false;
        };
    }

    private static IllegalArgumentException fail(String code, String detail) { return new IllegalArgumentException(code + ": " + detail); }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value, String label) {
        if (!(value instanceof Map<?,?> m)) throw fail("RTR-SCHEMA", label + " must be an object");
        return (Map<String,Object>) m;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value, String label) {
        if (!(value instanceof List<?> l)) throw fail("RTR-SCHEMA", label + " must be an array");
        return (List<Object>) l;
    }
    private static String string(Object value, String label) {
        if (value instanceof String s && !s.isBlank()) return s;
        throw fail("RTR-SCHEMA", label + " must be a non-blank string");
    }
    private static int integer(Object value) {
        if (value instanceof BigDecimal n && n.stripTrailingZeros().scale() <= 0) return n.intValueExact();
        if (value instanceof Number n) return n.intValue();
        throw fail("RTR-SCHEMA", "expected an integer, found " + value);
    }
    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> l) for (Object item : l) out.add(String.valueOf(item));
        return out;
    }
}
