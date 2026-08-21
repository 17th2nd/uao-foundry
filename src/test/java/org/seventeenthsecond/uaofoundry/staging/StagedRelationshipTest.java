package org.seventeenthsecond.uaofoundry.staging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.console.OperatorConsole;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the non-canonical staged relationship store (directive §18).
 *
 * <p>The store exists to make persistent relationship reconstruction measurable while ASA#29
 * blocks accumulation. Every test here is therefore about the same worry: that candidate
 * relationship <em>memory</em> could be mistaken for a certified relationship <em>graph</em>.
 */
class StagedRelationshipTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path RELATIONSHIP_FIXTURE =
            Path.of("src/test/resources/fixtures/relationship-bearing-cow.json");
    private static final String AT = "2026-08-21T00:00:00Z";

    @TempDir Path temp;

    @Test
    void aRetainedCandidateIsStagedWithItsIdentityBindingsIntact() {
        Path packageDir = manufacture("stage");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));

        List<Map<String,Object>> staged = store.stageFrom(packageDir, AT);
        assertEquals(1, staged.size());

        Map<String,Object> record = staged.getFirst();
        assertTrue(String.valueOf(record.get("stagedId")).matches("stg-[a-f0-9]{16}"));
        assertEquals("asa.core/contains@1", record.get("typeVersion"));
        assertEquals("PARTIALLY_BOUND", record.get("identityBindingStatus"));

        // The persistent uid binding is what makes the memory reusable at all; before Phase 5 a
        // candidate pointed only at bundle-local handles and was unfindable outside its package.
        List<Object> participants = array(record.get("participants"));
        assertTrue(participants.stream().map(StagedRelationshipTest::object)
                        .anyMatch(p -> String.valueOf(p.get("uaoId")).matches("uao-[a-f0-9]{12}")),
                "a staged candidate must carry the persistent identity it was bound to");

        new SchemaValidator().validate(record, SCHEMAS.resolve("staged-relationship.schema.json"))
                .requireValid("Staged relationship");
    }

    @Test
    void everyStagedRecordIsLabelledNonCanonicalByConstruction() {
        Path packageDir = manufacture("labels");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        Map<String,Object> record = store.stageFrom(packageDir, AT).getFirst();

        assertEquals(StagedRelationshipStore.STATUS, record.get("status"));
        assertEquals(StagedRelationshipStore.AUTHORITY_STATUS, record.get("authorityStatus"));
        assertEquals(Boolean.FALSE, record.get("certifying"));

        // The schema pins all three as constants, so a record cannot be written without them.
        Map<String,Object> stripped = object(Json.parse(Json.canonical(record)));
        stripped.put("certifying", Boolean.TRUE);
        assertFalse(new SchemaValidator().validate(stripped, SCHEMAS.resolve("staged-relationship.schema.json")).valid(),
                "a staged record claiming to certify must not validate");
    }

    @Test
    void stagingChangesNoPublicationDecisionAndNoRegistryAdmission() {
        Path packageDir = manufacture("nochange");
        String publicationBefore = Json.canonical(FileOps.readJson(packageDir.resolve("publication-decision.json")));
        String packageBefore = FileOps.treeHash(packageDir);

        new StagedRelationshipStore(temp.resolve("staged")).stageFrom(packageDir, AT);

        assertEquals(packageBefore, FileOps.treeHash(packageDir),
                "staging must not touch the immutable package it read");
        assertEquals(publicationBefore,
                Json.canonical(FileOps.readJson(packageDir.resolve("publication-decision.json"))));
        assertTrue(publicationBefore.contains("EVIDENCE_INCOMPLETE"),
                "the package remains evidence-incomplete, exactly as before");
        assertTrue(array(FileOps.readJson(packageDir.resolve("canonical-relationships.json"))).isEmpty(),
                "canonical URO count stays zero");

        // And it remains inadmissible: staging buys no publication eligibility whatsoever.
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry-admit"), SCHEMAS);
        assertThrows(IllegalArgumentException.class, () -> registry.register(packageDir));
    }

    @Test
    void theStagingStoreNeverEntersTheRegistryIndex() {
        // Manufacture something admissible so the registry has content of its own.
        Path clean = manufactureFrom("clean", Path.of("examples/demonstration/electric-motor.json"),
                temp.resolve("registry"), true);
        FoundryRegistry registry = new FoundryRegistry(temp.resolve("registry"), SCHEMAS);
        String indexBefore = Json.canonical(registry.index());

        // Stage a relationship candidate beside it.
        StagedRelationshipStore store = StagedRelationshipStore.besideRegistry(temp.resolve("registry"));
        store.stageFrom(manufacture("beside"), AT);
        assertFalse(store.list().isEmpty());

        assertEquals(temp.resolve("staged-relationships").toAbsolutePath().normalize(), store.root(),
                "the staging store must sit beside the registry, not inside it");
        assertEquals(indexBefore, Json.canonical(registry.index()),
                "the registry index is derived from packages and identity operations only");
        assertTrue(registry.verify().passed());
        assertNotNull(clean);
    }

    @Test
    void candidatesAccumulateAcrossManufacturesAndAreReachableFromAnIdentity() {
        // The point of the store: what ASA#29 currently prevents the registry from accumulating.
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        store.stageFrom(manufacture("acc-1"), AT);
        store.stageFrom(manufacture("acc-2"), "2026-08-22T00:00:00Z");

        List<Map<String,Object>> all = store.list();
        assertEquals(2, all.size(), "each observation contributes its own record");

        String uid = String.valueOf(object(array(all.getFirst().get("participants")).stream()
                .map(StagedRelationshipTest::object)
                .filter(p -> "RESOLVED".equals(p.get("binding"))).findFirst().orElseThrow()).get("uaoId"));

        Map<String,Object> neighbourhood = store.neighbourhood(uid);
        assertEquals(2, array(neighbourhood.get("edges")).size(),
                "both observations of the relationship are reachable from the identity");
        assertEquals(Boolean.FALSE, neighbourhood.get("certifying"));
        assertEquals(StagedRelationshipStore.AUTHORITY_STATUS, neighbourhood.get("authorityStatus"));
        assertTrue(String.valueOf(neighbourhood.get("caveat")).contains("asserted, not governed"));
    }

    @Test
    void stagingIsIdempotentAndAppendPreserving() {
        Path packageDir = manufacture("idem");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        store.stageFrom(packageDir, AT);
        String after = FileOps.treeHash(store.root());
        store.stageFrom(packageDir, AT);
        assertEquals(after, FileOps.treeHash(store.root()), "re-staging identical content changes nothing");
        assertEquals(1, store.list().size());
    }

    @Test
    void aRecordStrippedOfItsNonCanonicalLabellingFailsClosed() {
        Path packageDir = manufacture("tamper");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        Map<String,Object> record = store.stageFrom(packageDir, AT).getFirst();

        Path file = store.root().resolve(record.get("stagedId") + ".json");
        Map<String,Object> tampered = object(FileOps.readJson(file));
        tampered.put("certifying", Boolean.TRUE);
        FileOps.writeJson(file, tampered);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, store::list);
        assertTrue(failure.getMessage().contains("non-canonical labelling"), failure.getMessage());
    }

    @Test
    void aParticipantTamperWithUnchangedIdAndFilenameFailsClosed() {
        // The vector the label/filename checks alone did not catch: mutate a meaning-bearing field
        // while leaving stagedId, filename and the non-canonical labels intact.
        Path packageDir = manufacture("ptamper");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        Map<String,Object> record = store.stageFrom(packageDir, AT).getFirst();
        Path file = store.root().resolve(record.get("stagedId") + ".json");

        Map<String,Object> tampered = object(FileOps.readJson(file));
        List<Object> participants = array(tampered.get("participants"));
        object(participants.getFirst()).put("uaoId", "uao-000000000000");
        FileOps.writeJson(file, tampered);   // stagedId, filename, labels all unchanged

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, store::list);
        assertTrue(failure.getMessage().contains("content address does not match"), failure.getMessage());
    }

    @Test
    void aSourceRefTamperWithUnchangedIdAndFilenameFailsClosed() {
        Path packageDir = manufacture("stamper");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        Map<String,Object> record = store.stageFrom(packageDir, AT).getFirst();
        Path file = store.root().resolve(record.get("stagedId") + ".json");

        Map<String,Object> tampered = object(FileOps.readJson(file));
        tampered.put("sourceRefs", List.of("src-forged"));
        FileOps.writeJson(file, tampered);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, store::list);
        assertTrue(failure.getMessage().contains("content address does not match"), failure.getMessage());
    }

    @Test
    void aPackageIdTamperWithUnchangedIdAndFilenameFailsClosed() {
        Path packageDir = manufacture("pkgtamper");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        Map<String,Object> record = store.stageFrom(packageDir, AT).getFirst();
        Path file = store.root().resolve(record.get("stagedId") + ".json");

        Map<String,Object> tampered = object(FileOps.readJson(file));
        tampered.put("packageId", "pkg-0000000000000000");
        FileOps.writeJson(file, tampered);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, store::list);
        assertTrue(failure.getMessage().contains("content address does not match"), failure.getMessage());
    }

    @Test
    void anUntamperedRecordStillReadsSuccessfully() {
        Path packageDir = manufacture("clean-read");
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        store.stageFrom(packageDir, AT);
        assertEquals(1, store.list().size(), "a valid staged record must still read back");
    }

    @Test
    void stagedMemorySurvivesRestartDeterministically() {
        // Directive §9: prove persisted staged memory survives a process restart and reconstructs
        // the same edge set, with no live provider. A fresh store object over the same directory
        // stands in for a restarted process: it carries no in-memory edge collection.
        Path packageDir = manufacture("restart");
        Path stagedRoot = temp.resolve("staged");
        new StagedRelationshipStore(stagedRoot).stageFrom(packageDir, AT);

        StagedRelationshipStore afterRestart = new StagedRelationshipStore(stagedRoot);
        List<Map<String,Object>> reloaded = afterRestart.list();
        assertEquals(1, reloaded.size(), "the staged record must survive the restart");

        String uid = String.valueOf(object(array(reloaded.getFirst().get("participants")).stream()
                .map(StagedRelationshipTest::object)
                .filter(p -> "RESOLVED".equals(p.get("binding"))).findFirst().orElseThrow()).get("uaoId"));
        Map<String,Object> neighbourhood = afterRestart.neighbourhood(uid);
        assertEquals(1, array(neighbourhood.get("edges")).size(),
                "the identity's staged edge must be reachable after restart");
        assertEquals(Boolean.FALSE, neighbourhood.get("certifying"));
    }

    @Test
    void aPackageWithoutRelationshipCandidatesStagesNothing() {
        Path clean = manufactureFrom("empty", Path.of("examples/demonstration/electric-motor.json"),
                temp.resolve("registry-empty"), true);
        StagedRelationshipStore store = new StagedRelationshipStore(temp.resolve("staged"));
        assertEquals(List.of(), store.stageFrom(clean, AT),
                "staging must not invent a relationship where the provider asserted none");
    }

    // ---------------------------------------------------------------- helpers

    private Path manufacture(String tag) {
        return manufactureFrom(tag, RELATIONSHIP_FIXTURE, null, false);
    }

    private Path manufactureFrom(String tag, Path fixture, Path registry, boolean register) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        java.util.List<String> args = new java.util.ArrayList<>(List.of(
                "manufacture", fixture.equals(RELATIONSHIP_FIXTURE) ? "cow" : "electric motor",
                "--fixture", fixture.toString(), "--json", "--clock", AT,
                "--work-dir", temp.resolve("work-" + tag).toString(),
                "--dist-dir", temp.resolve("dist-" + tag).toString()));
        if (registry != null) { args.add("--registry"); args.add(registry.toString()); }
        if (register) args.add("--register");

        new OperatorConsole(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args.toArray(String[]::new));
        String stdout = out.toString(StandardCharsets.UTF_8);
        assertFalse(stdout.isBlank(), "manufacture produced no output: " + err.toString(StandardCharsets.UTF_8));
        Path packageDir = Path.of(String.valueOf(object(Json.parse(stdout)).get("packagePath")));
        assertTrue(Files.isDirectory(packageDir));
        return packageDir;
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
