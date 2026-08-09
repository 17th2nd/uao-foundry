package org.seventeenthsecond.uaofoundry.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Arguments {
    private static final Set<String> ALLOWED = Set.of(
            "--identity", "--language", "--profile", "--request", "--schema-dir",
            "--fixture", "--provider-command", "--provider-timeout-seconds",
            "--work-dir", "--dist-dir", "--repository-commit"
    );

    private final Map<String, String> values;
    private final List<String> positionals;

    private Arguments(Map<String, String> values, List<String> positionals) {
        this.values = Map.copyOf(values);
        this.positionals = List.copyOf(positionals);
    }

    public static Arguments parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> positionals = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) { positionals.add(token); continue; }
            if (!ALLOWED.contains(token)) throw new IllegalArgumentException("Unknown option: " + token);
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new IllegalArgumentException("Option requires a non-blank value: " + token);
            String value = args[++i];
            if (value.isBlank()) throw new IllegalArgumentException("Option requires a non-blank value: " + token);
            if (values.putIfAbsent(token, value) != null) throw new IllegalArgumentException("Duplicate option: " + token);
        }
        return new Arguments(values, positionals);
    }

    public Optional<String> optional(String key) { return Optional.ofNullable(values.get(key)); }
    public String required(String key) { return optional(key).orElseThrow(() -> new IllegalArgumentException("Missing required option: " + key)); }
    public List<String> positionals() { return positionals; }

    public String identitySeed() {
        String flagged = values.get("--identity");
        if (flagged != null && !positionals.isEmpty()) throw new IllegalArgumentException("Identity seed must be supplied either positionally or with --identity, not both.");
        if (flagged != null) return flagged;
        if (positionals.size() == 1) return positionals.getFirst();
        if (positionals.isEmpty()) throw new IllegalArgumentException("Missing identity seed.");
        throw new IllegalArgumentException("Expected one identity seed, received " + positionals.size() + ".");
    }
}
