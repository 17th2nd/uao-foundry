# USI Foundry — H1/H2 Re-run over Staged Memory (E-6 / E-7)

**Run label:** `claude-uaousi-h1h2-rerun-001`
**Programme:** USI Foundry — Manufacturing Application Alpha (post-crash continuation, stage after §18)
**Branch:** `programme/usi-manufacturer-application-alpha`
**Operator:** Claude (lead manufacturing operator)
**Date:** 2026-08-21

## Why this stage

The PIMA handoff's recommended order (§16): resolve P9-1 (done, Q1), land the staged-closure
study path (done, §18), **then re-run the blocked lanes** — the only way to convert H1/H2's
"not testable" into an answer. This run did that, under a pre-registration written before any
experiment code, with the registered prior "no effect on task success".

## Verdicts (decision rules unchanged from pre-registration)

| Hypothesis | Phase 10 | This run |
|---|---|---|
| H1 — reduces repeated relationship reconstruction | NOT TESTABLE | **SUPPORTED** (mechanical, 4/4 limbs) |
| H2 — improves relationship precision | NOT TESTABLE | **NOT SUPPORTED**; LLM lane **negative beyond noise floor** |

The full record, including the two lost runs traced to their mechanisms (a graph-recitation
failure and an oracle-lexicon miss, both on `qwen3-coder:30b`), is in
`research/UAO_USI/falsification/H1_H2_STAGED_RERUN_RESULTS.md`. The result is *worse* than the
registered prior and is reported exactly as found: staged relationship memory is durable and
exactly reproducible (H1), and feeding it to models does not help and can hurt (H2).

## What was run

- **Session 1** — 6 task repos × 3 rounds through the running application: 342 file
  registrations (P9-1 fix holding at depth 3), 216 edge manufactures refused-and-staged under
  ASA#29. **558 manufactures, 0 unintended failures**, all registries verify.
- **E-6** — reconstruction-vs-memory over every repo, restarted process, Java fail-closed read
  path in the loop. `temp/benchmark/staged_h1.json`.
- **E-7** — mechanical staged-graph relevance predictor vs frozen observer predictions, plus LLM
  lane `PID_F` (6 tasks × 2 local models, frozen runner verbatim, temp 0, seed 20260820).
  `temp/benchmark/staged_lanes.jsonl`, `staged_rerun_analysis.json`.

| lane | n | success | recall | median prompt tokens |
|---|---:|---:|---:|---:|
| B relational (frozen) | 12 | 0.75 | 0.806 | 1211 |
| C + identity (frozen) | 12 | 0.75 | 0.806 | 1656 |
| **F + staged relationships** | 12 | **0.583** | 0.806 | 1711 |

## Controls held

Frozen `~/asallm_empirical` at `e7a2afa`, byte-unchanged; A/B/C/D/E not re-run or re-graded;
oracle imported, not copied; stores disposable in the session scratchpad; `task.json` reached
grading only; no prior result file modified (a dated pointer was added under the Phase-10 H1/H2
section, text preserved).

## Engineering consequence recorded

The staging store's demonstrated value is **infrastructure** — durability, provenance, exact
reproducibility, later reuse under governed authority — not prompt payload. Nothing feeds staged
edges into manufacture or reasoning contexts by default, and this run is evidence that nothing
should without a design that survives its measured failure mode (graph recitation).

## New files

`benchmark/staged/{extract_references,build_task_stores,h1_reconstruction,run_lane_f,analyse_staged}.py`,
`research/UAO_USI/falsification/{H1_H2_STAGED_RERUN_PREREGISTRATION,H1_H2_STAGED_RERUN_RESULTS}.md`,
`temp/benchmark/{staged_h1.json,staged_lanes.jsonl,staged_rerun_analysis.json}`, this report.
Experiment register: E-6/E-7 appended.

## Next

Remaining from the handoff's recommended order: provenance/identity-continuity task design (H6
stays untestable on this benchmark), and one authenticated live-provider manufacture into a
disposable registry (field evidence; requires an operator-authenticated Claude session, which
this run does not assume). Both are operator-decision-shaped rather than build-shaped, so this
report closes the autonomous continuation at a natural gate.

**Q-gate discipline unchanged:** awaiting independent audit; Claude does not self-certify.
PR #16 remains the integration surface; not merged.
