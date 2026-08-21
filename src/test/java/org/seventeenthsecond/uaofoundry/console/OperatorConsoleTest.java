package org.seventeenthsecond.uaofoundry.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.registry.SemanticVariants;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8 tests for the operator console and the end-to-end acceptance flow.
 *
 * <p>These run the same demonstration the programme's acceptance criteria describe — manufacture,
 * register, rediscover, reuse, manufacture only what is new — against the real pipeline and a
 * disposable registry, and assert the numbers an operator would read.
 */
class OperatorConsoleTest {
    private static final Path DEMO = Path.of("examples/demonstration");

    @TempDir Path temp;

    // ---------------------------------------------------------------- acceptance flow

    @Test
    void manufactureRegisterRediscoverAndReuse() {
        Path registry = temp.resolve("registry");

        Result first = run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(),
                "--register", "--json");
        assertEquals(0, first.exit(), first.err());
        Map<String,Object> firstCounts = counts(first);
        assertEquals(0, intOf(firstCounts, "existingIdentitiesReused"), "an empty registry can reuse nothing");
        assertEquals(3, intOf(firstCounts, "newIdentitiesManufactured"));
        assertEquals("PASS", object(Json.parse(first.out())).get("verification"));
        assertEquals("REGISTERED", object(Json.parse(first.out())).get("registryAdmission"));

        // Rediscovery: the identity is findable by the durable external identifier it was
        // manufactured with, not merely by the words used the first time.
        Result found = run("identity", "wikidata:Q53068", "--registry", registry.toString(), "--json");
        assertEquals(0, found.exit(), found.err());
        Map<String,Object> resolution = object(object(Json.parse(found.out())).get("resolution"));
        assertEquals("SAME", resolution.get("decision"));
        assertEquals(List.of("EXTERNAL_IDENTIFIER_CONTINUITY"), resolution.get("reasonCodes"));

        // A later, related request reuses what is justified and manufactures only what is new.
        Result second = run("manufacture", "EV traction motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("ev-traction-motor.json").toString(),
                "--register", "--json");
        assertEquals(0, second.exit(), second.err());
        Map<String,Object> secondCounts = counts(second);
        assertEquals(3, intOf(secondCounts, "existingIdentitiesReused"),
                "the motor, rotor and stator were already manufactured and must not be manufactured again");
        assertEquals(2, intOf(secondCounts, "newIdentitiesManufactured"),
                "only the traction motor and the magnet are genuinely new semantic material");
        assertEquals(0, intOf(secondCounts, "semanticVariants"));

