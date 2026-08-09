package org.seventeenthsecond.uaofoundry.provider;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Provider boundary for identity-specific interpretation, planning, sources and candidates. */
public interface FoundryProvider {
    String name();
    String hash();
    Path source();
    String identitySeed();
    String fixedClock();
    String knowledgeHorizon();
    List<Object> interpretations();
    Map<String, Object> scopeResolution();
    Map<String, Object> manufacturingPlan();
    Map<String, Object> sourceStrategy();
    List<Object> sources();
    Map<String, Object> candidates();
    Map<String, Object> coverageAnswers();
}
