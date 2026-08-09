package org.seventeenthsecond.uaofoundry.provider;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Replays a captured provider bundle during resume without invoking the original provider again. */
public final class SnapshotProvider implements FoundryProvider {
    private final Path source;
    private final Map<String, Object> bundle;
    private final String hash;
    private final String name;
    private final String executionMode;

    public SnapshotProvider(Path snapshot, Path schemaDir, String providerName, String executionMode) {
        this.source = snapshot.toAbsolutePath().normalize();
        Object parsed = FileOps.readJson(this.source);
        new SchemaValidator().validate(parsed, schemaDir.resolve("fixture-bundle.schema.json"))
                .requireValid("Provider snapshot bundle");
        this.bundle = Json.object(parsed, "Provider snapshot bundle");
        this.hash = Hashes.canonicalJson(bundle);
        this.name = providerName == null || providerName.isBlank() ? "snapshot" : providerName;
        if (!"fixture".equals(executionMode) && !"live".equals(executionMode)) {
            throw new IllegalArgumentException("Snapshot provider executionMode must be fixture or live.");
        }
        this.executionMode = executionMode;
    }

    public String name() { return name; }
    public String kind() { return "snapshot"; }
    public String executionMode() { return executionMode; }
    public String hash() { return hash; }
    public Path source() { return source; }
    public Map<String, Object> snapshot() { return Json.object(Json.parse(Json.canonical(bundle)), "Provider snapshot"); }
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
        throw new IllegalArgumentException("Provider snapshot field must be string: " + key);
    }
}
