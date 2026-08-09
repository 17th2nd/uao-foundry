package org.seventeenthsecond.uaofoundry;

import org.seventeenthsecond.uaofoundry.cli.Arguments;
import org.seventeenthsecond.uaofoundry.json.JsonOutput;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FoundryApplication {
    private static final String FOUNDRY_VERSION = "0.1.0-SNAPSHOT";

    private final PrintStream out;
    private final PrintStream err;

    public FoundryApplication() {
        this(System.out, System.err);
    }

    FoundryApplication(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        int exitCode = new FoundryApplication().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 2;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (command) {
            case "manufacture" -> manufacture(commandArgs);
            case "interpret", "status", "resume", "verify", "inspect" -> foundationLifecycle(command);
            case "help", "--help", "-h" -> {
                printUsage();
                yield 0;
            }
            default -> {
                err.println("Unknown command: " + command);
                printUsage();
                yield 2;
            }
        };
    }

    private int manufacture(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            ManufacturingRequest request = new ManufacturingRequest(
                    "0.1.0",
                    parsed.required("--identity"),
                    parsed.optional("--language").orElse("en"),
                    parsed.optional("--profile").orElse("default")
            );

            Map<String, String> response = new LinkedHashMap<>();
            response.put("foundryVersion", FOUNDRY_VERSION);
            response.put("phase", "REQUEST_ACCEPTED");
            response.put("publicationStatus", "NOT_PUBLISHED");
            response.put("schemaVersion", request.schemaVersion());
            response.put("identitySeed", request.identitySeed());
            response.put("language", request.language());
            response.put("manufacturingProfile", request.manufacturingProfile());

            out.println(JsonOutput.object(response));
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }
    }

    private int foundationLifecycle(String command) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("foundryVersion", FOUNDRY_VERSION);
        response.put("command", command);
        response.put("implementationStatus", "FOUNDATION_ONLY");
        response.put("publicationStatus", "NOT_PUBLISHED");
        out.println(JsonOutput.object(response));
        return 0;
    }

    private void printUsage() {
        out.println("UAO Foundry " + FOUNDRY_VERSION);
        out.println("Usage:");
        out.println("  uao-foundry manufacture --identity <seed> [--language <tag>] [--profile <name>]");
        out.println("  uao-foundry interpret|status|resume|verify|inspect");
    }
}
