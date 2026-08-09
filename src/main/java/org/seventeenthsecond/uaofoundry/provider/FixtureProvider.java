package org.seventeenthsecond.uaofoundry.provider;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Deterministic offline provider. Identity-specific material is fixture data, never core logic. */
public final class FixtureProvider implements FoundryProvider {
    private final Path source;
    private final Map<String, Object> bundle;
    private final String hash;

    public FixtureProvider(Path source, Path schemaDir) {
        this.source = source.toAbsolutePath().normalize();
        Object parsed = FileOps.readJson(this.source);
        new SchemaValidator().validate(parsed, schemaDir.resolve("fixture-bundle.schema.json"))
                .requireValid("Fixture provider bundle");
        this.bundle = Json.object(parsed, "Fixture bundle");
        this.hash = Hashes.canonicalJson(bundle);
    }

    public String name() { return "fixture"; }
    public String hash() { return hash; }
    public Path source() { return source; }
    public String identitySeed() { return string("identitySeed"); }
    public String fixedClock() { return string("fixedClock"); }
    public String knowledgeHorizon() { return string("knowledgeHorizon"); }

    @SuppressWarnings("unchecked") public List<Object> interpretations() { return (List<Object>) bundle.get("interpretations"); }
    public Map<String, Object> scopeResolution() { return Json.object(bundle.get("scopeResolution"), "scopeResolution"); }
    public Map<String, Object> manufacturingPlan() { return Json.object(bundle.get("manufacturingPlan"), "manufacturingPlan"); }
    public Map<String, Object> sourceStrategy() { return Json.object(bundle.get("sourceStrategy"), "sourceStrategy"); }
    @SuppressWarnings("unchecked") public List<Object> sources() { return (List<Object>) bundle.get("sources"); }
    public Map<String, Object> candidates() { return Json.object(bundle.get("candidates"), "candidates"); }
    public Map<String, Object> coverageAnswers() { return Json.object(bundle.get("coverageAnswers"), "coverageAnswers"); }

    private String string(String key) {
        Object value = bundle.get(key);
        if (value instanceof String s) return s;
        throw new IllegalArgumentException("Fixture field must be string: " + key);
    }
}
