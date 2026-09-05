package org.seventeenthsecond.uaofoundry.relationship;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Experiment 002 — the minimum relationship primitive under an RTR-format edition.
 *
 * <p>Two boundaries are pinned here. Without a declared edition nothing changes (the ASA#29
 * fail-closed path is untouched, see {@code RelationshipBindingTest}). With one, a relationship is
 * registered only when its type resolves in the digest-pinned edition, its instance passes RTR
 * §10.1, and every participant is bound; everything else stays unresolved and the package stays
 * inadmissible. Records are re-derived by the verifier and never certify anything.
 */
class RelationshipEditionTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path EDITION = Path.of("config/relationship-types/foundry-exp002.json");
    private static final Path ASA_EDITION = Path.of("src/test/resources/asa/relationship_types-2026.2.json");
    private static final Path COW = Path.of("src/test/resources/fixtures/relationship-bearing-cow.json");
    private static final String PART_OF = "asa:type:foundry.exp002/part-of@1";

    @TempDir Path temp;

    // ---------------------------------------------------------------- edition loading (RTR §6, §8)

    @Test
    void theAsaEditionLoadsAndItsDigestRecomputesByteForByte() {
        // Cross-implementation agreement: the ASA kernel (Python, RFC 8785 JCS) produced this
        // digest; the Foundry's canonical JSON must reproduce it or the two would disagree about
        // what the governed facet is.
        RelationshipTypeEdition asa = RelationshipTypeEdition.load(ASA_EDITION);
        assertEquals("2026.2", asa.registryVersion());
        assertEquals("sha256:a0c6a69c4bfaf1ffde97f6187797e455c23bea5343706f78a678e66307a7059c", asa.digest());
        assertNotNull(asa.resolve("asa:type:asa.core/supports@1"));
        assertTrue(RelationshipTypeEdition.bindable(asa.resolve("asa:type:asa.core/supports@1")));
        assertNull(asa.resolve(PART_OF), "the ASA edition admits no domain type this experiment needs");
    }

    @Test
    void theFoundryEditionLoadsCarriesTheCoreAndIsProposedOnly() {
        RelationshipTypeEdition edition = RelationshipTypeEdition.load(EDITION);
        assertEquals("2026.902", edition.registryVersion());
        assertEquals(22, edition.typeIds().size());
        assertNotNull(edition.resolve("asa:type:asa.core/supports@1"), "the five core meta-types are carried verbatim");
        Map<String,Object> partOf = edition.resolve(PART_OF);
        assertNotNull(partOf);
        assertEquals("proposed", RelationshipTypeEdition.admissionState(partOf));
        assertFalse(RelationshipTypeEdition.bindable(partOf), "a proposed type is not bindable under RTR §9 step 5");
        assertEquals("PROPOSED", edition.summary().get("provenanceAnchor"));
    }

    @Test
    void aTamperedFacetFailsClosedOnDigestOrDefinitionHash() {
        Map<String,Object> doc = object(FileOps.readJson(EDITION));
        List<Object> types = array(doc.get("types"));
        Map<String,Object> first = object(types.getFirst());
        object(first.get("admission")).put("state", "admitted");
        object(first.get("admission")).put("decision_ref", "D-999");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RelationshipTypeEdition.fromDocument(doc, "tampered"));
        assertTrue(ex.getMessage().startsWith("RTR-DIGEST-MISMATCH"), ex.getMessage());

        Map<String,Object> doc2 = object(FileOps.readJson(EDITION));
        Map<String,Object> definition = object(object(array(doc2.get("types")).getLast()).get("definition"));
        object(array(definition.get("roles")).getFirst()).put("max", java.math.BigDecimal.valueOf(9));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> RelationshipTypeEdition.fromDocument(doc2, "tampered"));
        assertTrue(ex2.getMessage().startsWith("RTR-DEFINITION-HASH"), ex2.getMessage());
    }

    // ---------------------------------------------------------------- stage 11 under an edition

    @Test
    void aValidTypedRelationshipIsRegisteredWithProvenanceAndUndeterminedDiagnostics() {
        PipelineResult result = manufacture("valid", "cow", EDITION, RelationshipEditionTest::validPartOf);
        assertEquals("EXPERIMENTAL", result.publicationStatus());
        assertTrue(result.verificationPassed());
        List<Object> records = array(FileOps.readJson(result.packagePath().resolve("experimental-relationships.json")));
        assertEquals(1, records.size());
        Map<String,Object> record = object(records.getFirst());
        assertEquals("EXPERIMENTAL_TYPED_RELATIONSHIP", record.get("status"));
        assertEquals(Boolean.FALSE, record.get("certifying"));
        assertEquals(PART_OF, record.get("typeId"));
        assertEquals("EXPLICIT", record.get("basis"));
        assertEquals(List.of("src-cow-bio"), record.get("sourceRefs"));
        assertEquals("undetermined", record.get("outcome"), "AU-1 and non-admission must not collapse into pass");
        List<Object> diagnostics = array(record.get("diagnostics"));
        assertTrue(diagnostics.stream().anyMatch(d -> "ARCHITECTURAL-UNCERTAINTY".equals(object(d).get("code")) && "AU-1".equals(object(d).get("uncertainty_id"))));
        assertTrue(diagnostics.stream().anyMatch(d -> "RTR-TYPE-NOT-ADMITTED".equals(object(d).get("code"))));
        Map<String,Object> edition = object(record.get("typeEdition"));
        assertEquals("proposed", edition.get("admissionState"));
        assertEquals(ExperimentalRelationships.ADMISSION_OVERRIDE, edition.get("admissionOverride"));
        assertTrue(String.valueOf(record.get("statement")).contains("—part-of→"), String.valueOf(record.get("statement")));
        // Canonical UROs stay at zero: the record is not a CSS URO and never pretends to be.
        assertTrue(array(FileOps.readJson(result.packagePath().resolve("canonical-relationships.json"))).isEmpty());
        assertTrue(array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).isEmpty());
        assertTrue(Files.isRegularFile(result.packagePath().resolve("relationship-type-edition.json")));
        assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
    }

    @Test
    void anUnknownPredicateStaysUnresolvedAndThePackageStaysInadmissible() {
        PipelineResult result = manufacture("unknown", "cow", EDITION, fixture -> {
            validPartOf(fixture);
            relationship(fixture).put("typeVersion", "asa:type:foundry.exp002/nonsense@1");
        });
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        Map<String,Object> finding = object(array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).getFirst());
        assertEquals("RTR-TYPE-UNKNOWN", finding.get("code"));
        assertTrue(array(FileOps.readJson(result.packagePath().resolve("experimental-relationships.json"))).isEmpty());
        assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry-unknown"), SCHEMAS);
        assertThrows(IllegalArgumentException.class, () -> registry.register(result.packagePath()));
    }

    @Test
    void anIllegalRoleFailsRtrInstanceValidationAndStaysUnresolved() {
        PipelineResult result = manufacture("badrole", "cow", EDITION, fixture -> {
            validPartOf(fixture);
            object(array(relationship(fixture).get("participants")).getFirst()).put("role", "container");
        });
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        Map<String,Object> finding = object(array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).getFirst());
        assertEquals("URO-INSTANCE-INVALID", finding.get("code"));
        List<Object> diagnostics = array(finding.get("diagnostics"));
        assertTrue(diagnostics.stream().anyMatch(d -> "URO-ROLE-UNKNOWN".equals(object(d).get("code"))), diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(d -> "URO-ROLE-MISSING".equals(object(d).get("code"))), diagnostics.toString());
    }

    @Test
    void anUnboundParticipantIsNeverCompletedByInventingAnIdentity() {
        // The shipped fixture references cid-species, which is not a candidate identity.
        PipelineResult result = manufacture("unbound", "cow", EDITION, fixture -> relationship(fixture).put("typeVersion", PART_OF));
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        Map<String,Object> finding = object(array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).getFirst());
        assertEquals("PARTICIPANT_UNBOUND", finding.get("code"));
        assertEquals("PARTIALLY_BOUND", finding.get("identityBindingStatus"));
    }

    @Test
    void withoutADeclaredEditionNothingChanges() {
        PipelineResult result = manufacture("no-edition", "cow", null, RelationshipEditionTest::validPartOf);
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        assertFalse(Files.exists(result.packagePath().resolve("experimental-relationships.json")));
        assertFalse(Files.exists(result.packagePath().resolve("relationship-type-edition.json")));
        Map<String,Object> finding = object(array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json"))).getFirst());
        assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", finding.get("code"));
        assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
    }

    // ---------------------------------------------------------------- registry, determinism, traversal

    @Test
    void theSameRelationshipRestatedInAnotherPackageResolvesToTheSameIdAndIsTraversable() {
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        PipelineResult first = manufacture("first", "cow", EDITION, RelationshipEditionTest::validPartOf);
        PipelineResult second = manufacture("second", "cow", EDITION, fixture -> {
            validPartOf(fixture);
            relationship(fixture).put("candidateId", "rel-restated");
            relationship(fixture).put("basis", "INFERRED");
            object(fixture).put("fixedClock", "2026-09-05T00:00:00Z");
        });
        registry.register(first.packagePath());
        registry.register(second.packagePath());
        assertNotEquals(first.packagePath(), second.packagePath());

        Map<String,Object> index = registry.index();
        List<Object> relationships = array(index.get("relationships"));
        assertEquals(1, relationships.size(), "one relationship, two immutable occurrences");
        Map<String,Object> relationship = object(relationships.getFirst());
        assertEquals(2, array(relationship.get("occurrences")).size());
        assertEquals("MIXED", relationship.get("basis"));
        assertTrue(registry.verify().passed());

        String root = first.rootUaoId();
        Map<String,Object> neighbourhood = registry.relationshipNeighbourhood(root);
        assertEquals(1, array(neighbourhood.get("edges")).size());
        assertEquals(1, array(neighbourhood.get("neighbourUids")).size());
        assertEquals(Boolean.FALSE, neighbourhood.get("certifying"));
        Map<String,Object> graph = registry.graph();
        assertEquals(2, array(graph.get("nodes")).size());
        assertEquals(1, array(graph.get("edges")).size());
    }

    @Test
    void aRegistryWithoutTypedRelationshipsKeepsItsPreviousIndexShape() {
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry-plain"), SCHEMAS);
        PipelineResult plain = manufacture("plain", "cow", null, fixture -> object(fixture.get("candidates")).put("relationships", List.of()));
        registry.register(plain.packagePath());
        assertFalse(registry.index().containsKey("relationships"), "registries built before Experiment 002 must keep verifying without a rebuild");
        assertTrue(registry.verify().passed());
    }

    // ---------------------------------------------------------------- verifier

    @Test
    void anEditedRecordOrAStrippedEditionFailsVerification() {
        PipelineResult result = manufacture("tamper", "cow", EDITION, RelationshipEditionTest::validPartOf);
        Path file = result.packagePath().resolve("experimental-relationships.json");
        List<Object> records = array(FileOps.readJson(file));
        object(records.getFirst()).put("basis", "EXPLICIT-FORGED");
        FileOps.writeJson(file, records);
        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed());
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("Experimental relationship records do not reconstruct")), verification.errors().toString());

        PipelineResult stripped = manufacture("strip", "cow", EDITION, RelationshipEditionTest::validPartOf);
        try { Files.delete(stripped.packagePath().resolve("relationship-type-edition.json")); } catch (Exception ex) { fail(ex); }
        PackageVerifier.Result strippedVerification = new PackageVerifier(SCHEMAS).verify(stripped.packagePath());
        assertFalse(strippedVerification.passed());
        assertTrue(strippedVerification.errors().stream().anyMatch(e -> e.contains("without an embedded relationship type edition")), strippedVerification.errors().toString());
    }

    // ---------------------------------------------------------------- helpers

    private static void validPartOf(Map<String,Object> fixture) {
        Map<String,Object> rel = relationship(fixture);
        rel.put("typeVersion", PART_OF);
        rel.put("basis", "EXPLICIT");
        rel.put("participants", List.of(
                Json.object(Json.parse("{\"role\":\"part\",\"candidateIdentityRef\":\"cid-root\"}"), "p"),
                Json.object(Json.parse("{\"role\":\"whole\",\"candidateIdentityRef\":\"cid-bovine-context\"}"), "p")));
    }

    private static Map<String,Object> relationship(Map<String,Object> fixture) {
        return object(array(object(fixture.get("candidates")).get("relationships")).getFirst());
    }

    private PipelineResult manufacture(String suffix, String seed, Path edition, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(COW)));
        mutation.accept(fixture);
        Path fixturePath = temp.resolve("fixture-" + suffix + ".json");
        FileOps.writeJson(fixturePath, fixture);
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed(seed, "en", "experimental");
        FoundryPipeline pipeline = new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), "test-sha");
        if (edition != null) pipeline.relationshipEdition(edition);
        return pipeline.manufacture(request, new FixtureProvider(fixturePath, SCHEMAS), false);
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v) { return (Map<String,Object>) v; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object v) { return (List<Object>) v; }
}
