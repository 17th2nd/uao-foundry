package org.seventeenthsecond.uaofoundry.runs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.console.OperatorConsole;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for finding P9-1 and the run-evidence boundary (ADR-0006).
 *
 * <p>P9-1: {@code reuse-report.json} was written inside the content-addressed package but excluded
 * from the content digest, while embedding a registry hash that moves as the registry grows. Two
 * manufactures of semantically identical material against a moved-on registry therefore produced
 * the same {@code packageId} with different bytes, and the collision guards correctly refused the
 * second. Measured in the Alpha: 69 of 114 cumulative manufactures refused.
 *
 * <p>The fix removes the volatile input rather than loosening the guard, so these tests check both
 * halves: accumulation now works, <em>and</em> the guard still catches a genuine collision.
 */
class CumulativeManufactureTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path DEMO = Path.of("examples/demonstration");
    private static final String AT = "2026-08-21T00:00:00Z";

    @TempDir Path temp;

    @Test
    void tenRepeatedManufacturesOfIdenticalMaterialAllSucceed() {
        Path registry = temp.resolve("registry");
        Set<String> packageIds = new LinkedHashSet<>();

        for (int i = 1; i <= 10; i++) {
            Result result = manufacture("electric motor", "electric-motor.json", registry, "run" + i);
            assertEquals(0, result.exit(), "manufacture " + i + " failed: " + result.err());
            Map<String,Object> report = object(Json.parse(result.out()));
            assertEquals("REGISTERED", report.get("registryAdmission"),
                    "manufacture " + i + " was refused: " + report.get("registryAdmission"));
            packageIds.add(String.valueOf(report.get("packageId")));
        }

        // Before the fix this failed at the third manufacture with a package-id collision.
        FoundryRegistry verified = new FoundryRegistry(registry, SCHEMAS);
        assertTrue(verified.verify().passed(), verified.verify().errors().toString());
        assertEquals(3, verified.verify().identityCount(),
                "ten manufactures of one identity expression must still yield three identities");

        // The first run is "new", every later run is "reused"; only those two package shapes exist.
        assertEquals(2, packageIds.size(),
                "identical material must stop producing new package ids once the identity is registered");
        assertEquals(2, verified.verify().packageCount());
    }

    @Test
    void repeatedManufactureProducesByteIdenticalPackages() {
        Path registry = temp.resolve("registry");
        manufacture("electric motor", "electric-motor.json", registry, "seed");

        Result second = manufacture("electric motor", "electric-motor.json", registry, "a");
        Result third = manufacture("electric motor", "electric-motor.json", registry, "b");
        Path secondPackage = Path.of(String.valueOf(object(Json.parse(second.out())).get("packagePath")));
        Path thirdPackage = Path.of(String.valueOf(object(Json.parse(third.out())).get("packagePath")));

        assertEquals(FileOps.treeHash(secondPackage), FileOps.treeHash(thirdPackage),
                "a package must not vary with the registry state that surrounded its manufacture");
        assertFalse(Files.isRegularFile(secondPackage.resolve("reuse-report.json")));
        assertTrue(new PackageVerifier(SCHEMAS).verify(secondPackage).passed());
    }

    @Test
    void theCollisionGuardStillCatchesAGenuineCollision() {
        // The fix must not have been a loosening. Forge a package whose bytes differ from an
        // already-registered package sharing its id, and confirm admission is still refused.
        Path registry = temp.resolve("registry");
        Result first = manufacture("electric motor", "electric-motor.json", registry, "guard");
        Path original = Path.of(String.valueOf(object(Json.parse(first.out())).get("packagePath")));

        Path forged = temp.resolve("forged");
        FileOps.copyTree(original, forged);
        Path corpus = forged.resolve("source-corpus");
        try (var stream = Files.list(corpus)) {
            Path snapshot = stream.filter(Files::isRegularFile).findFirst().orElseThrow();
            Files.writeString(snapshot, "\nFORGED\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }

        FoundryRegistry verified = new FoundryRegistry(registry, SCHEMAS);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> verified.register(forged));
        assertTrue(failure.getMessage().contains("verification failed")
                        || failure.getMessage().contains("collision"),
                "a genuine content difference must still be refused: " + failure.getMessage());
        assertTrue(verified.verify().passed(), "and the registry must be left verifiable");
    }

    // ---------------------------------------------------------------- run evidence

    @Test
    void everyManufactureLeavesAnInspectableRunRecordBesideTheRegistry() {
        Path registry = temp.resolve("registry");
        Result result = manufacture("electric motor", "electric-motor.json", registry, "evidence");
        Map<String,Object> report = object(Json.parse(result.out()));

        RunStore store = RunStore.besideRegistry(registry);
        assertEquals(temp.resolve("runs").toAbsolutePath().normalize(), store.root(),
                "the run store must sit beside the registry, not inside it");

        RunRecord run = store.get(String.valueOf(report.get("runId")));
        assertEquals("electric motor", run.identitySeed());
        assertEquals(RunRecord.COMPLETED, run.status());
        assertEquals(report.get("packageId"), run.packageId());
        assertEquals(3, run.usiIds().size());
        assertNotNull(run.reuseReport(), "the reuse report must survive the move out of the package");
        assertNotEquals(run.registryBeforeHash(), run.registryAfterHash(),
                "registering an identity must move the registry state the run recorded");
    }

    @Test
    void runRecordsNeverInfluenceTheRegistryIndex() {
        Path registry = temp.resolve("registry");
        manufacture("electric motor", "electric-motor.json", registry, "isolation");

        FoundryRegistry verified = new FoundryRegistry(registry, SCHEMAS);
        String indexBefore = Json.canonical(verified.index());

        // Add more run evidence without manufacturing anything.
        RunStore store = RunStore.besideRegistry(registry);
        store.record(RunRecord.create("unrelated", null, "fixture", RunRecord.COMPLETED,
                null, List.of(), null, null, null, AT, AT, null, "synthetic"));

        assertEquals(indexBefore, Json.canonical(verified.index()),
                "the registry index is derived from packages and identity operations only");
        assertTrue(verified.verify().passed());
    }

    @Test
    void runRecordsAreContentAddressedAndAppendPreserving() {
        RunStore store = new RunStore(temp.resolve("runs"));
        RunRecord run = RunRecord.create("seed", "ctx", "fixture", RunRecord.COMPLETED,
                "pkg-0000000000000000", List.of("uao-000000000000"), "a".repeat(64), "b".repeat(64),
                null, AT, AT, null, null);

        assertTrue(run.runId().matches("run-[a-f0-9]{16}"));
        store.record(run);
        String after = FileOps.treeHash(store.root());
        store.record(run);
        assertEquals(after, FileOps.treeHash(store.root()), "re-recording an identical run changes nothing");

        // A correction appends, referencing what it corrects, rather than editing history.
        RunRecord correction = RunRecord.create("seed", "ctx", "fixture", RunRecord.COMPLETED,
                "pkg-0000000000000000", List.of("uao-000000000000"), "a".repeat(64), "b".repeat(64),
                null, AT, AT, run.runId(), "corrected");
        store.record(correction);
        assertEquals(2, store.list().size());
        assertNotEquals(run.runId(), correction.runId());
        assertEquals(run.runId(), correction.supersedesRunId());
        assertEquals(RunRecord.COMPLETED, store.get(run.runId()).status(), "the original is untouched");
    }

    @Test
    void anEditedRunRecordBreaksItsContentAddress() {
        RunStore store = new RunStore(temp.resolve("runs"));
        RunRecord run = store.record(RunRecord.create("seed", null, "fixture", RunRecord.COMPLETED,
                null, List.of(), null, null, null, AT, AT, null, null));

        Path file = store.root().resolve(run.runId() + ".json");
        Map<String,Object> tampered = object(FileOps.readJson(file));
        tampered.put("identitySeed", "something else");
        FileOps.writeJson(file, tampered);

        assertThrows(IllegalArgumentException.class, store::list,
                "run history must not be rewritable after the fact");
    }

    // ---------------------------------------------------------------- helpers

    private Result manufacture(String seed, String fixture, Path registry, String tag) {
        return run("manufacture", seed, "--registry", registry.toString(),
                "--fixture", DEMO.resolve(fixture).toString(), "--register", "--json",
                "--work-dir", temp.resolve("work-" + tag).toString(),
                "--dist-dir", temp.resolve("dist-" + tag).toString(),
                "--clock", AT);
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = new OperatorConsole(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exit, String out, String err) {}

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
}
