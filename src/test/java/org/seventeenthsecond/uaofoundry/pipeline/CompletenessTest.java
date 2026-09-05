package org.seventeenthsecond.uaofoundry.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The plan's `required` flag decides completeness; an optional question left open is recorded, not blocking. */
class CompletenessTest {
    private static final Path SCHEMAS = Path.of("schemas");
    private static final Path FIXTURE = Path.of("examples/demonstration/electric-motor.json");
    @TempDir Path temp;

    @Test
    void anUnresolvedOptionalQuestionIsRecordedButDoesNotBlockPublication() {
        PipelineResult result = manufacture("optional", false);
        Map<String,Object> coverage = object(FileOps.readJson(result.packagePath().resolve("coverage-report.json")));
        assertEquals(1, ((Number) coverage.get("unresolved")).intValue());
        assertEquals(0, ((Number) coverage.get("unresolvedRequired")).intValue());
        assertEquals(Boolean.TRUE, coverage.get("complete"));
        assertEquals("EXPERIMENTAL", result.publicationStatus());
        assertTrue(new PackageVerifier(SCHEMAS).verify(result.packagePath()).passed());
    }

    @Test
    void anUnresolvedRequiredQuestionStillBlocksPublication() {
        PipelineResult result = manufacture("required", true);
        Map<String,Object> coverage = object(FileOps.readJson(result.packagePath().resolve("coverage-report.json")));
        assertEquals(1, ((Number) coverage.get("unresolvedRequired")).intValue());
        assertEquals(Boolean.FALSE, coverage.get("complete"));
        assertEquals("EVIDENCE_INCOMPLETE", result.publicationStatus());
    }

    private PipelineResult manufacture(String suffix, boolean required) {
        Map<String,Object> fixture = object(Json.parse(FileOps.readText(FIXTURE)));
        List<Object> questions = array(object(fixture.get("manufacturingPlan")).get("completionQuestions"));
        questions.add(Json.object(Json.parse("{\"questionId\":\"q-precursors\",\"prompt\":\"Are precursors evidenced?\",\"required\":" + required + "}"), "q"));
        object(fixture.get("coverageAnswers")).put("q-precursors", "unresolved");
        Path path = temp.resolve("fixture-" + suffix + ".json"); FileOps.writeJson(path, fixture);
        RequestLoader loader = new RequestLoader(SCHEMAS.resolve("manufacturing-request.schema.json"));
        return new FoundryPipeline(SCHEMAS, temp.resolve("work-" + suffix), temp.resolve("dist-" + suffix), "test-sha")
                .manufacture(loader.fromSeed("electric motor", "en", "experimental"), new FixtureProvider(path, SCHEMAS), false);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v) { return (Map<String,Object>) v; }
    @SuppressWarnings("unchecked") private static List<Object> array(Object v) { return (List<Object>) v; }
}
