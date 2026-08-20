# H1 / H2 Re-run over Staged Relationship Memory — Pre-registration

**Status:** RESEARCH / NON-AUTHORITATIVE · registered **before** any experiment code was written
or run
**Date:** 2026-08-21
**Prerequisites landed:** P9-1 resolved (ADR-0006, Q1); §18 staged relationship store (commit
`f1c9c50`)

## What changed since Phase 10

Phase 10 recorded H1 and H2 as **NOT TESTABLE**: relationship-bearing packages are inadmissible
under ASA#29, so relationship bindings never accumulated (`FAILED_HYPOTHESES.md`), and P9-1
independently capped accumulation past the third observation (`IDENTITY_ERRORS.md`). Both blockers
are now different: P9-1 is fixed, and the §18 staging store accumulates identity-bound candidates
as **non-canonical memory**.

## Honest scope limitation, stated first

This re-run measures hypotheses over **staged candidate memory**, not over a governed relationship
graph. ASA#29 remains open; nothing here closes, works around, or prejudges it. If H1/H2 are
supported here, the supported claim is "persistent *candidate* relationship memory does X", and
canonical-authority versions of the hypotheses remain open upstream. If they are not supported,
that is reportable as a genuine negative for the staged-memory design.

## Experiments

### E-6 (H1) — repeated relationship reconstruction, mechanical

*H1: persistent relationship memory reduces repeated relationship reconstruction.*

Session 1, per task repository (T01, T03, T04, T06, T07, T08): a deterministic extractor derives
file-reference edges (Python `import`/`from` statements resolved to repository files; Markdown
references to repository paths — repository content only, `task.json` never read). Each edge is
manufactured through the **running USI Foundry application** as a relationship-bearing fixture
bundle: refused admission, staged by the application, exactly the shipped operator flow.

Session 2 answers, for every file in every repo, "which files does this file reference / which
reference it" two ways:

- **reconstruction** — fresh extraction: re-read repository content, re-derive edges;
- **memory** — read the staged store's neighbourhoods; repository content not touched.

Measured: exact edge-set equality; bytes of repository content read by each path; wall time per
query path; store survival across process restart (list() re-validation included); accumulation
depth (each repo staged twice more to prove observations accumulate past the third without any
P9-1-class refusal).

**Decision rule (all four limbs required for SUPPORTED):**

1. memory-derived edge sets are exactly equal to fresh-extraction edge sets on every task repo;
2. the memory path reads 0 bytes of repository content;
3. the store yields identical answers after restart;
4. ≥3 staged observations per repo accumulate with 0 unintended refusals.

Any failed limb ⇒ NOT SUPPORTED, reported with the failing limb. Note limb 1+2 make H1's
"reduction" structural (re-reading nothing is strictly less than re-reading everything); wall
time is reported but is **not** a decision criterion at these repo sizes.

### E-7 (H2) — relationship precision, mechanical + LLM lane

*H2: persistent relationship memory improves relationship precision.*

**Mechanical:** for each task, a graph-neighbourhood file predictor: seed = files whose names
appear in the task prompt (the same text every lane's model sees); prediction = seeds plus their
staged-graph neighbours. Compare precision/recall against `task.json` `relevant_files`
(ground truth used at grading time only, exactly as the frozen lanes use it) with the frozen
predictions lane B used (`observer/<task>_predictions.json`) scored identically.

**LLM lane `PID_F`:** lane B's exact file set (held constant, as C/D/E did), plus a staged
relationship block (edges with persistent uids, binding status, evidence refs, originating
package) — and **no identity/provenance/negative-space blocks**, so F−B isolates relationship
memory the way C−B isolated identity. 6 tasks × 2 models (`qwen3-coder:30b`, `gpt-oss:20b`),
temperature 0, seed 20260820, frozen runner's `chat()` and `grade()` imported verbatim, lanes
A/B/C/D/E read from the frozen trace and **not re-run**.

**Decision rules:**

- Mechanical: graph predictor recall/precision vs lane B's frozen selection, reported exactly.
  H2-mechanical SUPPORTED only if the graph predictor is ≥ B on recall without lower precision.
- LLM: n=12 per lane; noise floor one run = 0.083 success. |Δ success(F−B)| ≤ 0.083 ⇒ reported
  as **no effect**. Token cost reported alongside, as for C/D/E.

## Registered prior

Phase 10's headline was that B = C = D: good relationship extraction was already doing the work.
The registered expectation is therefore **no effect on task success** (F ≈ B) and a token cost
increase, with the mechanical E-6 limbs expected to pass. If exactly that happens, the honest
summary is: *staged memory makes relationship knowledge durable and cheap to reproduce, and
(still) does not make models answer better on this benchmark.* Anything stronger must be earned
by the numbers.

## Controls carried over from E-3

- Frozen ASALLM workspace `~/asallm_empirical` at `e7a2afa`, byte-unchanged; oracle imported, not
  copied.
- Registries and application homes are disposable (session scratchpad); nothing persistent is
  populated.
- `task.json` reaches only grading, never the extractor, the stores or the prompts (task prompt
  text excepted, which every lane already receives).
- Frozen lane records are read from `temp/benchmark/pid_lanes.jsonl` and the ASALLM trace; not
  re-run, not re-graded.
- New results are appended to new files; no prior result file is modified.
