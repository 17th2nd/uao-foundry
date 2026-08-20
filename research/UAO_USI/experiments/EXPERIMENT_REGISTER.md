# Experiment Register

**Status:** RESEARCH / NON-AUTHORITATIVE
**Phases:** 9–10

| # | Experiment | Model needed | Result | Where |
|---|---|---|---|---|
| E-1 | Acceptance demonstration: manufacture → register → rediscover → reuse | no | **PASS** — 3 reused, 2 new, domain-independent | `examples/demonstration/README.md` |
| E-2 | Cumulative identity across repeated observation of one codebase | no | 19 identities, **0 duplicates**; 69/114 manufactures blocked by P9-1 | `temp/benchmark/cumulative_identity.json` |
| E-3 | Benchmark lanes C/D/E vs frozen A/B | yes | **B = C = D** on task success | `temp/benchmark/lane_analysis.json` |
| E-4 | Negative-space accuracy by lane | yes | B 0.70 → E 0.90, confounded | same |
| E-5 | Context cost by lane | yes | +37% / +56% / +80% over B | same |
| E-6 | H1 re-run: reconstruction from staged memory (§18) | no | **SUPPORTED** — 4/4 limbs; 558 manufactures, 0 unintended failures | `temp/benchmark/staged_h1.json` |
| E-7 | H2 re-run: staged-graph precision + lane PID_F | yes | **NOT SUPPORTED** — graph ≪ extraction; F −0.167 vs B, beyond noise floor | `temp/benchmark/staged_rerun_analysis.json` |

## E-1 — Acceptance demonstration

Deterministic fixtures, disposable registry. Run 1 manufactures three identities; run 2 reuses all
three and adds two; an unrelated domain reuses nothing. Rediscovery by durable external identifier
resolves `SAME`. The reused identity ends with two occurrences, two identity decisions and **one**
state version.

Demonstrates the programme's success criterion. Says nothing about whether it is *useful*.

## E-2 — Cumulative identity

One registry, six observations of one 19-file codebase (the task repo variants, which differ in 1–2
files each). No model involved: this is a property of the machine.

```
final identities        19
duplicate identities     0
unreconciled identities  0
refusals                77  of 114 attempted
  by P9-1               69   pre-existing packaging defect
  by content-changed     8   intended fail-closed behaviour
```

Supports H3. Also the experiment that surfaced P9-1, which is the more consequential finding.

## E-3 — Benchmark lanes

6 tasks (T01, T03, T04, T06, T07, T08) × 2 local models (`qwen3-coder:30b`, `gpt-oss:20b`),
temperature 0, seed 20260820, default reasoning effort.

**Controls applied:**

- Lanes A and B read from the frozen ASALLM trace; **not re-run, not re-graded, not altered**.
- The oracle is *imported* from the ASALLM runner, not copied.
- Reasoning-effort variants excluded — they exist only for the ASA policy and would have given
  lane B four extra runs.
- One errored baseline run (T03/BRUTE/gpt-oss) excluded rather than scored as incorrect.
- C/D/E take **exactly lane B's file set**, varying only what is said about those files.
- The registry is built from repository content only; `task.json` never reaches it.

| lane | success | recall | tokens |
|---|---:|---:|---:|
| A similarity | 0.58 | 0.53 | 581 |
| B relational | **0.75** | 0.81 | 1211 |
| C + identity | **0.75** | 0.81 | 1656 |
| D + provenance | **0.75** | 0.81 | 1888 |
| E + negative space | 0.83 | 0.81 | 2178 |

Noise floor 0.083 (one run of twelve). Registry lookup median 0.214 s. Human interventions: 0.

## Threats to validity, stated

1. **Small n.** 12 runs per lane. Only a difference of two or more runs is worth discussing, and
   only lane E reaches even one.
2. **Lane E is near-oracular on absence tasks.** See `../falsification/FAILED_HYPOTHESES.md §H4`.
3. **Tasks were not designed for this question.** They test repository reasoning, not identity
   continuity across sessions — which is what persistent identity is *for*. The benchmark can
   refute a strong claim but cannot confirm a weak one.
4. **One codebase, one domain.** Nineteen Python/Markdown files.
5. **Identity is supplied, not resolved on demand.** The lanes hand the model identity material up
   front. An architecture where the model *asks* was not built and is not measured.
6. **No live provider.** All manufacture is deterministic-fixture. The Claude adapter path is
   exercised only by its own CI.

## Not run, and why

- **Live-provider manufacture** — requires an authenticated Claude Code session; §22 directs that
  development not depend on one. Remains a field-evidence step.
- **Cross-model identity sharing (§18)** — the registry is model-agnostic by construction (no
  session, conversation or model material may enter a resolution key, enforced by
  `ResolutionKeys`), but no experiment demonstrated two models resolving the same identity.
- **Cross-modal (§19)** — out of scope for the Alpha; the schema admits any `ext:` scheme, so
  nothing forecloses it.

## E-6 / E-7 — H1/H2 re-run over staged candidate memory (2026-08-21)

Run after P9-1 was resolved and the §18 staging store landed, under a pre-registration written
before any experiment code (`../falsification/H1_H2_STAGED_RERUN_PREREGISTRATION.md`). Session 1
built six per-task registries + staged stores through the running USI Foundry application: 558
manufactures, 0 unintended failures, accumulation depth 3 everywhere.

- **E-6 (H1):** a restarted process answers every reference-graph query from the store, byte-equal
  to fresh extraction, reading 0 repository bytes. SUPPORTED on all four limbs — with the recorded
  caveat that at 19-file scale re-extraction is *faster*; the win is durability, not latency.
- **E-7 (H2):** the staged reference graph predicts relevant files far worse than relational
  extraction (precision 0.417 vs 0.625, recall 0.333 vs 0.806), and lane PID_F (B's file set +
  staged edges) scored 0.583 vs B's 0.75 — negative beyond the noise floor, both lost runs on one
  model, mechanisms recorded. NOT SUPPORTED.

Full record: `../falsification/H1_H2_STAGED_RERUN_RESULTS.md`.
