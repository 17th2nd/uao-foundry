package org.seventeenthsecond.usifoundry;

import java.nio.file.Path;

/**
 * Application entry point.
 *
 * <p>Starts the local server and prints the address. Deliberately <b>not</b> the jar's main class:
 * {@code java -jar uao-foundry-0.1.0.jar manufacture …} must keep working exactly as it did, so the
 * application is launched explicitly and the audited CLI is untouched.
 */
public final class UsiFoundryApp {

    public static void main(String[] args) throws Exception {
        Path home = null;
        int port = -1;
        boolean open = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--home" -> home = Path.of(require(args, ++i, "--home"));
                case "--port" -> port = Integer.parseInt(require(args, ++i, "--port"));
                case "--no-open" -> open = false;
                case "--help", "-h" -> { usage(); return; }
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        UsiFoundryConfig config = home == null ? new UsiFoundryConfig() : new UsiFoundryConfig(home);
        UsiApiServer.requireUiPresent();
        UsiApiServer server = new UsiApiServer(new UsiFoundryService(config), port > 0 ? port : config.port());
        server.start();

        System.out.println();
        System.out.println("  " + UsiFoundryConfig.APPLICATION_NAME + " " + UsiFoundryConfig.APPLICATION_VERSION);
        System.out.println("  Universal Semantic Identity Factory");
        System.out.println();
        System.out.println("  open      " + server.address());
        System.out.println("  home      " + config.home());
        System.out.println("  registry  " + config.registry());
        System.out.println();
        System.out.println("  Listening on the loopback interface only. Press Ctrl-C to stop.");
        System.out.println();

        if (open) tryOpenBrowser(server.address());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }

    /** Best effort. A headless machine is a normal way to run this, not a failure. */
    private static void tryOpenBrowser(String address) {
        if (System.getenv("DISPLAY") == null && System.getenv("WAYLAND_DISPLAY") == null) return;
        try {
            new ProcessBuilder("xdg-open", address).redirectErrorStream(true).start();
        } catch (Exception ignored) {
            // The address is printed above; that is sufficient.
        }
    }

    private static String require(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Option requires a value: " + option);
        return args[index];
    }

    private static void usage() {
        System.out.println("USI Foundry " + UsiFoundryConfig.APPLICATION_VERSION);
        System.out.println("  java -cp uao-foundry-0.1.0.jar org.seventeenthsecond.usifoundry.UsiFoundryApp \\");
        System.out.println("       [--home <path>] [--port <n>] [--no-open]");
        System.out.println();
        System.out.println("  Default home: ~/.usi-foundry  (override with USI_FOUNDRY_HOME)");
    }
}
