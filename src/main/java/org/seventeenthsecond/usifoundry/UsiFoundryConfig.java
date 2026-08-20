package org.seventeenthsecond.usifoundry;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-first application configuration and storage layout.
 *
 * <p>Everything the application writes lives under one governed root, by default
 * {@code ~/.usi-foundry}. Nothing is written into whatever directory the operator happened to
 * launch from: a manufacturing tool that scatters registries across working directories produces
 * registries nobody can find and reuse that silently never happens.
 *
 * <pre>
 * ~/.usi-foundry/
 * ├── registry/   packages/ · identity-operations/ · index.json
 * ├── runs/       run evidence, beside the registry (ADR-0006)
 * ├── cache/      job working directories; safe to delete
 * ├── packages/   manufactured package output
 * ├── config/     config.json
 * └── logs/
 * </pre>
 *
 * <p>Overridable by {@code USI_FOUNDRY_HOME} or an explicit constructor argument, so a disposable
 * registry for a demonstration or a test is one environment variable away.
 */
public final class UsiFoundryConfig {
    public static final String APPLICATION_NAME = "USI Foundry";
    public static final String APPLICATION_VERSION = "0.1.0-alpha";
    public static final String HOME_ENV = "USI_FOUNDRY_HOME";

    private final Path home;
    private final Map<String,Object> settings;

    public UsiFoundryConfig() { this(defaultHome()); }

    public UsiFoundryConfig(Path home) {
        this.home = home.toAbsolutePath().normalize();
        try {
            for (Path directory : new Path[]{registry(), runs(), cache(), packages(), configDir(), logs()}) {
                Files.createDirectories(directory);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to prepare the USI Foundry home at " + this.home + ": " + ex.getMessage(), ex);
        }
        this.settings = loadSettings();
    }

    private static Path defaultHome() {
        String override = System.getenv(HOME_ENV);
        if (override != null && !override.isBlank()) return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".usi-foundry");
    }

    public Path home() { return home; }
    public Path registry() { return home.resolve("registry"); }
    public Path runs() { return home.resolve("runs"); }
    public Path cache() { return home.resolve("cache"); }
    public Path packages() { return home.resolve("packages"); }
    public Path configDir() { return home.resolve("config"); }
    public Path logs() { return home.resolve("logs"); }
    public Path configFile() { return configDir().resolve("config.json"); }

    /**
     * Schema directory. Resolved from the repository checkout when present, because the JSON
     * contracts are validated from disk by the audited core rather than from the classpath.
     */
    public Path schemaDir() {
        String configured = string("schemaDir");
        if (configured != null) return Path.of(configured);
        Path local = Path.of("schemas");
        if (Files.isDirectory(local)) return local.toAbsolutePath().normalize();
        return home.resolve("schemas");
    }

    /** Provider settings (§27). Never a hardcoded permanent architecture; Claude is one option. */
    public String claudeCommand() { return string("claudeCommand"); }
    public int providerTimeoutSeconds() { return integer("providerTimeoutSeconds", 900); }
    public int catalogLimit() { return integer("catalogLimit", 5000); }
    public String defaultLanguage() { String v = string("defaultLanguage"); return v == null ? "en" : v; }
    public String defaultProfile() { String v = string("defaultProfile"); return v == null ? "experimental" : v; }
    public int port() { return integer("port", 7717); }

    public Map<String,Object> describe() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("applicationName", APPLICATION_NAME);
        out.put("applicationVersion", APPLICATION_VERSION);
        out.put("home", home.toString());
        out.put("registry", registry().toString());
        out.put("runs", runs().toString());
        out.put("packages", packages().toString());
        out.put("schemaDir", schemaDir().toString());
        out.put("claudeCommandConfigured", claudeCommand() != null);
        out.put("providerTimeoutSeconds", java.math.BigDecimal.valueOf(providerTimeoutSeconds()));
        return out;
    }

    private Map<String,Object> loadSettings() {
        if (!Files.isRegularFile(configFile())) {
            Map<String,Object> defaults = new LinkedHashMap<>();
            defaults.put("configVersion", "0.1.0");
            defaults.put("providerTimeoutSeconds", java.math.BigDecimal.valueOf(900));
            defaults.put("catalogLimit", java.math.BigDecimal.valueOf(5000));
            defaults.put("defaultLanguage", "en");
            defaults.put("defaultProfile", "experimental");
            defaults.put("port", java.math.BigDecimal.valueOf(7717));
            FileOps.writeJson(configFile(), defaults);
            return defaults;
        }
        return Json.object(FileOps.readJson(configFile()), "config");
    }

    private String string(String key) {
        return settings.get(key) instanceof String s && !s.isBlank() ? s : null;
    }

    private int integer(String key, int fallback) {
        return settings.get(key) instanceof java.math.BigDecimal n ? n.intValue() : fallback;
    }
}
