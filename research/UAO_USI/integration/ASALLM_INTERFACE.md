# ASALLM Benchmark Integration

**Status:** RESEARCH / NON-AUTHORITATIVE
**Phase:** 9
**Harness:** `benchmark/pima/` (in this repository)
**Benchmark:** `~/asallm_empirical` at `e7a2afa` — **read-only, unmodified**

## 1. What was integrated, and what was not touched

The ASALLM empirical workspace already contains a real repository benchmark: an `orbitkit` fixture
repo, ten tasks with mechanical oracles, two local models, a frozen relation extractor, an absence
algebra and an observer predictor.

Two of its context policies already correspond to the programme's baseline lanes:

| Programme lane | Existing policy | Status |
|---|---|---|
| A — similarity retrieval | `SIM` | **unchanged, not re-run, not re-graded** |
| B — relational extraction | `ASA` | **unchanged, not re-run, not re-graded** |

Lanes C, D and E are added by this programme. Lane A/B numbers are read from the frozen
`traces/runs.jsonl`.

### The oracle is imported, not copied

`benchmark/pima/run_lanes.py` loads `run_llm_benchmark.py` by path and calls its `chat()` and
`grade()` directly. A benchmark that grades itself with its own copy of the grader is not
comparable to the baseline it claims to beat, and a copied grader drifts.

### The ASALLM repository is not modified

Verified: `git status` in that repository shows no change to `benchmark/`, `observer/`,
`relationships/`, `negative_space/`, `traces/` or `results/`, and `HEAD` remains `e7a2afa`.

> **Operator error recorded:** the harness was first written into
> `~/asallm_empirical/benchmark/pima/` because the shell working directory had drifted. It was
> moved into this repository and the stray directory removed before any run. Modifying that
> repository was never authorised.

## 2. Lane design — one variable at a time

All three new lanes take **exactly lane B's file set** — the observer's predicted relevant files in
full, everything else as summaries — and vary only what is said *about* those files:

| Lane | Adds |
|---|---|
| `PID_C` | persistent identity per file: uid, address, kind, content identifier, aliases, status |
| `PID_D` | C + provenance: occurrence count, distinct states, identity basis, evidence custody |
| `PID_E` | D + repo-derived expected-versus-observed records with their observation scope |

Holding retrieval constant is what makes any difference attributable to persistent identity rather
than to having found better files.

## 3. The registry is built from the repository alone

`benchmark/pima/build_repo_registry.py` manufactures one UAO identity per repository file:

```
resolutionKey       foundry:v0.1:file:<path-slug>     the address
externalIdentifiers {"sha256": <content hash>}        durable, content-issued
aliases             [<basename>]                      a hint; can never establish identity
```

**`task.json` is never read.** The ground-truth firewall the ASALLM pipeline documents
(`pipeline_static.py` lines 9–10) is preserved: a registry that had seen the answers would make
every downstream lane worthless.

The content hash is the durable external identifier because, unlike a path, it is issued by the
content itself. The path is the address; the hash is the evidence. That choice has a consequence
measured in `../experiments/EXPERIMENT_REGISTER.md`.

## 4. Structural limit discovered during integration

**A persistent relationship graph cannot be built at all.**

Any package carrying a relationship candidate is `EVIDENCE_INCOMPLETE` under ASA#29 and is refused
registry admission. Relationship bindings therefore never reach the index, and there is nothing for
a later session to reuse.

The registry bundles are consequently emitted with empty relationship candidate sets — not to make
the demonstration easier, but because the alternative is a registry that cannot be built.

This is what makes H1 and H2 **not testable**, rather than disproved. It is an upstream authority
blockage, and no amount of Foundry work removes it.
