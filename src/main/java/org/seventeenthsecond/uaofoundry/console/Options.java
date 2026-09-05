package org.seventeenthsecond.uaofoundry.console;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Console option parsing.
 *
 * <p>Every option that could change what gets manufactured is explicit and has no clever default.
 * In particular the registry is never inferred: a manufacture that silently found a registry would
 * reuse identities the operator did not know existed, and one that silently missed a registry would
 * report every identity as new. Both are wrong in ways that look like success.
 */
record Options(List<String> positionals, Path registry, Path schemaDir, Path workDir, Path distDir,
               Path fixture, Path providerCommand, Path runStore, String context, String language, String profile,
               String repositoryCommit, String explicitClock, int catalogLimit, int timeoutSeconds,
               boolean register, boolean json, Path relationshipEdition) {

    static Options parse(String[] args) {
        List<String> positionals = new ArrayList<>();
        Path registry = null, fixture = null, providerCommand = null, runStore = null, relationshipEdition = null;
        Path schemaDir = Path.of("schemas");
        Path workDir = Path.of("work");
        Path distDir = Path.of("dist");
        String context = null, language = "en", profile = "experimental", repositoryCommit = "local", clock = null;
        int catalogLimit = 5000, timeoutSeconds = 900;
        boolean register = false, json = false;

        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) { positionals.add(token); continue; }
            switch (token) {
                case "--register" -> { register = true; continue; }
                case "--json" -> { json = true; continue; }
                default -> { }
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Option requires a value: " + token);
            }
            String value = args[++i];
            switch (token) {
                case "--registry" -> registry = Path.of(value);
                case "--schema-dir" -> schemaDir = Path.of(value);
                case "--work-dir" -> workDir = Path.of(value);
                case "--dist-dir" -> distDir = Path.of(value);
                case "--fixture" -> fixture = Path.of(value);
                case "--provider" -> providerCommand = Path.of(value);
                case "--run-store" -> runStore = Path.of(value);
                case "--relationship-edition" -> relationshipEdition = Path.of(value);
                case "--clock" -> clock = value;
                case "--context" -> context = value;
                case "--language" -> language = value;
                case "--profile" -> profile = value;
                case "--repository-commit" -> repositoryCommit = value;
                case "--catalog-limit" -> catalogLimit = positiveInt(value, "--catalog-limit", 1, 100000);
                case "--timeout-seconds" -> timeoutSeconds = positiveInt(value, "--timeout-seconds", 1, 86400);
                default -> throw new IllegalArgumentException("Unknown option: " + token);
            }
        }
        if (fixture != null && providerCommand != null) {
            throw new IllegalArgumentException("--fixture and --provider are mutually exclusive; a manufacture has one evidence source.");
        }
        return new Options(List.copyOf(positionals), registry, schemaDir, workDir, distDir, fixture, providerCommand,
                runStore, context, language, profile, repositoryCommit, clock, catalogLimit, timeoutSeconds,
                register, json, relationshipEdition);
    }

    /**
     * The timestamp a run record is stamped with. Explicit when supplied so a test is reproducible,
     * wall clock otherwise. Run records are operational evidence, so a real clock is correct in
     * production; determinism is what tests need, not what operators need.
     */
    String clock() {
        return explicitClock != null ? explicitClock : java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString();
    }

    String single(String message) {
        if (positionals.size() != 1) throw new IllegalArgumentException(message);
        return positionals.getFirst();
    }

    private static int positiveInt(String value, String option, int min, int max) {
        int parsed;
        try { parsed = Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(option + " must be an integer."); }
        if (parsed < min || parsed > max) throw new IllegalArgumentException(option + " must be between " + min + " and " + max + ".");
        return parsed;
    }
}
