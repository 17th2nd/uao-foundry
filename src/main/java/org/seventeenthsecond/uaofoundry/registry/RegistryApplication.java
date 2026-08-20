package org.seventeenthsecond.uaofoundry.registry;

import org.seventeenthsecond.uaofoundry.identity.IdentityOperation;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.json.Json;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone registry CLI kept separate from manufacture until registry invariants are accepted. */
public final class RegistryApplication {
    private final PrintStream out;
    private final PrintStream err;

    public RegistryApplication() { this(System.out, System.err); }
    RegistryApplication(PrintStream out, PrintStream err) { this.out = out; this.err = err; }

    public static void main(String[] args) {
        int exit = new RegistryApplication().run(args);
        if (exit != 0) System.exit(exit);
    }

    int run(String[] args) {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0])) {
            usage(); return args.length == 0 ? 2 : 0;
        }
        try {
            String command = args[0];
            Parsed parsed = parse(java.util.Arrays.copyOfRange(args, 1, args.length));
            FoundryRegistry registry = new FoundryRegistry(parsed.registry(), parsed.schemaDir());
            return switch (command) {
                case "register" -> register(registry, parsed);
                case "search" -> search(registry, parsed);
                case "identity" -> identity(registry, parsed);
                case "supersede" -> operation(registry, parsed, IdentityOperation.Kind.SUPERSEDE);
                case "retire" -> operation(registry, parsed, IdentityOperation.Kind.RETIRE);
                case "merge" -> operation(registry, parsed, IdentityOperation.Kind.MERGE);
                case "split" -> operation(registry, parsed, IdentityOperation.Kind.SPLIT);
                case "operations" -> operations(registry, parsed);
                case "list" -> list(registry, parsed);
                case "verify" -> verify(registry, parsed);
                case "context" -> context(registry, parsed);
                case "rebuild" -> rebuild(registry, parsed);
                default -> throw new IllegalArgumentException("Unknown registry command: " + command);
            };
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage()); return 2;
        }
    }

    private int register(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 1, "register requires exactly one manufactured package path.");
        out.println(Json.canonical(registry.register(Path.of(parsed.positionals().getFirst())).toMap()));
        return 0;
    }

    private int search(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 1, "search requires exactly one query.");
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("query", parsed.positionals().getFirst());
        response.put("matches", registry.search(parsed.positionals().getFirst()));
        out.println(Json.canonical(response));
        return 0;
    }

    /**
     * Exact persistent-identity lookup, as opposed to {@code search}'s discovery ranking.
     *
     * <p>The reference kind is inferred from the argument's shape rather than from a flag: a
     * {@code uao-} address, a canonical resolution key, a {@code scheme:identifier} external
     * identifier, or otherwise an alias. Inference is safe here because an alias can never
     * establish identity anyway, so the worst case of a misread argument is an honest
     * {@code UNRESOLVED}.
     */
    private int identity(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 1, "identity requires exactly one reference (uid, resolution key, scheme:identifier or alias).");
        String value = parsed.positionals().getFirst();
        IdentityReference reference;
        if (value.matches("uao-[a-f0-9]{12}")) {
            reference = IdentityReference.uid(value);
        } else if (value.startsWith("foundry:") || value.startsWith("fixture:") || value.startsWith("ext:")) {
            reference = IdentityReference.resolutionKey(value);
        } else if (value.matches("[a-z][a-z0-9._-]*:\\S+")) {
            int split = value.indexOf(':');
            reference = IdentityReference.externalIdentifier(value.substring(0, split), value.substring(split + 1));
        } else {
            reference = IdentityReference.alias(value);
        }
        Map<String,Object> record = registry.identityRecord(reference);
        out.println(Json.canonical(record));
        // A considered "not well enough" is a distinct outcome from success and from failure.
        return "SAME".equals(object(record.get("resolution")).get("decision")) ? 0 : 4;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) { return (Map<String,Object>) value; }

    /**
     * Records an identity lifecycle operation.
     *
     * <p>{@code --reason} and {@code --justification} are mandatory rather than convenient: an
     * identity operation without a stated reason is indistinguishable from a mistake once its
     * author has moved on. {@code --recorded-at} is likewise explicit rather than defaulted to the
     * wall clock, so a recorded operation is reproducible and testable.
     */
    private int operation(FoundryRegistry registry, Parsed parsed, IdentityOperation.Kind kind) {
        List<String> subjects = parsed.subjects();
        List<String> targets = parsed.targets();
        if (subjects.isEmpty()) throw new IllegalArgumentException(kind + " requires at least one --subject.");
        if (parsed.reasonCodes().isEmpty()) throw new IllegalArgumentException(kind + " requires at least one --reason.");
        if (parsed.justification() == null) throw new IllegalArgumentException(kind + " requires --justification.");
        if (parsed.recordedAt() == null) throw new IllegalArgumentException(kind + " requires --recorded-at (an explicit timestamp, not the wall clock).");
        requirePositionals(parsed, 0, kind + " takes no positional arguments; use --subject and --target.");

        IdentityOperation operation = IdentityOperation.create(kind, subjects, targets, parsed.reasonCodes(),
                parsed.justification(), List.of(), parsed.authority(), parsed.recordedAt());
        out.println(Json.canonical(registry.applyIdentityOperation(operation).toMap()));
        return 0;
    }

    private int operations(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 0, "operations accepts no positional arguments.");
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("identityOperations", registry.index().get("identityOperations"));
        out.println(Json.canonical(response));
        return 0;
    }

    private int list(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 0, "list accepts no positional arguments.");
        out.println(Json.canonical(registry.index())); return 0;
    }

    private int verify(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 0, "verify accepts no positional arguments.");
        FoundryRegistry.VerificationResult result = registry.verify();
        out.println(Json.canonical(result.toMap())); return result.passed() ? 0 : 5;
    }

    private int context(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 1, "context requires exactly one query.");
        out.println(Json.canonical(registry.discoveryContext(parsed.positionals().getFirst(), parsed.catalogLimit()))); return 0;
    }

    private int rebuild(FoundryRegistry registry, Parsed parsed) {
        requirePositionals(parsed, 0, "rebuild accepts no positional arguments.");
        out.println(Json.canonical(registry.rebuildAndPersist())); return 0;
    }

    private Parsed parse(String[] args) {
        Path registry = Path.of(".uao-registry");
        Path schemaDir = Path.of("schemas");
        int catalogLimit = 5000;
        java.util.ArrayList<String> positionals = new java.util.ArrayList<>();
        java.util.ArrayList<String> subjects = new java.util.ArrayList<>();
        java.util.ArrayList<String> targets = new java.util.ArrayList<>();
        java.util.ArrayList<String> reasonCodes = new java.util.ArrayList<>();
        String justification = null;
        String authority = null;
        String recordedAt = null;
        for (int i=0;i<args.length;i++) {
            String token = args[i];
            if (!token.startsWith("--")) { positionals.add(token); continue; }
            if (i+1 >= args.length || args[i+1].startsWith("--")) throw new IllegalArgumentException("Option requires a value: " + token);
            String value = args[++i];
            switch (token) {
                case "--registry" -> registry = Path.of(value);
                case "--schema-dir" -> schemaDir = Path.of(value);
                case "--catalog-limit" -> {
                    try { catalogLimit = Integer.parseInt(value); }
                    catch (NumberFormatException ex) { throw new IllegalArgumentException("--catalog-limit must be an integer."); }
                    if (catalogLimit < 1 || catalogLimit > 100000) throw new IllegalArgumentException("--catalog-limit must be between 1 and 100000.");
                }
                case "--subject" -> subjects.add(value);
                case "--target" -> targets.add(value);
                case "--reason" -> reasonCodes.add(value);
                case "--justification" -> justification = value;
                case "--authority" -> authority = value;
                case "--recorded-at" -> recordedAt = value;
                default -> throw new IllegalArgumentException("Unknown registry option: " + token);
            }
        }
        return new Parsed(registry, schemaDir, catalogLimit, List.copyOf(positionals),
                List.copyOf(subjects), List.copyOf(targets), List.copyOf(reasonCodes), justification, authority, recordedAt);
    }

    private static void requirePositionals(Parsed parsed, int count, String message) {
        if (parsed.positionals().size() != count) throw new IllegalArgumentException(message);
    }

    private void usage() {
        out.println("UAO Foundry Registry " + FoundryRegistry.REGISTRY_VERSION);
        out.println("Usage:");
        out.println("  java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication register <package> [--registry .uao-registry]");
        out.println("  ... RegistryApplication search <query> [--registry .uao-registry]");
        out.println("  ... RegistryApplication identity <uid|resolution-key|scheme:identifier|alias>");
        out.println("  ... RegistryApplication context <query> [--catalog-limit 5000]");
        out.println("  ... RegistryApplication list");
        out.println("  ... RegistryApplication verify");
        out.println("  ... RegistryApplication rebuild");
        out.println("  ... RegistryApplication supersede --subject <uid> --target <uid> --reason <CODE> --justification <text> --recorded-at <iso8601>");
        out.println("  ... RegistryApplication retire    --subject <uid> --reason <CODE> --justification <text> --recorded-at <iso8601>");
        out.println("  ... RegistryApplication merge     --subject <uid> --subject <uid> --target <uid> --reason <CODE> --justification <text> --recorded-at <iso8601>");
        out.println("  ... RegistryApplication split     --subject <uid> --target <uid> --target <uid> --reason <CODE> --justification <text> --recorded-at <iso8601>");
        out.println("  ... RegistryApplication operations");
    }

    private record Parsed(Path registry, Path schemaDir, int catalogLimit, List<String> positionals,
                          List<String> subjects, List<String> targets, List<String> reasonCodes,
                          String justification, String authority, String recordedAt) {}
}
