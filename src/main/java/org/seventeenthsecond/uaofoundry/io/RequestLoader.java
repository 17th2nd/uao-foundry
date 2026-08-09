package org.seventeenthsecond.uaofoundry.io;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;
import org.seventeenthsecond.uaofoundry.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestLoader {
    private final Path schemaPath;
    private final SchemaValidator validator;

    public RequestLoader(Path schemaPath) {
        this.schemaPath = schemaPath;
        this.validator = new SchemaValidator();
    }

    public ManufacturingRequest fromFile(Path requestPath) {
        try {
            Object parsed = Json.parse(Files.readString(requestPath));
            return fromObject(Json.object(parsed, "Manufacturing request"));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read request file " + requestPath + ": " + ex.getMessage(), ex);
        }
    }

    public ManufacturingRequest fromSeed(String identitySeed, String language, String profile) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("identitySeed", identitySeed);
        if (language != null) request.put("inputLanguage", language);
        if (profile != null) request.put("manufacturingProfile", profile);
        return fromObject(request);
    }

    public ManufacturingRequest fromObject(Map<String, Object> request) {
        ValidationResult initial = validator.validate(request, schemaPath);
        initial.requireValid("Manufacturing request");
        ManufacturingRequest normalized = ManufacturingRequest.fromValidatedMap(request);
        ValidationResult normalizedValidation = validator.validate(normalized.toMap(), schemaPath);
        normalizedValidation.requireValid("Normalized manufacturing request");
        return normalized;
    }

    public ValidationResult validateRaw(Path requestPath) {
        try {
            Object parsed = Json.parse(Files.readString(requestPath));
            return validator.validate(parsed, schemaPath);
        } catch (IOException | IllegalArgumentException ex) {
            return new ValidationResult(java.util.List.of(ex.getMessage()));
        }
    }
}
