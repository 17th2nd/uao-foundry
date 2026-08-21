package org.seventeenthsecond.uaofoundry.identity;

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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 regression tests for relationship binding under the ASA#29 authority gap.
 *
 * <p>The claim being tested is narrow and must stay narrow: binding a participant to a persistent
 * identity solves the <em>identity</em> half of a relationship and moves the <em>type-role</em>
 * half not one step. Every test that improves binding is paired with an assertion that the
 * fail-closed publication boundary is exactly where it was.
 */
class RelationshipBindingTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path RELATIONSHIP_COW = Path.of("src/test/resources/fixtures/relationship-bearing-cow.json");

    @TempDir Path temp;
    private Path registryRoot;

    @Test
    void bindingParticipantsDoesNotMoveTheAsa29PublicationBoundary() {
        PipelineResult result = manufacture("boundary", fixture -> {});

        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        assertTrue(array(FileOps.readJson(result.packagePath().resolve("canonical-relationships.json"))).isEmpty(),
                "canonical URO count must remain zero while ASA#29 is open");
        Map<String,Object> publication = object(FileOps.readJson(result.packagePath().resolve("publication-decision.json")));
        assertEquals(Boolean.FALSE, publication.get("eligible"));

        for (Object raw : unresolved(result)) {
            assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", object(raw).get("code"));
        }
        // Every UAO still holds an empty relationship_references array.
        for (Object raw : array(FileOps.readJson(result.packagePath().resolve("canonical-identities.json")))) {
            assertTrue(array(object(raw).get("relationship_references")).isEmpty());
        }
        assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
    }

    @Test
    void aResolvableParticipantIsBoundToItsPersistentIdentity() {
        PipelineResult result = manufacture("bound", fixture -> {});
        Map<String,Object> relationship = object(unresolved(result).getFirst());

        Map<String,Object> container = participant(relationship, "container");
        assertEquals("RESOLVED", container.get("binding"));
        assertEquals(rootUid(result), container.get("uaoId"),
                "a relationship must point at a persistent identity, not a bundle-local handle");
        assertEquals("asa.core/contains@1", relationship.get("typeVersion"),
                "the ASA#29 extension seam is recorded verbatim and validated against nothing");
    }

    @Test
    void anUnresolvableParticipantIsNeverGivenAnIdentityToLookComplete() {
        // The fixture names cid-species, which is not among its candidate identities. Before
        // binding, that dangling reference was invisible: the unresolved finding recorded only a
        // candidate id and a reason code.
        PipelineResult result = manufacture("dangling", fixture -> {});
        Map<String,Object> relationship = object(unresolved(result).getFirst());

        Map<String,Object> member = participant(relationship, "member");
        assertEquals("UNRESOLVED", member.get("binding"));
        assertNull(member.get("uaoId"), "identity certainty must never be fabricated to complete a relation");
        assertEquals("PARTIALLY_BOUND", relationship.get("identityBindingStatus"),
                "a partly bound relationship must say so rather than round up");
    }

    @Test
    void aFullyBoundRelationshipIsStillNotPublishable() {
        // Point both participants at identities the fixture actually manufactures.
        PipelineResult result = manufacture("fully-bound", fixture ->
                participantRef(fixture, "member").put("candidateIdentityRef", "cid-bovine-context"));

        Map<String,Object> relationship = object(unresolved(result).getFirst());
        assertEquals("ALL_PARTICIPANTS_BOUND", relationship.get("identityBindingStatus"));
        for (Object raw : array(relationship.get("participants"))) {
            assertEquals("RESOLVED", object(raw).get("binding"));
        }

        // Solving identity entirely leaves the type-role gap exactly where it was.
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
        assertTrue(array(FileOps.readJson(result.packagePath().resolve("canonical-relationships.json"))).isEmpty(),
                "ALL_PARTICIPANTS_BOUND must not be mistaken for publishable");
        assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", relationship.get("code"));
    }

    @Test
    void aBoundRelationshipIsTraceableFromTheIdentityItMentions() {
        PipelineResult result = manufacture("traceable", fixture ->
                participantRef(fixture, "member").put("candidateIdentityRef", "cid-bovine-context"));

        // EVIDENCE_INCOMPLETE packages are not registry-admissible, so trace via a sibling package
        // that shares the identity but carries no relationship candidate.
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        assertThrows(IllegalArgumentException.class, () -> registry.register(result.packagePath()),
                "an evidence-incomplete package must remain inadmissible");

        // The binding is nonetheless durable inside the package and points at a persistent uid,
        // which is what makes cross-package tracing possible once a package is admissible.
        Map<String,Object> relationship = object(unresolved(result).getFirst());
        List<String> boundUids = new ArrayList<>();
        for (Object raw : array(relationship.get("participants"))) {
            Object uid = object(raw).get("uaoId");
            if (uid != null) boundUids.add(uid.toString());
        }
        assertEquals(2, boundUids.size());
        assertTrue(boundUids.contains(rootUid(result)));
        assertTrue(boundUids.stream().allMatch(v -> v.matches("uao-[a-f0-9]{12}")));
    }

    @Test
    void aForgedParticipantBindingFailsVerification() {
        PipelineResult result = manufacture("forge", fixture -> {});
        Path path = result.packagePath().resolve("unresolved-items.json");
        List<Object> items = array(FileOps.readJson(path));
        Map<String,Object> member = participant(object(items.getFirst()), "member");
        member.put("binding", "RESOLVED");
        member.put("uaoId", "uao-000000000000");
        FileOps.writeJson(path, items);

        PackageVerifier.Result verification = new PackageVerifier(SCHEMAS).verify(result.packagePath());
        assertFalse(verification.passed(), "a package must not be able to assert a participant binding");
        assertTrue(verification.errors().stream().anyMatch(e -> e.contains("Unresolved relationship projection")),
                verification.errors().toString());
    }

    @Test
    void aRelationshipCandidateStillForcesEvidenceIncompleteEvenWhenEverythingElsePasses() {
        PipelineResult withRelationship = manufacture("with-rel", fixture -> {});
        assertEquals("EVIDENCE_INCOMPLETE", withRelationship.publicationStatus());

        // The identical fixture without the relationship candidate publishes normally, isolating
        // the relationship candidate as the sole cause.
        PipelineResult without = manufacture("without-rel", fixture ->
                object(fixture.get("candidates")).put("relationships", List.of()));
        assertEquals("EXPERIMENTAL", without.publicationStatus());
    }

    // ---------------------------------------------------------------- helpers

    private PipelineResult manufacture(String suffix, Consumer<Map<String,Object>> mutation) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(RELATIONSHIP_COW)));
        mutation.accept(fixture);
        Path fixturePath = temp.resolve("fixture-" + suffix + ".json");
        FileOps.writeJson(fixturePath, fixture);

        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        ManufacturingRequest request = loader.fromSeed("cow", "en", "experimental");
        registryRoot = temp.resolve("registry");
        return new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), "test-sha")
                .manufacture(request, new FixtureProvider(fixturePath, SCHEMAS), false);
    }

    private static List<Object> unresolved(PipelineResult result) {
        return array(FileOps.readJson(result.packagePath().resolve("unresolved-items.json")));
    }

    private static Map<String,Object> participant(Map<String,Object> relationship, String role) {
        return array(relationship.get("participants")).stream().map(RelationshipBindingTest::object)
                .filter(v -> role.equals(v.get("role"))).findFirst().orElseThrow();
    }

    private static Map<String,Object> participantRef(Map<String,Object> fixture, String role) {
        Map<String,Object> relationship = object(array(object(fixture.get("candidates")).get("relationships")).getFirst());
        return array(relationship.get("participants")).stream().map(RelationshipBindingTest::object)
                .filter(v -> role.equals(v.get("role"))).findFirst().orElseThrow();
    }

    private static String rootUid(PipelineResult result) {
        return object(FileOps.readJson(result.packagePath().resolve("manifest.json"))).get("rootUaoId").toString();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
