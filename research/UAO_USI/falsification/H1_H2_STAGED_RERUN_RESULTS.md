# H1 / H2 Re-run over Staged Relationship Memory — Results

**Status:** RESEARCH / NON-AUTHORITATIVE
**Date:** 2026-08-21 · run under `H1_H2_STAGED_RERUN_PREREGISTRATION.md`, decision rules unchanged
**Data:** `temp/benchmark/staged_h1.json`, `staged_lanes.jsonl`, `staged_rerun_analysis.json`
**Substrate caveat:** everything below is about **non-canonical staged candidate memory** (§18).
ASA#29 remains open; no canonical relationship graph exists and none is implied.

## Headline

| Hypothesis | Phase 10 | Now |
|---|---|---|
| **H1** reduces repeated relationship reconstruction | NOT TESTABLE | **SUPPORTED** (mechanical, all four pre-registered limbs) |
| **H2** improves relationship precision | NOT TESTABLE | **NOT SUPPORTED** — mechanical predictor clearly worse; LLM lane **negative beyond the noise floor** |

The registered prior was "no effect on task success". The result is worse than the prior:
supplying staged relationship memory to the models **degraded** task success relative to plain
relational extraction. Recorded as found.

## E-6 — H1, mechanical: SUPPORTED

Session 1 built, through the running USI Foundry application (shipped operator flow, disposable
homes): 6 task repos × 3 rounds × (19 files registered + 12 edges refused-and-staged) —
**558 manufactures, 0 unintended failures**, every registry verifying, every file re-registration
admitted at depth 3 (the depth P9-1 used to cap).

| limb | result |
|---|---|
| 1. memory edge set == fresh extraction edge set, every repo | **true** (12 = 12, all six repos) |
| 2. memory path reads repository content | **0 bytes** (vs 8.1–8.3 KB per fresh pass) |
| 3. identical answers from a restarted process (Java fail-closed read path included) | **true** |
| 4. ≥3 observations per edge, no unintended refusals | **true** (depth exactly 3, min = max) |

**Honest wall-clock note, as pre-registered:** the memory path (~1.1 s: JVM start-up + HTTP) is
*slower* than fresh extraction (~2 ms) at these 19-file repos. Time was excluded from the decision
rule for exactly this reason. What H1 support means here is durability and zero repository
re-access — relationship knowledge survives the process and the repo not being there — not speed.
At toy scale, re-extraction is cheap; whether the balance flips at real scale is unmeasured
(and the registry scale probe already shows linear per-op costs that would dominate first).

## E-7 — H2: NOT SUPPORTED, and negative where it was measurable

### Mechanical predictor

Staged-graph neighbourhood prediction (seeds = files named in the task prompt) vs the frozen
observer prediction lane B used, both scored against `task.json` ground truth:

| predictor | mean precision | mean recall |
|---|---:|---:|
| staged reference graph | 0.417 | 0.333 |
| frozen relational extraction (lane B's) | **0.625** | **0.806** |

The deterministic reference graph — imports and path mentions — is a **much worse** relevance
predictor than relational extraction. Import structure says which files touch, not which files
matter for a given fault. H2-mechanical fails both limbs.

### LLM lane PID_F

Lane B's exact file set + the staged relationship block, nothing else added. 6 tasks × 2 models,
frozen `chat()`/`grade()`, temperature 0, seed 20260820:

| lane | n | success | recall | median prompt tokens |
|---|---:|---:|---:|---:|
| B relational (frozen) | 12 | 0.75 | 0.806 | 1211 |
| C + identity (frozen) | 12 | 0.75 | 0.806 | 1656 |
| **F + staged relationships** | 12 | **0.583** | 0.806 | 1711 |

Δ success (F − B) = **−0.167**, twice the pre-registered noise floor (0.083). Recall is identical
by construction (same file set). Token cost +41% over B — the same "identity material is a thing
you add" pattern H5 already established.

### Where the two lost runs went, exactly

Both losses are `qwen3-coder:30b` (4/6 → 2/6); `gpt-oss:20b` is unchanged (5/6 both lanes).

- **T06** (list files relevant to a pass-prediction fault): with the edge block present, qwen
  listed **seventeen** files — essentially the repository ordered by graph reach — sweeping in
  every oracle distractor. Lane B's answer, same file set and no edge block, was a tight correct
  list. The relationship memory acted as a *distractor amplifier* on a ranking task: the graph
  names many files, and the model recited the graph.
- **T08** (verify a CHANGELOG claim): qwen concluded the claim is "**not supported by
  evidence**" — semantically the same verdict lane B expressed, but containing none of the
  oracle's answer substrings, so graded incorrect. This loss is an oracle-lexicon miss in which
  the added block changed the wording path, not observably the reasoning.

The pre-registered rule counts both; the verdict **stands as negative beyond noise floor**, with
the mechanism inspection recorded so nobody later mistakes n=12 with one affected model for a
robust effect size.

## What this adds to the Phase-10 headline

Phase 10: *good relationship extraction was already doing the work.* This re-run sharpens it:

1. staged memory makes relationship knowledge **durable and exactly reproducible** without
   re-reading the repository (H1, mechanical, clean);
2. handing that memory to a model **does not help and can hurt** — reference-graph structure is
   noise for tasks that relational extraction already serves (H2, both limbs);
3. the failure mode is concrete: graph material invites exhaustive graph recitation.

The engineering implication is that the staged store's value, if any, is as **infrastructure**
(persistence, provenance, later reuse under governed authority) — not as prompt payload. Nothing
here justifies feeding staged edges into manufacture or reasoning contexts by default, and the
Foundry does not do so.

## Controls audit

Frozen workspace `~/asallm_empirical` still at `e7a2afa`, byte-unchanged; lanes A/B/C/D/E read
from frozen records, not re-run; oracle imported, not copied; stores and homes disposable under
the session scratchpad; `task.json` reached grading only; prior result files unmodified — new
results live in new files (`staged_h1.json`, `staged_lanes.jsonl`, `staged_rerun_analysis.json`,
this document).
