package org.seventeenthsecond.usifoundry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.seventeenthsecond.uaofoundry.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The local application server.
 *
 * <p>Built on the JDK's own {@code com.sun.net.httpserver}, which keeps the Foundry's
 * <b>zero runtime dependencies</b> posture intact. That posture is a security property of the
 * audited core, and adding a web framework to serve four screens would trade it for nothing.
 *
 * <h2>Local only</h2>
 *
 * The listener binds to the <b>loopback address</b> and refuses any other. There is no
 * authentication, because there is no remote access: a manufacturing tool that binds to every
 * interface with no auth hands its registry to the network, and "local-first" has to mean the
 * socket, not just the storage.
 *
 * <p>A request whose {@code Origin} is not this server's own origin is rejected, so a page served
 * from somewhere else cannot drive the local API behind the operator's back. The check compares the
 * value rather than merely noting the header's presence: browsers send {@code Origin} on same-origin
 * POSTs too, and rejecting those would stop the application calling its own API.
 */
public final class UsiApiServer {
    private static final int MAX_BODY_BYTES = 1 << 20;

    private final UsiFoundryService service;
    private final HttpServer server;
    private final ExecutorService workers;
    private final Map<String,ManufactureJob> jobs = new ConcurrentHashMap<>();

    public UsiApiServer(UsiFoundryService service, int port) throws IOException {
        this.service = service;
        this.workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "usi-foundry");
            thread.setDaemon(true);
            return thread;
        });
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.server.setExecutor(workers);

        this.server.createContext("/api/", this::routeApi);
        this.server.createContext("/", this::routeStatic);
    }

    public void start() { server.start(); }

    public void stop() {
        server.stop(0);
        workers.shutdownNow();
    }

    public int port() { return server.getAddress().getPort(); }

    public String address() { return "http://127.0.0.1:" + port() + "/"; }

    // ------------------------------------------------------------------ API routing

    private void routeApi(HttpExchange exchange) throws IOException {
        try {
            if (!originAllowed(exchange)) {
                // A browser sends Origin on same-origin POSTs too, so the check must compare the
                // value rather than merely note its presence -- otherwise the application cannot
                // call its own API. What must be refused is a page served from somewhere else
                // driving this local API on the operator's behalf.
                respond(exchange, 403, Map.of("error", "FORBIDDEN",
                        "message", "Cross-origin requests are refused; this API is local only."));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/status") && method.equals("GET")) {
                respond(exchange, 200, service.status());
            } else if (path.equals("/api/manufacture") && method.equals("POST")) {
                respond(exchange, 202, startManufacture(readBody(exchange)));
            } else if (path.startsWith("/api/manufacture/") && method.equals("GET")) {
                respond(exchange, 200, jobStatus(segment(path, "/api/manufacture/")));
            } else if (path.equals("/api/registry/search") && method.equals("GET")) {
                String query = queryParam(exchange, "q");
                if (query == null || query.isBlank()) {
                    throw new UsiFoundryException(UsiFoundryException.INVALID_INPUT, "A search query is required.");
                }
                respond(exchange, 200, service.search(query));
            } else if (path.equals("/api/registry/verify") && method.equals("POST")) {
                respond(exchange, 200, service.verifyRegistry());
            } else if (path.startsWith("/api/identity/") && method.equals("GET")) {
                respond(exchange, 200, service.identity(segment(path, "/api/identity/")));
            } else if (path.startsWith("/api/staged-relationships/") && method.equals("GET")) {
                respond(exchange, 200, service.stagedRelationships(segment(path, "/api/staged-relationships/")));
            } else if (path.startsWith("/api/significance/") && method.equals("GET")) {
                respond(exchange, 200, service.significanceInputs(segment(path, "/api/significance/")));
            } else if (path.startsWith("/api/package/") && method.equals("GET")) {
                respond(exchange, 200, service.packageDetail(segment(path, "/api/package/")));
            } else if (path.equals("/api/runs") && method.equals("GET")) {
                respond(exchange, 200, service.runs(50));
            } else {
                respond(exchange, 404, Map.of("error", "NOT_FOUND", "message", "No such endpoint: " + method + " " + path));
            }
        } catch (UsiFoundryException ex) {
            respond(exchange, statusFor(ex.code()), ex.toMap());
        } catch (IllegalArgumentException ex) {
            UsiFoundryException classified = UsiFoundryException.classify(ex);
            respond(exchange, statusFor(classified.code()), classified.toMap());
        } catch (RuntimeException ex) {
            respond(exchange, 500, Map.of("error", UsiFoundryException.UNCLASSIFIED,
                    "message", String.valueOf(ex.getMessage())));
        }
    }

    /** Same-origin requests are permitted; anything from another origin is not. */
    private boolean originAllowed(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;                 // non-browser client, or a same-origin GET
        int actualPort = server.getAddress().getPort();
        for (String host : new String[]{"127.0.0.1", "localhost", "[::1]"}) {
            if (origin.equals("http://" + host + ":" + actualPort)) return true;
        }
        return false;
    }

    private static int statusFor(String code) {
        return switch (code) {
            case UsiFoundryException.NOT_FOUND -> 404;
            case UsiFoundryException.INVALID_INPUT, UsiFoundryException.CONFIGURATION_ERROR -> 400;
            case UsiFoundryException.UNCLASSIFIED -> 500;
            default -> 409;
        };
    }

    // ------------------------------------------------------------------ manufacture jobs

    /**
     * A manufacture in flight. Held in memory only: the durable record of what happened is the run
     * record on disk, not this.
     */
    private static final class ManufactureJob {
        final Path workDir;
        final String identity;
        final long startedAtMillis = System.currentTimeMillis();
        volatile Map<String,Object> result;
        volatile Map<String,Object> failure;
        ManufactureJob(Path workDir, String identity) { this.workDir = workDir; this.identity = identity; }
    }

    private Map<String,Object> startManufacture(Map<String,Object> body) {
        String identity = string(body.get("identity"));
        if (identity == null) {
            throw new UsiFoundryException(UsiFoundryException.INVALID_INPUT, "An identity or topic is required.");
        }
        String provider = string(body.get("provider"));
        String fixture = string(body.get("fixture"));
        boolean register = !Boolean.FALSE.equals(body.get("register"));

        if ("fixture".equals(provider) && fixture == null) {
            throw new UsiFoundryException(UsiFoundryException.INVALID_INPUT,
                    "Fixture manufacture requires a fixture bundle path.");
        }

        String jobToken = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path workDir = service.config().cache().resolve("jobs").resolve(jobToken);
        ManufactureJob job = new ManufactureJob(workDir, identity);
        jobs.put(jobToken, job);

        UsiFoundryService.ManufactureRequest request = new UsiFoundryService.ManufactureRequest(
                identity, string(body.get("context")), provider,
                fixture == null ? null : Path.of(fixture), register);

        workers.submit(() -> {
            try {
                job.result = service.manufacture(request, workDir);
            } catch (UsiFoundryException ex) {
                job.failure = ex.toMap();
            } catch (RuntimeException ex) {
                job.failure = UsiFoundryException.classify(ex).toMap();
            }
        });

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("jobToken", jobToken);
        out.put("identity", identity);
        out.put("state", "RUNNING");
        return out;
    }

    /**
     * Real progress. The stage list and its statuses come from the checkpoint the pipeline writes
     * as it goes, so a stage reads COMPLETE only because the core recorded it.
     */
    private Map<String,Object> jobStatus(String jobToken) {
        ManufactureJob job = jobs.get(jobToken);
        if (job == null) throw new UsiFoundryException(UsiFoundryException.NOT_FOUND, "No such job: " + jobToken);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("jobToken", jobToken);
        out.put("identity", job.identity);
        out.put("elapsedSeconds", java.math.BigDecimal.valueOf(
                (System.currentTimeMillis() - job.startedAtMillis) / 1000));
        if (job.failure != null) {
            out.put("state", "FAILED");
            out.put("failure", job.failure);
        } else if (job.result != null) {
            out.put("state", "COMPLETE");
            out.put("result", job.result);
        } else {
            out.put("state", "RUNNING");
        }
        out.putAll(service.stageProgress(job.workDir));
        return out;
    }

    // ------------------------------------------------------------------ static assets

    private void routeStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) {
            respond(exchange, 400, Map.of("error", "INVALID_INPUT", "message", "Bad path."));
            return;
        }
        String resource = "/app" + path;
        try (InputStream stream = UsiApiServer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                respond(exchange, 404, Map.of("error", "NOT_FOUND", "message", "No such resource: " + path));
                return;
            }
            byte[] body = stream.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            // The UI loads only its own inlined assets; a strict policy makes that enforceable.
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ helpers

    private Map<String,Object> readBody(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES);
        if (raw.length == 0) return Map.of();
        return Json.object(Json.parse(new String(raw, StandardCharsets.UTF_8)), "request body");
    }

    private static void respond(HttpExchange exchange, int status, Map<String,Object> body) throws IOException {
        byte[] payload = Json.canonical(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(payload); }
    }

    private static String segment(String path, String prefix) {
        String value = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
        if (value.isBlank()) throw new UsiFoundryException(UsiFoundryException.INVALID_INPUT, "A reference is required.");
        return value;
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int split = pair.indexOf('=');
            if (split > 0 && pair.substring(0, split).equals(name)) {
                return URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /** Convenience for embedding: resolves the packaged UI so a missing build fails loudly. */
    static void requireUiPresent() {
        if (UsiApiServer.class.getResource("/app/index.html") == null) {
            throw new IllegalStateException("The USI Foundry UI is not on the classpath. "
                    + "Build with 'mvn package' so app/frontend is bundled.");
        }
    }

    static boolean uiPresent() {
        return UsiApiServer.class.getResource("/app/index.html") != null;
    }

    static Path uiSourceHint() { return Path.of("app", "frontend"); }

    static boolean directoryExists(Path path) { return Files.isDirectory(path); }
}
