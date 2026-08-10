package org.seventeenthsecond.uaofoundry.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PipelineResult(String jobId, Path packagePath, String publicationStatus, String rootUaoId, boolean verificationPassed, int resumedStages, List<String> invalidatedStages) {
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("packagePath", packagePath.toString());
        out.put("publicationStatus", publicationStatus);
        out.put("rootUaoId", rootUaoId);
        out.put("verificationPassed", verificationPassed);
        out.put("resumedStages", resumedStages);
        out.put("invalidatedStages", new ArrayList<>(invalidatedStages));
        return out;
    }
}
