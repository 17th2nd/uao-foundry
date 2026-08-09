package org.seventeenthsecond.uaofoundry.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Arguments {
    private static final Set<String> ALLOWED = Set.of("--identity", "--language", "--profile");

    private final Map<String, String> values;

    private Arguments(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static Arguments parse(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be supplied as --key value pairs.");
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            String value = args[i + 1];

            if (!ALLOWED.contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Option requires a non-blank value: " + key);
            }
            if (values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate option: " + key);
            }
        }
        return new Arguments(values);
    }

    public String required(String key) {
        return optional(key).orElseThrow(() -> new IllegalArgumentException("Missing required option: " + key));
    }

    public Optional<String> optional(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
