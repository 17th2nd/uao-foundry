package org.seventeenthsecond.uaofoundry.validation;

import org.seventeenthsecond.uaofoundry.json.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Fail-closed validator for the JSON-Schema 2020-12 subset used by Foundry-owned
 * contracts and the checked-in ASA validation projections. Unsupported assertion
 * keywords are reported as validation errors rather than silently ignored.
 */
public final class SchemaValidator {
    private static final Set<String> ANNOTATIONS = Set.of(
            "$schema", "$id", "$comment", "title", "description", "default", "examples", "readOnly", "writeOnly"
    );
    private static final Set<String> SUPPORTED = Set.of(
            "$ref", "type", "required", "properties", "additionalProperties", "const", "enum",
            "minLength", "maxLength", "pattern", "format", "items", "minItems", "maxItems", "uniqueItems",
            "minimum", "maximum", "minProperties", "maxProperties", "allOf", "anyOf", "oneOf", "not",
            "if", "then", "else"
    );

    public ValidationResult validate(Object instance, Path schemaPath) {
        try {
            Object schema = Json.parse(Files.readString(schemaPath));
            List<String> errors = new ArrayList<>();
            validateAgainst(instance, Json.object(schema, "Schema"), schemaPath.toAbsolutePath().normalize(), "$", errors);
            return new ValidationResult(errors);
        } catch (IOException ex) {
            return new ValidationResult(List.of("Unable to read schema " + schemaPath + ": " + ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return new ValidationResult(List.of("Invalid schema " + schemaPath + ": " + ex.getMessage()));
        }
    }

    private void validateAgainst(Object instance, Map<String, Object> schema, Path schemaPath, String path, List<String> errors) {
        rejectUnsupportedKeywords(schema, path, errors);

        if (schema.containsKey("$ref")) {
            resolveRef(schemaPath, string(schema.get("$ref"), "$ref"), path, errors, instance);
            return;
        }

        if (schema.containsKey("allOf")) {
            for (Object child : Json.array(schema.get("allOf"), "allOf")) {
                validateAgainst(instance, Json.object(child, "allOf item"), schemaPath, path, errors);
            }
        }
        if (schema.containsKey("anyOf")) {
            int passing = countPassing(instance, Json.array(schema.get("anyOf"), "anyOf"), schemaPath, path);
            if (passing == 0) errors.add(path + ": does not match anyOf branches");
        }
        if (schema.containsKey("oneOf")) {
            int passing = countPassing(instance, Json.array(schema.get("oneOf"), "oneOf"), schemaPath, path);
            if (passing != 1) errors.add(path + ": must match exactly one oneOf branch; matched " + passing);
        }
        if (schema.containsKey("not")) {
            List<String> nested = new ArrayList<>();
            validateAgainst(instance, Json.object(schema.get("not"), "not"), schemaPath, path, nested);
            if (nested.isEmpty()) errors.add(path + ": matches forbidden not-schema");
        }
        if (schema.containsKey("if")) {
            List<String> condition = new ArrayList<>();
            validateAgainst(instance, Json.object(schema.get("if"), "if"), schemaPath, path, condition);
            if (condition.isEmpty() && schema.containsKey("then")) {
                validateAgainst(instance, Json.object(schema.get("then"), "then"), schemaPath, path, errors);
            } else if (!condition.isEmpty() && schema.containsKey("else")) {
                validateAgainst(instance, Json.object(schema.get("else"), "else"), schemaPath, path, errors);
            }
        }

        if (schema.containsKey("type") && !matchesType(instance, schema.get("type"))) {
            errors.add(path + ": expected type " + Json.canonical(schema.get("type")) + " but was " + typeName(instance));
            return;
        }

        if (schema.containsKey("const") && !jsonEquals(instance, schema.get("const"))) {
            errors.add(path + ": value does not equal const " + Json.canonical(schema.get("const")));
        }
        if (schema.containsKey("enum")) {
            boolean found = false;
            for (Object allowed : Json.array(schema.get("enum"), "enum")) {
                if (jsonEquals(instance, allowed)) { found = true; break; }
            }
            if (!found) errors.add(path + ": value is not in enum");
        }

        if (instance instanceof String s) validateString(s, schema, path, errors);
        if (instance instanceof BigDecimal n) validateNumber(n, schema, path, errors);
        if (instance instanceof List<?> list) validateArray(list, schema, schemaPath, path, errors);
        if (instance instanceof Map<?, ?> raw) validateObject(raw, schema, schemaPath, path, errors);
    }

    private void validateString(String value, Map<String, Object> schema, String path, List<String> errors) {
        if (schema.containsKey("minLength") && value.codePointCount(0, value.length()) < integer(schema.get("minLength"), "minLength")) {
            errors.add(path + ": string shorter than minLength");
        }
        if (schema.containsKey("maxLength") && value.codePointCount(0, value.length()) > integer(schema.get("maxLength"), "maxLength")) {
            errors.add(path + ": string longer than maxLength");
        }
        if (schema.containsKey("pattern")) {
            try {
                if (!Pattern.compile(string(schema.get("pattern"), "pattern")).matcher(value).find()) {
                    errors.add(path + ": string does not match pattern");
                }
            } catch (PatternSyntaxException ex) {
                errors.add(path + ": schema pattern is invalid: " + ex.getMessage());
            }
        }
        if (schema.containsKey("format")) {
            String format = string(schema.get("format"), "format");
            if ("date-time".equals(format)) {
                try { OffsetDateTime.parse(value); }
                catch (DateTimeParseException ex) { errors.add(path + ": invalid date-time"); }
            } else {
                errors.add(path + ": unsupported format assertion: " + format);
            }
        }
    }

    private void validateNumber(BigDecimal value, Map<String, Object> schema, String path, List<String> errors) {
        if (schema.containsKey("minimum") && value.compareTo(number(schema.get("minimum"), "minimum")) < 0) {
            errors.add(path + ": number below minimum");
        }
        if (schema.containsKey("maximum") && value.compareTo(number(schema.get("maximum"), "maximum")) > 0) {
            errors.add(path + ": number above maximum");
        }
    }

    private void validateArray(List<?> list, Map<String, Object> schema, Path schemaPath, String path, List<String> errors) {
        if (schema.containsKey("minItems") && list.size() < integer(schema.get("minItems"), "minItems")) errors.add(path + ": too few items");
        if (schema.containsKey("maxItems") && list.size() > integer(schema.get("maxItems"), "maxItems")) errors.add(path + ": too many items");
        if (Boolean.TRUE.equals(schema.get("uniqueItems"))) {
            Set<String> seen = new HashSet<>();
            for (Object item : list) {
                if (!seen.add(Json.canonical(item))) { errors.add(path + ": array items are not unique"); break; }
            }
        }
        if (schema.containsKey("items")) {
            Map<String, Object> child = Json.object(schema.get("items"), "items");
            for (int i = 0; i < list.size(); i++) validateAgainst(list.get(i), child, schemaPath, path + "[" + i + "]", errors);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateObject(Map<?, ?> raw, Map<String, Object> schema, Path schemaPath, String path, List<String> errors) {
        Map<String, Object> object = (Map<String, Object>) raw;
        if (schema.containsKey("minProperties") && object.size() < integer(schema.get("minProperties"), "minProperties")) errors.add(path + ": too few properties");
        if (schema.containsKey("maxProperties") && object.size() > integer(schema.get("maxProperties"), "maxProperties")) errors.add(path + ": too many properties");

        if (schema.containsKey("required")) {
            for (Object required : Json.array(schema.get("required"), "required")) {
                String key = string(required, "required item");
                if (!object.containsKey(key)) errors.add(path + ": missing required property '" + key + "'");
            }
        }

        Map<String, Object> properties = schema.containsKey("properties")
                ? Json.object(schema.get("properties"), "properties") : Map.of();
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object propertySchema = properties.get(entry.getKey());
            if (propertySchema != null) {
                validateAgainst(entry.getValue(), Json.object(propertySchema, "property schema"), schemaPath, path + "." + entry.getKey(), errors);
                continue;
            }
            Object additional = schema.get("additionalProperties");
            if (Boolean.FALSE.equals(additional)) {
                errors.add(path + ": additional property is not allowed: '" + entry.getKey() + "'");
            } else if (additional instanceof Map<?, ?>) {
                validateAgainst(entry.getValue(), Json.object(additional, "additionalProperties"), schemaPath, path + "." + entry.getKey(), errors);
            }
        }
    }

    private int countPassing(Object instance, List<Object> schemas, Path schemaPath, String path) {
        int count = 0;
        for (Object raw : schemas) {
            List<String> nested = new ArrayList<>();
            validateAgainst(instance, Json.object(raw, "schema branch"), schemaPath, path, nested);
            if (nested.isEmpty()) count++;
        }
        return count;
    }

    private void resolveRef(Path currentSchemaPath, String ref, String instancePath, List<String> errors, Object instance) {
        try {
            String filePart = ref;
            String fragment = null;
            int hash = ref.indexOf('#');
            if (hash >= 0) {
                filePart = ref.substring(0, hash);
                fragment = ref.substring(hash + 1);
            }
            Path targetPath = filePart.isEmpty() ? currentSchemaPath : currentSchemaPath.getParent().resolve(filePart).normalize();
            Object targetRoot = Json.parse(Files.readString(targetPath));
            Object target = fragment == null || fragment.isEmpty() ? targetRoot : resolvePointer(targetRoot, fragment);
            validateAgainst(instance, Json.object(target, "$ref target"), targetPath, instancePath, errors);
        } catch (Exception ex) {
            errors.add(instancePath + ": unable to resolve $ref '" + ref + "': " + ex.getMessage());
        }
    }

    private Object resolvePointer(Object root, String fragment) {
        if (!fragment.startsWith("/")) throw new IllegalArgumentException("Only JSON Pointer fragments are supported");
        Object current = root;
        for (String encoded : fragment.substring(1).split("/")) {
            String token = encoded.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) current = map.get(token);
            else if (current instanceof List<?> list) current = list.get(Integer.parseInt(token));
            else throw new IllegalArgumentException("Pointer traverses a scalar at " + token);
            if (current == null) throw new IllegalArgumentException("Pointer target not found: " + token);
        }
        return current;
    }

    private void rejectUnsupportedKeywords(Map<String, Object> schema, String path, List<String> errors) {
        for (String key : schema.keySet()) {
            if (ANNOTATIONS.contains(key) || SUPPORTED.contains(key) || key.startsWith("x-")) continue;
            errors.add(path + ": unsupported schema keyword: " + key);
        }
    }

    private boolean matchesType(Object instance, Object rawType) {
        if (rawType instanceof String type) return matchesTypeName(instance, type);
        if (rawType instanceof List<?> list) {
            for (Object item : list) if (item instanceof String type && matchesTypeName(instance, type)) return true;
            return false;
        }
        throw new IllegalArgumentException("Schema type must be string or array of strings");
    }

    private boolean matchesTypeName(Object value, String type) {
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof BigDecimal;
            case "integer" -> value instanceof BigDecimal n && n.stripTrailingZeros().scale() <= 0;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> throw new IllegalArgumentException("Unsupported JSON Schema type: " + type);
        };
    }

    private boolean jsonEquals(Object left, Object right) {
        return Json.canonical(left).equals(Json.canonical(right));
    }
    private String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?>) return "object";
        if (value instanceof List<?>) return "array";
        if (value instanceof String) return "string";
        if (value instanceof BigDecimal) return "number";
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }
    private String string(Object value, String name) {
        if (value instanceof String s) return s;
        throw new IllegalArgumentException(name + " must be a string");
    }
    private int integer(Object value, String name) {
        BigDecimal n = number(value, name);
        try { return n.intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(name + " must be an integer"); }
    }
    private BigDecimal number(Object value, String name) {
        if (value instanceof BigDecimal n) return n;
        throw new IllegalArgumentException(name + " must be a number");
    }
}
