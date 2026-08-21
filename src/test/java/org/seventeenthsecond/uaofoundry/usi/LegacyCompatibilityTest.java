package org.seventeenthsecond.uaofoundry.usi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.console.OperatorConsole;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-0004 compatibility guarantees, and the reserved identifier mapping of ADR-0005.
 *
 * <p>The promise being tested is that adopting USI terminology costs a legacy operator nothing: a
 * package manufactured before this programme must still open, verify, register, search and be
 * reused, with no conversion step and no rewriting of immutable evidence.
 */
class LegacyCompatibilityTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path DEMO = Path.of("examples/demonstration");
    private static final String AT = "2026-08-21T00:00:00Z";

    @TempDir Path temp;

    // ---------------------------------------------------------------- legacy packages

    @Test
    void aLegacyPackageCarryingAnEmbeddedReuseReportStillVerifiesAndRegisters() {
        // Build a package in the pre-ADR-0006 shape: the reuse report attached inside, listed in
        // the manifest and covered by the checksums, exactly as attachAndVerify used to leave it.
        Path registry = temp.resolve("registry");
        Path legacy = legacyStylePackage(registry);

        assertTrue(Files.isRegularFile(legacy.resolve("reuse-report.json")),
                "the fixture must actually be in the legacy shape");
        assertTrue(new PackageVerifier(SCHEMAS).verify(legacy).passed(),
                "a legacy package must remain self-consistent and verifiable, unconverted");

        FoundryRegistry target = new FoundryRegistry(temp.resolve("legacy-registry"), SCHEMAS);
        FoundryRegistry.RegistrationResult admitted = target.register(legacy);
        assertEquals("REGISTERED", admitted.alreadyPresent() ? "PRESENT" : "REGISTERED");
        assertTrue(target.verify().passed());
    }

    @Test
    void aLegacyPackageIsSearchableAndInspectableThroughTheCurrentSurfaces() {
        Path registry = temp.resolve("registry");
        Path legacy = legacyStylePackage(registry);
        FoundryRegistry target = new FoundryRegistry(temp.resolve("legacy-registry"), SCHEMAS);
        target.register(legacy);

        assertFalse(target.search("electric motor").isEmpty(), "legacy identities must remain findable");

        Map<String,Object> record = target.identityRecord(IdentityReference.externalIdentifier("wikidata", "Q53068"));
        assertEquals("SAME", object(record.get("resolution")).get("decision"),
                "a legacy identity must remain addressable by its durable external identifier");

        // And the significance surface still works over legacy material.
        Map<String,Object> inputs = target.significanceInputs(
                IdentityReference.uid(String.valueOf(object(record.get("identity")).get("uid"))));
        assertNotNull(object(inputs.get("A_x")).get("identity"));
    }

    @Test
    void newManufactureCoexistsWithLegacyRecordsInOneRegistry() {
        Path registry = temp.resolve("registry");
        Path legacy = legacyStylePackage(registry);
        Path shared = temp.resolve("shared-registry");
        FoundryRegistry target = new FoundryRegistry(shared, SCHEMAS);
        target.register(legacy);
        String legacyDigest = FileOps.treeHash(shared.resolve("packages"));

        // A current-shape manufacture into the same registry, reusing the legacy identity.
        Result result = run("manufacture", "EV traction motor", "--registry", shared.toString(),
                "--fixture", DEMO.resolve("ev-traction-motor.json").toString(),
                "--register", "--json", "--clock", AT,
                "--work-dir", temp.resolve("w2").toString(), "--dist-dir", temp.resolve("d2").toString());
        assertEquals(0, result.exit(), result.err());
        Map<String,Object> report = object(Json.parse(result.out()));
        assertEquals("REGISTERED", report.get("registryAdmission"));
        assertEquals(3, intOf(object(report.get("counts")), "existingIdentitiesReused"),
                "current manufacture must reuse identities that a legacy package registered");

        assertTrue(target.verify().passed(), "the mixed registry must still verify");
        assertNotEquals(legacyDigest, FileOps.treeHash(shared.resolve("packages")),
                "the new package was genuinely added");
        assertTrue(new PackageVerifier(SCHEMAS).verify(
                        shared.resolve("packages").resolve(String.valueOf(
                                object(FileOps.readJson(legacy.resolve("manifest.json"))).get("packageId")))).passed(),
                "and the legacy package inside it is untouched and still verifies");
    }

    // ---------------------------------------------------------------- identifier strategy

    @Test
    void theCanonicalIdentifierRemainsTheAsaPinnedShape() {
        Path registry = temp.resolve("registry");
        Result result = run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(),
                "--register", "--json", "--clock", AT,
                "--work-dir", temp.resolve("w").toString(), "--dist-dir", temp.resolve("d").toString());
        Path pkg = Path.of(String.valueOf(object(Json.parse(result.out())).get("packagePath")));

        for (Object raw : array(FileOps.readJson(pkg.resolve("canonical-identities.json")))) {
            String uid = String.valueOf(object(raw).get("uid"));
            assertTrue(UsiIdentifiers.isLegacy(uid),
                    "ADR-0005: the canonical uid stays uao-<12 hex>, which ASA CSS pins; got " + uid);
            assertFalse(UsiIdentifiers.isUsi(uid), "no usi-* identifier may be minted canonically");
        }
    }

    @Test
    void theReservedUsiMappingIsTotalAndReversibleButUnused() {
        String legacy = "uao-e7582726a3c8";
        assertEquals("usi-e7582726a3c8", UsiIdentifiers.toUsi(legacy));
        assertEquals(legacy, UsiIdentifiers.toLegacy(UsiIdentifiers.toUsi(legacy)),
                "the mapping must round-trip, so a future migration is reversible");
        assertEquals(UsiIdentifiers.LEGACY_SCHEME, UsiIdentifiers.schemeOf(legacy));
        assertEquals(UsiIdentifiers.USI_SCHEME, UsiIdentifiers.schemeOf("usi-e7582726a3c8"));

        assertThrows(IllegalArgumentException.class, () -> UsiIdentifiers.toUsi("usi-e7582726a3c8"));
        assertThrows(IllegalArgumentException.class, () -> UsiIdentifiers.toLegacy("uao-e7582726a3c8"));
        assertThrows(IllegalArgumentException.class, () -> UsiIdentifiers.schemeOf("pkg-0000000000000000"));
    }

    @Test
    void noProductionCodeMintsAUsiIdentifierAndTheCoreNeverSeesTheMapping() throws Exception {
        // ADR-0005 §4. Two distinct invariants, because the directions differ in risk:
        //
        //   toUsi()   MINTS a usi- string. Calling it anywhere in production would start leaking
        //             usi- identifiers into artefacts without the governed migration.
        //   toLegacy()/schemeOf()  translate INBOUND and label a scheme. Safe, and used by the
        //             application facade so a future-form reference is not silently unresolvable.
        //
        // Separately, the audited core must not reference the class at all: the migration seam
        // belongs at the application boundary, not inside manufacture, registry or verification.
        List<String> minters = new ArrayList<>();
        List<String> coreReferences = new ArrayList<>();
        Path main = Path.of("src/main/java");
        try (var stream = Files.walk(main)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = main.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith("usi/UsiIdentifiers.java")) continue;
                String text = Files.readString(file);
                if (text.contains("UsiIdentifiers.toUsi(")) minters.add(relative);
                if (text.contains("UsiIdentifiers.") && relative.startsWith("org/seventeenthsecond/uaofoundry/")) {
                    coreReferences.add(relative);
                }
            }
        }
        assertEquals(List.of(), minters, "no production code may mint a usi- identifier");
        assertEquals(List.of(), coreReferences,
                "the audited core must not reference the migration seam; it lives at the application boundary");

        // And nothing that reaches an artefact is in the usi- form.
        Path registry = temp.resolve("registry");
        Result result = run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(),
                "--register", "--json", "--clock", AT,
                "--work-dir", temp.resolve("wm").toString(), "--dist-dir", temp.resolve("dm").toString());
        Path pkg = Path.of(String.valueOf(object(Json.parse(result.out())).get("packagePath")));
        try (var stream = Files.walk(pkg)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                assertFalse(Files.readString(file).contains("usi-"),
                        "no manufactured artefact may contain a usi- identifier: " + pkg.relativize(file));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Reproduces the pre-ADR-0006 package shape, with the reuse report attached inside. */
    private Path legacyStylePackage(Path registry) {
        Result seeded = run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(),
                "--register", "--json", "--clock", AT,
                "--work-dir", temp.resolve("w1").toString(), "--dist-dir", temp.resolve("d1").toString());
        assertEquals(0, seeded.exit(), seeded.err());
        Map<String,Object> report = object(Json.parse(seeded.out()));
        Path source = Path.of(String.valueOf(report.get("packagePath")));

        Path legacy = temp.resolve("legacy-package");
        FileOps.copyTree(source, legacy);
        Map<String,Object> reuse = object(object(FileOps.readJson(
                registry.resolveSibling("runs").resolve(report.get("runId") + ".json"))).get("reuseReport"));
        FileOps.writeJson(legacy.resolve("reuse-report.json"), reuse);

        Map<String,Object> manifest = object(FileOps.readJson(legacy.resolve("manifest.json")));
        Set<String> files = new LinkedHashSet<>();
        for (Object raw : array(manifest.get("files"))) files.add(String.valueOf(raw));
        files.add("reuse-report.json");
        manifest.put("files", files.stream().sorted().toList());
        FileOps.writeJson(legacy.resolve("manifest.json"), manifest);
        rewriteChecksums(legacy);
        return legacy;
    }

    /** Mirrors the checksum discipline the old attachAndVerify applied. */
    private static void rewriteChecksums(Path packageDir) {
        List<String> names = new ArrayList<>();
        try (var stream = Files.walk(packageDir)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relative = packageDir.relativize(file).toString().replace('\\', '/');
                if (!"checksums.sha256".equals(relative)) names.add(relative);
            }
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        StringBuilder out = new StringBuilder();
        for (String relative : names) {
            try {
                out.append(Hashes.sha256(Files.readAllBytes(packageDir.resolve(relative))))
                        .append("  ").append(relative).append('\n');
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        }
        FileOps.writeText(packageDir.resolve("checksums.sha256"), out.toString());
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = new OperatorConsole(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exit, String out, String err) {}

    private static int intOf(Map<String,Object> counts, String key) {
        return ((java.math.BigDecimal) counts.get(key)).intValue();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