        // History accumulated on the reused identity rather than being replaced.
        FoundryRegistry verified = new FoundryRegistry(registry, Path.of("schemas"));
        assertTrue(verified.verify().passed());
        Map<String,Object> motor = identityByKey(verified.index(), "foundry:v0.1:machine:electric-motor");
        assertEquals(2, array(motor.get("occurrences")).size(), "both manufactures are preserved");
        assertEquals(2, array(motor.get("decisionHistory")).size());
        assertEquals(1, array(motor.get("stateVersions")).size(),
                "one identity in one state, evidenced twice -- not two states");
        assertEquals(SemanticVariants.SINGLE_VARIANT, motor.get("semanticVariantStatus"));
    }

    @Test
    void anUnrelatedDomainManufacturesIndependently() {
        Path registry = temp.resolve("registry");
        run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(), "--register", "--json");

        Result other = run("manufacture", "tidal barrage", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("tidal-barrage.json").toString(), "--register", "--json");
        assertEquals(0, other.exit(), other.err());
        Map<String,Object> counts = counts(other);
        assertEquals(0, intOf(counts, "existingIdentitiesReused"),
                "an unrelated domain must reuse nothing; the machine is not domain-specific");
        assertEquals(2, intOf(counts, "newIdentitiesManufactured"));

        FoundryRegistry verified = new FoundryRegistry(registry, Path.of("schemas"));
        assertTrue(verified.verify().passed());
        assertEquals(5, verified.verify().identityCount());
    }

    // ---------------------------------------------------------------- honesty of the report

    @Test
    void withoutARegistryTheReportSaysWhyEverythingLooksNew() {
        Result result = run("manufacture", "electric motor",
                "--fixture", DEMO.resolve("electric-motor.json").toString());
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("no registry was consulted"),
                "reporting three new identities without saying no registry was consulted would mislead");
        assertTrue(result.out().contains("not evidence that none existed"));
    }

    @Test
    void everyReportedNumberIsReadBackFromTheArtefactsRatherThanTallied() {
        Path registry = temp.resolve("registry");
        Result result = run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(), "--register", "--json");
        Map<String,Object> report = object(Json.parse(result.out()));
        Path packagePath = Path.of(String.valueOf(report.get("packagePath")));

        // Cross-check each counter against the artefacts it claims to describe. Since ADR-0006 the
        // reuse report is run evidence stored beside the registry, so the cross-check reads it
        // from there -- and asserts it is not inside the immutable package.
        assertFalse(java.nio.file.Files.isRegularFile(packagePath.resolve("reuse-report.json")),
                "volatile run evidence must not live inside a content-addressed package");
        Map<String,Object> run = object(FileOps.readJson(
                registry.resolveSibling("runs").resolve(report.get("runId") + ".json")));
        Map<String,Object> reuse = object(run.get("reuseReport"));
        Map<String,Object> reuseCounts = object(reuse.get("counts"));
        Map<String,Object> reported = counts(result);
        assertEquals(intOf(reuseCounts, "newUaoCount"), intOf(reported, "newIdentitiesManufactured"));
        assertEquals(intOf(reuseCounts, "reusedUaoCount"), intOf(reported, "existingIdentitiesReused"));
        assertEquals(intOf(reuseCounts, "newSourceCount"), intOf(reported, "newSources"));
        assertEquals(array(FileOps.readJson(packagePath.resolve("unresolved-items.json"))).size(),
                intOf(reported, "unresolvedRelationships"));
        assertEquals(object(FileOps.readJson(packagePath.resolve("manifest.json"))).get("packageId"),
                report.get("packageId"));
    }

    @Test
    void anIneligiblePackageIsReportedAsRefusedRatherThanSilentlySkipped() {
        Path registry = temp.resolve("registry");
        // A relationship-bearing bundle is EVIDENCE_INCOMPLETE under ASA#29 and inadmissible.
        Result result = run("manufacture", "cow", "--registry", registry.toString(),
                "--fixture", "src/test/resources/fixtures/relationship-bearing-cow.json",
                "--register", "--json");
        Map<String,Object> report = object(Json.parse(result.out()));
        assertEquals("EVIDENCE_INCOMPLETE", report.get("publicationStatus"));
        assertTrue(String.valueOf(report.get("registryAdmission")).startsWith("REFUSED"),
                "a refused admission must be visible, not absent: " + report.get("registryAdmission"));
        assertEquals(1, intOf(counts(result), "unresolvedRelationships"));
    }

    // ---------------------------------------------------------------- discovery surface

    @Test
    void searchFindsARegisteredIdentityAndReportsNoMatchDistinctly() {
        Path registry = temp.resolve("registry");
        run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(), "--register", "--json");

        Result hit = run("search", "electric motor", "--registry", registry.toString(), "--json");
        assertEquals(0, hit.exit());
        assertFalse(array(object(Json.parse(hit.out())).get("matches")).isEmpty());

        Result miss = run("search", "something never manufactured", "--registry", registry.toString(), "--json");
        assertEquals(4, miss.exit(), "no match is a distinct outcome from success and from error");
    }

    @Test
    void anAliasLookupReturnsCandidatesAndANonZeroExit() {
        Path registry = temp.resolve("registry");
        run("manufacture", "electric motor", "--registry", registry.toString(),
                "--fixture", DEMO.resolve("electric-motor.json").toString(), "--register", "--json");

        Result result = run("identity", "electric motor", "--registry", registry.toString(), "--json");
        assertEquals(4, result.exit(), "an alias never resolves, so the console must not report success");
        Map<String,Object> resolution = object(object(Json.parse(result.out())).get("resolution"));
        assertEquals("UNRESOLVED", resolution.get("decision"));
        assertEquals(List.of("ALIAS_MATCH_INSUFFICIENT"), resolution.get("reasonCodes"));
    }

    @Test
    void usageErrorsAreRefusedRatherThanGuessed() {
        assertEquals(2, run("manufacture", "electric motor").exit(), "no evidence source named");
        assertEquals(2, run("manufacture", "a", "b", "--fixture", "x").exit(), "two identity expressions");
        assertEquals(2, run("search", "x").exit(), "no registry named");
        assertEquals(2, run("nonsense").exit());
        assertEquals(2, run("manufacture", "x", "--fixture", "a", "--provider", "b").exit(),
                "a manufacture has one evidence source, not two");
    }

    // ---------------------------------------------------------------- helpers

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] full = new String[args.length + 4];
        System.arraycopy(args, 0, full, 0, args.length);
        full[args.length] = "--work-dir";
        full[args.length + 1] = temp.resolve("work").toString();
        full[args.length + 2] = "--dist-dir";
        full[args.length + 3] = temp.resolve("dist").toString();
        int exit = new OperatorConsole(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(full);
        return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exit, String out, String err) {}

    private static Map<String,Object> counts(Result result) {
        return object(object(Json.parse(result.out())).get("counts"));
    }

    private static int intOf(Map<String,Object> counts, String key) {
        return ((java.math.BigDecimal) counts.get(key)).intValue();
    }

    private static Map<String,Object> identityByKey(Map<String,Object> index, String key) {
        return array(index.get("identities")).stream().map(OperatorConsoleTest::object)
                .filter(v -> key.equals(v.get("resolutionKey"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
