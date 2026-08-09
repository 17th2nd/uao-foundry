package org.seventeenthsecond.uaofoundry.model;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable Foundry request. Optional values are represented by null and omitted on output. */
public record ManufacturingRequest(
        String schemaVersion,
        String requestId,
        String identitySeed,
        String inputLanguage,
        String scopeHint,
        String jurisdictionHint,
        String timeScope,
        String manufacturingProfile,
        String executionMode,
        String requestedVersion
) {
    public static final String SCHEMA_VERSION = "0.1.0";

    public static ManufacturingRequest fromValidatedMap(Map<String, Object> source) {
        Map<String, Object> normalizedForId = new LinkedHashMap<>();
        normalizedForId.put("schemaVersion", stringOrDefault(source, "schemaVersion", SCHEMA_VERSION));
        normalizedForId.put("identitySeed", requiredString(source, "identitySeed"));
        putIfPresent(normalizedForId, "inputLanguage", stringOrDefault(source, "inputLanguage", "en"));
        putIfPresent(normalizedForId, "scopeHint", optionalString(source, "scopeHint"));
        putIfPresent(normalizedForId, "jurisdictionHint", optionalString(source, "jurisdictionHint"));
        putIfPresent(normalizedForId, "timeScope", optionalString(source, "timeScope"));
        putIfPresent(normalizedForId, "manufacturingProfile", stringOrDefault(source, "manufacturingProfile", "experimental"));
        putIfPresent(normalizedForId, "executionMode", stringOrDefault(source, "executionMode", "fixture"));
        putIfPresent(normalizedForId, "requestedVersion", stringOrDefault(source, "requestedVersion", "0.1.0"));

        String derivedId = StableIdentifiers.forJson("req", 16, normalizedForId);
        String suppliedId = optionalString(source, "requestId");
        if (suppliedId != null && !suppliedId.equals(derivedId)) {
            throw new IllegalArgumentException("Supplied requestId does not match deterministic request content. Expected " + derivedId);
        }

        return new ManufacturingRequest(
                (String) normalizedForId.get("schemaVersion"),
                derivedId,
                (String) normalizedForId.get("identitySeed"),
                (String) normalizedForId.get("inputLanguage"),
                (String) normalizedForId.get("scopeHint"),
                (String) normalizedForId.get("jurisdictionHint"),
                (String) normalizedForId.get("timeScope"),
                (String) normalizedForId.get("manufacturingProfile"),
                (String) normalizedForId.get("executionMode"),
                (String) normalizedForId.get("requestedVersion")
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", schemaVersion);
        out.put("requestId", requestId);
        out.put("identitySeed", identitySeed);
        putIfPresent(out, "inputLanguage", inputLanguage);
        putIfPresent(out, "scopeHint", scopeHint);
        putIfPresent(out, "jurisdictionHint", jurisdictionHint);
        putIfPresent(out, "timeScope", timeScope);
        putIfPresent(out, "manufacturingProfile", manufacturingProfile);
        putIfPresent(out, "executionMode", executionMode);
        putIfPresent(out, "requestedVersion", requestedVersion);
        return out;
    }

    private static String requiredString(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required non-blank string: " + key);
        return value;
    }
    private static String stringOrDefault(Map<String, Object> map, String key, String fallback) {
        String value = optionalString(map, key);
        return value == null ? fallback : value;
    }
    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(key + " must be a string or null.");
    }
    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
