package org.seventeenthsecond.usifoundry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.significance.SignificanceBoundary;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Application-level tests for the USI Foundry (directive §28).
 *
 * <p>These drive the real HTTP API against a real local registry, so they test the application an
 * operator actually uses rather than the service class in isolation.
 */
class UsiApplicationTest {
    private static final Path DEMO = Path.of("examples/demonstration");

    @TempDir Path temp;
    private UsiApiServer server;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws Exception {
        UsiFoundryConfig config = new UsiFoundryConfig(temp.resolve("usi-home"));
        server = new UsiApiServer(new UsiFoundryService(config), 0);
        server.start();
        base = server.address();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    // ---------------------------------------------------------------- local-only posture

    @Test
    void theServerBindsToLoopbackOnly() throws Exception {
        // "Local-first" has to mean the socket, not just the storage. A manufacturing tool bound to
        // every interface with no authentication hands its registry to the network.
        assertTrue(base.startsWith("http://127.0.0.1:"), base);
        assertEquals("PASS", get("/api/status").get("registryVerification"),
                "a brand-new registry is a new registry, not a broken one");
    }

    @Test
    void aCrossOriginRequestIsRefusedWhileTheApplicationsOwnIsNot() throws Exception {
        HttpResponse<String> foreign = http.send(HttpRequest.newBuilder(URI.create(base + "api/registry/verify"))
                .header("Origin", "http://evil.example").POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, foreign.statusCode(), "a page from elsewhere must not drive the local API");

        HttpResponse<String> own = http.send(HttpRequest.newBuilder(URI.create(base + "api/registry/verify"))
                .header("Origin", base.substring(0, base.length() - 1))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, own.statusCode(),
                "browsers send Origin on same-origin POSTs, so the application must still reach its own API");
    }

    // ---------------------------------------------------------------- terminology (ADR-0003/0004)

    @Test
    void theOperatorInterfaceSpeaksUsiRatherThanUao() throws Exception {
        String html = resource("/app/index.html");
        assertTrue(html.contains("USI FOUNDRY"), "the product is USI Foundry");
        assertTrue(html.contains("Universal Semantic Identity"), "the term must be spelled out for an operator");

        // Legacy terminology must not appear as product language. It is permitted only where it is
        // genuinely a legacy wire identifier, which the UI labels as such at render time.
        String visible = html.replaceAll("(?s)<!--.*?-->", "");
        assertFalse(visible.contains("UAO"), "UAO must not appear as product language in the UI");
        assertFalse(visible.contains("Universal ASA Object"));
    }

    @Test
    void aLegacyIdentifierIsLabelledRatherThanDisguised() throws Exception {
        Map<String,Object> result = manufacture("electric motor", "electric-motor.json", true);
        String usiId = String.valueOf(result.get("usiId"));

        // ADR-0005: the value is the canonical identifier, unchanged. No usi- string is minted,
        // because an identifier an operator can copy must be one the registry accepts back.
        assertTrue(usiId.matches("uao-[a-f0-9]{12}"), usiId);
        assertEquals("legacy-uao", result.get("identifierScheme"));

        String js = resource("/app/app.js");
        assertTrue(js.contains("legacy wire identifier"),
                "the UI must say which scheme the identifier is in rather than presenting it bare");

        // And the identifier it shows is one the API will accept back.
        assertEquals("SAME", get("/api/identity/" + usiId).get("decision"));
    }

    // ---------------------------------------------------------------- manufacture and reuse

    @Test
    void manufactureRegisterRediscoverAndReuseThroughTheApi() throws Exception {
        Map<String,Object> first = manufacture("electric motor", "electric-motor.json", true);
        assertEquals("PASS", first.get("verification"));
        assertEquals("REGISTERED", first.get("registryAdmission"));
        assertEquals(3, count(first, "newIdentitiesManufactured"));
        assertEquals(0, count(first, "existingIdentitiesReused"));

        // Rediscovery by a durable external identifier, not by the words used the first time.
        Map<String,Object> found = get("/api/identity/wikidata%3AQ53068");
        assertEquals("SAME", found.get("decision"));
        assertEquals(List.of("EXTERNAL_IDENTIFIER_CONTINUITY"), found.get("reasonCodes"));

        Map<String,Object> second = manufacture("EV traction motor", "ev-traction-motor.json", true);
        assertEquals(3, count(second, "existingIdentitiesReused"),
                "prior governed identities must be reused where justified");
        assertEquals(2, count(second, "newIdentitiesManufactured"),
                "and only genuinely new semantic material manufactured");
    }

    @Test
    void anUnrelatedDomainReusesNothing() throws Exception {
        manufacture("electric motor", "electric-motor.json", true);
        Map<String,Object> other = manufacture("tidal barrage", "tidal-barrage.json", true);
        assertEquals(0, count(other, "existingIdentitiesReused"),
                "a false reuse across unrelated domains would be worse than no reuse at all");
        assertEquals(2, count(other, "newIdentitiesManufactured"));
    }

    @Test
    void repeatedIdenticalManufactureAccumulatesWithoutCollision() throws Exception {
        // The application-level guard against finding P9-1 regressing.
        String packageId = null;
        for (int i = 1; i <= 10; i++) {
            Map<String,Object> result = manufacture("electric motor", "electric-motor.json", true);
            assertEquals("REGISTERED", result.get("registryAdmission"), "manufacture " + i + " was refused");
            if (i >= 3) {
                if (packageId == null) packageId = String.valueOf(result.get("packageId"));
                assertEquals(packageId, result.get("packageId"),
                        "identical material must keep producing one package id");
            }
        }
        assertEquals("PASS", get("/api/status").get("registryVerification"));
    }

    @Test
    void semanticReuseSurvivesADifferentlySourcedManufacture() throws Exception {
        manufacture("electric motor", "electric-motor.json", true);

        // Same identity, evidence renamed: provenance differs, meaning does not.
        Path variant = temp.resolve("resourced.json");
        String bundle = Files.readString(DEMO.resolve("electric-motor.json"))
                .replace("src-motor", "src-motor-alt").replace("src-components", "src-components-alt");
        Files.writeString(variant, bundle);

        Map<String,Object> again = manufactureFrom("electric motor", variant, true);
        assertEquals("REGISTERED", again.get("registryAdmission"));
        assertEquals(3, count(again, "existingIdentitiesReused"),
                "differently-sourced evidence for one identity must not read as a new identity");
        assertEquals(0, count(again, "semanticVariants"));
    }

    // ---------------------------------------------------------------- fail-closed behaviour

    @Test
    void anAmbiguousIdentityIsBlockedFromAutomaticReuseAndSaysSo() throws Exception {
        manufacture("electric motor", "electric-motor.json", true);

        // Same identity, genuinely different meaning: a semantic variant, not a re-observation.
        Path divergent = temp.resolve("divergent.json");
        Files.writeString(divergent, Files.readString(DEMO.resolve("electric-motor.json"))
                .replace("An electric motor converts electrical energy into mechanical rotation.",
                        "An electric motor is a decorative object with no function."));
        // Manufacture itself is refused: automatic reuse of an identity whose meaning has diverged
        // would silently overwrite one account with another. The refusal is classified and carries
        // guidance rather than collapsing into a generic failure.
        Map<String,Object> failure = manufactureExpectingFailure("electric motor", divergent);
        assertEquals("SEMANTIC_VARIANT_CONFLICT", failure.get("error"));
        assertTrue(String.valueOf(failure.get("message")).contains("SEMANTIC_VARIANT_DIVERGENCE"));
        assertTrue(String.valueOf(failure.get("guidance")).contains("Every occurrence is preserved"),
                "the operator must be told that nothing was discarded");

        // The registry is untouched and the original identity still resolves.
        assertEquals("PASS", get("/api/status").get("registryVerification"));
        assertEquals("SAME", get("/api/identity/wikidata%3AQ53068").get("decision"),
                "an unrelated-to-this-conflict lookup must keep working");
    }

    @Test
    void aRelationshipBearingBundleStaysFailClosedAndSaysWhy() throws Exception {
        Map<String,Object> result = manufactureFrom("cow",
                Path.of("src/test/resources/fixtures/relationship-bearing-cow.json"), true);
        assertEquals("EVIDENCE_INCOMPLETE", result.get("publicationStatus"));
        assertEquals("REFUSED", result.get("registryAdmission"));
        assertEquals("URO_TYPE_AUTHORITY_UNAVAILABLE", result.get("relationshipAuthority"));
        assertTrue(String.valueOf(result.get("relationshipAuthorityNote")).contains("ASA#29"),
                "an operator must be told which authority is missing, not merely that something failed");
        assertEquals(1, count(result, "relationshipsUnresolved"));
    }

    @Test
    void errorsAreClassifiedRatherThanCollapsed() throws Exception {
        // An operator who picked the wrong evidence bundle must be sent to the bundle, not to the
        // provider timeout settings.
        Map<String,Object> failure = manufactureExpectingFailure("granite", DEMO.resolve("tidal-barrage.json"));
        assertEquals("INVALID_INPUT", failure.get("error"));
        assertTrue(String.valueOf(failure.get("guidance")).contains("bundle"), failure.toString());

        Map<String,Object> missing = manufactureExpectingFailure("anything", temp.resolve("absent.json"));
        assertEquals("CONFIGURATION_ERROR", missing.get("error"));

        HttpResponse<String> notFound = send("GET", "/api/package/pkg-0000000000000000", null);
        assertEquals(404, notFound.statusCode());
        assertEquals("NOT_FOUND", object(Json.parse(notFound.body())).get("error"));
    }

    // ---------------------------------------------------------------- significance boundary

    @Test
    void noSignificanceEntersAnyApplicationSurface() throws Exception {
        Map<String,Object> result = manufacture("electric motor", "electric-motor.json", true);

        List<String> violations = new ArrayList<>();
        SignificanceBoundary.collect(result, "$manufacture", violations);
        SignificanceBoundary.collect(get("/api/status"), "$status", violations);
        SignificanceBoundary.collect(get("/api/runs"), "$runs", violations);
        SignificanceBoundary.collect(get("/api/identity/" + result.get("usiId")), "$identity", violations);
        SignificanceBoundary.collect(get("/api/package/" + result.get("packageId")), "$package", violations);
        assertEquals(List.of(), violations, "USI state must never carry significance");

        // The A_x / R_x view supplies inputs and names what it does not own.
        Map<String,Object> inputs = get("/api/significance/" + result.get("usiId"));
        assertNotNull(inputs.get("A_x"));
        Map<String,Object> rx = object(inputs.get("R_x"));
        assertEquals(Boolean.FALSE, rx.get("complete"));
        assertEquals("17th2nd/ASA#29", rx.get("blockedBy"));
        Map<String,Object> notSupplied = object(inputs.get("notSupplied"));
        assertTrue(object(notSupplied.get("runtimeOwned")).containsKey("C_q"));
        assertTrue(object(notSupplied.get("significanceEngineOwned")).containsKey("S_v"));
    }

    // ---------------------------------------------------------------- discovery surfaces

    @Test
    void searchInspectAndRunHistoryAreServed() throws Exception {
        Map<String,Object> result = manufacture("electric motor", "electric-motor.json", true);

        List<Object> matches = array(get("/api/registry/search?q=electric+motor").get("matches"));
        assertFalse(matches.isEmpty());
        assertEquals("legacy-uao", object(matches.getFirst()).get("identifierScheme"));

        Map<String,Object> pkg = get("/api/package/" + result.get("packageId"));
        assertEquals(Boolean.TRUE, pkg.get("verificationPassed"));
        assertEquals(Boolean.FALSE, pkg.get("legacyEmbeddedReuseReport"),
                "a package manufactured now carries no embedded run evidence (ADR-0006)");
        assertEquals(3, array(pkg.get("identities")).size());

        Map<String,Object> runs = get("/api/runs");
        assertEquals(1, array(runs.get("runs")).size());
        assertEquals(result.get("runId"), object(array(runs.get("runs")).getFirst()).get("runId"));

        // An alias returns candidates, never an identity.
        Map<String,Object> byAlias = get("/api/identity/electric%20motor");
        assertEquals("UNRESOLVED", byAlias.get("decision"));
        assertEquals(List.of("ALIAS_MATCH_INSUFFICIENT"), byAlias.get("reasonCodes"));
    }

    @Test
    void stageProgressComesFromThePipelineNotFromATimer() throws Exception {
        Map<String,Object> started = post("/api/manufacture", Map.of(
                "identity", "electric motor", "provider", "fixture",
                "fixture", DEMO.resolve("electric-motor.json").toAbsolutePath().toString(),
                "register", true));
        Map<String,Object> status = awaitJob(String.valueOf(started.get("jobToken")));

        List<Object> stages = array(status.get("stages"));
        assertEquals(16, stages.size());
        assertEquals(16, ((java.math.BigDecimal) status.get("completedCount")).intValue(),
                "every stage must be reported complete because the pipeline recorded it");
        for (Object raw : stages) assertEquals("COMPLETE", object(raw).get("status"));
    }

    // ---------------------------------------------------------------- helpers

    private Map<String,Object> manufacture(String identity, String fixture, boolean register) throws Exception {
        return manufactureFrom(identity, DEMO.resolve(fixture), register);
    }

    private Map<String,Object> manufactureFrom(String identity, Path fixture, boolean register) throws Exception {
        Map<String,Object> started = post("/api/manufacture", Map.of(
                "identity", identity, "provider", "fixture",
                "fixture", fixture.toAbsolutePath().toString(), "register", register));
        Map<String,Object> status = awaitJob(String.valueOf(started.get("jobToken")));
        if (status.get("failure") != null) {
            throw new AssertionError("manufacture failed: " + Json.canonical(object(status.get("failure"))));
        }
        return object(status.get("result"));
    }

    private Map<String,Object> manufactureExpectingFailure(String identity, Path fixture) throws Exception {
        Map<String,Object> started = post("/api/manufacture", Map.of(
                "identity", identity, "provider", "fixture",
                "fixture", fixture.toAbsolutePath().toString(), "register", true));
        Map<String,Object> status = awaitJob(String.valueOf(started.get("jobToken")));
        assertEquals("FAILED", status.get("state"), "expected this manufacture to fail");
        return object(status.get("failure"));
    }

    private Map<String,Object> awaitJob(String token) throws Exception {
        for (int i = 0; i < 300; i++) {
            Map<String,Object> status = get("/api/manufacture/" + token);
            if (!"RUNNING".equals(status.get("state"))) return status;
            Thread.sleep(100);
        }
        throw new AssertionError("manufacture did not finish");
    }

    private Map<String,Object> get(String path) throws Exception {
        HttpResponse<String> response = send("GET", path, null);
        return object(Json.parse(response.body()));
    }

    private Map<String,Object> post(String path, Map<String,Object> body) throws Exception {
        HttpResponse<String> response = send("POST", path, Json.canonical(body));
        return object(Json.parse(response.body()));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path.substring(1)));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = UsiApplicationTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "packaged UI resource missing: " + path
                    + " (build with 'mvn package' so app/frontend is bundled)");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(Map<String,Object> result, String key) {
        return ((java.math.BigDecimal) object(result.get("counts")).get(key)).intValue();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) { return (List<Object>) value; }
}
