package org.seventeenthsecond.uaofoundry.runs;

import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Append-preserving store for manufacturing run evidence.
 *
 * <p>Deliberately a <b>sibling</b> of the registry rather than a directory inside it. The registry
 * index is fully derived from {@code packages/} and {@code identity-operations/} and is verified by
 * rebuild-and-compare; run records must never be able to influence it. Keeping them outside the
 * registry root makes that structural rather than a rule someone has to remember.
 *
 * <p>Records are content-addressed, so re-recording an identical run is idempotent and recording
 * different content under an existing id is refused. A completed run is never edited — a
 * correction appends a new record carrying {@code supersedesRunId}.
 */
public final class RunStore {
    public static final String STORE_VERSION = "0.1.0";

    private final Path root;

    public RunStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** The conventional run store for a registry: a {@code runs} directory beside it. */
    public static RunStore besideRegistry(Path registryRoot) {
        Path registry = registryRoot.toAbsolutePath().normalize();
        Path parent = registry.getParent();
        if (parent == null) throw new IllegalArgumentException("Registry path has no parent to place a run store beside: " + registry);
        return new RunStore(parent.resolve("runs"));
    }

    public Path root() { return root; }

    /**
     * Records a run. Idempotent for identical content; fail-closed on an id collision with
     * different content, which would mean the content address had been broken.
     *
     * <p>Reference discipline (finding F-R3). Run records carry two kinds of reference with
     * deliberately different obligations:
     * <ul>
     *   <li>{@code supersedesRunId} is <b>MUST_RESOLVE</b>: a correction that claims a local
     *       predecessor must name one that already exists in this store, so a run cannot supersede
     *       a run that was never recorded. This is enforced below.</li>
     *   <li>{@code packageId} is <b>MAY_BE_HISTORICAL</b>: a run may reference a package that is not
     *       (or is no longer) in any registry — e.g. a relationship-bearing package that was refused
     *       admission — so it is recorded as a pointer and not required to resolve.</li>
     * </ul>
     * An unresolvable historical pointer is not an invalid one; only a dangling
     * {@code supersedesRunId} is rejected.
     */
    public RunRecord record(RunRecord run) {
        Path destination = root.resolve(run.runId() + ".json").normalize();
        if (!destination.startsWith(root)) throw new IllegalArgumentException("Run id escapes the run store root.");
        if (run.supersedesRunId() != null && !run.supersedesRunId().isBlank()) {
            Path predecessor = root.resolve(run.supersedesRunId() + ".json").normalize();
            if (!predecessor.startsWith(root) || !Files.isRegularFile(predecessor)) {
                throw new IllegalArgumentException(
                        "Run record supersedes a run that is not in this store: " + run.supersedesRunId());
            }
        }
        if (Files.isRegularFile(destination)) {
            if (!Json.canonical(FileOps.readJson(destination)).equals(Json.canonical(run.toMap()))) {
                throw new IllegalArgumentException("Run id collision with different content: " + run.runId());
            }
            return run;
        }
        FileOps.writeJson(destination, run.toMap());
        return run;
    }

    /** Every stored run, newest first by completion, re-deriving each content address on the way in. */
    public List<RunRecord> list() {
        List<RunRecord> runs = new ArrayList<>();
        if (!Files.isDirectory(root)) return runs;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) continue;
                RunRecord run = RunRecord.fromMap(object(FileOps.readJson(file)));
                if (!name.equals(run.runId() + ".json")) {
                    throw new IllegalArgumentException("Run record file name does not match its content address: " + name);
                }
                runs.add(run);
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Unable to read the run store: " + ex.getMessage(), ex);
        }
        runs.sort(Comparator.comparing(RunRecord::completedAt).reversed().thenComparing(RunRecord::runId));
        return runs;
    }

    public RunRecord get(String runId) {
        Path path = root.resolve(runId + ".json").normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("No such run: " + runId);
        }
        return RunRecord.fromMap(object(FileOps.readJson(path)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("Run record must be an object.");
        return (Map<String,Object>) map;
    }
}
