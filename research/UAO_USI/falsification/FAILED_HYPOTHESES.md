# Failed and Untestable Hypotheses

**Status:** RESEARCH / NON-AUTHORITATIVE
**Phase:** 10
**Data:** `temp/benchmark/lane_analysis.json`, `pid_lanes.jsonl`, `cumulative_identity.json`

Negative results are recorded here first and in full, because the programme's deciding question is
*what measurable capability does persistent identity add* — and the answer, on this benchmark, is
mostly "none yet".

## Headline

| lane | n | task success | relevant-file recall | median prompt tokens |
|---|---:|---:|---:|---:|
| (reference) whole repository | 12 | 0.75 | 1.00 | 3317 |
| **A** similarity retrieval | 12 | 0.58 | 0.53 | 581 |
| **B** relational extraction | 12 | **0.75** | 0.81 | 1211 |
| **C** relational + persistent identity | 12 | **0.75** | 0.81 | 1656 |
| **D** + identity provenance | 12 | **0.75** | 0.81 | 1888 |
| **E** + negative space | 12 | 0.83 | 0.81 | 2178 |

6 tasks × 2 models × default reasoning effort, identical across every lane.
**Noise floor: one run = 0.083 success.**

> **B, C and D score identically.** Adding persistent identity, and then provenance on top of it,
> changed task success by exactly zero while increasing prompt tokens by 37% and 56%.

## H5 — CONTRADICTED

> *Persistent identity reduces active context requirements.*

It increases them. B 1211 → C 1656 → D 1888 → E 2178 tokens, monotonically, over an identical file
set. Identity, provenance and absence material are things you *add* to a context.

The hypothesis presumed that a stable referent would let you send a pointer instead of content.
Nothing in the current design does that: identity is supplied *alongside* the files, not instead of
them. A design where the model could resolve a uid on demand — rather than being handed everything
up front — is a different architecture, and is not what was built or measured.

## H7 — NOT REFUTED

> *Persistent identity provides no material improvement beyond good relationship extraction.*

On task success, H7 stands. Lane B already extracts relations mechanically and predicts relevant
files; layering persistent identity and provenance on top moved nothing. Lane E is one run higher,
which is at the noise floor and separately confounded (below).

This is the programme's most important result and must not be softened. The honest summary is:
**good relationship extraction was already doing the work.**

## H1, H2 — NOT TESTABLE (blocked upstream, not disproved)

> *H1: reduces repeated relationship reconstruction. H2: improves relationship precision.*

A persistent relationship graph **cannot be built at all**. Any package carrying a relationship
candidate is `EVIDENCE_INCOMPLETE` under ASA#29 and is refused registry admission, so relationship
bindings never reach the index and no later session has anything to reuse.

Reporting these as "no effect" would be a finding the experiment did not earn. They are blocked by
missing upstream authority, and no Foundry work removes the blockage. See
`../integration/ASA_INTERFACE.md §2`.

## H4 — MEASURED, HEAVILY CONFOUNDED

> *Persistent identity improves negative-space reasoning.*

| lane | n | correct | accuracy |
|---|---:|---:|---:|
| A | 10 | 6 | 0.60 |
| B | 10 | 7 | 0.70 |
| C | 10 | 8 | 0.80 |
| D | 10 | 8 | 0.80 |
| E | 10 | 9 | 0.90 |

Monotone, and the largest apparent effect anywhere in the study. **Do not believe it yet.**

Lane E supplies repo-derived expected-versus-observed records. On a task whose correct answer *is*
the missing artefact — T03 asks which module lacks a test, and the record says
`tests/test_report.py … MISSING` — that record is close to an oracle for that task. The firewall
holds in the sense that `classify_ns()` never sees `task.json` (verified in
`pipeline_static.py:215–229`), so this is not leakage of the answer key. But it is a computation
that happens to answer the question, which is a different and weaker claim than "better reasoning".

Treat 0.90 as an **upper bound**. A fair test needs tasks where the absence is relevant but is not
itself the answer.

## H6 — PARTIALLY MEASURED, WEAK PROXY

> *Persistent identity improves provenance tracing.*

Lane D scores identically to B and C. But the benchmark's ten tasks were never written to require
provenance tracing, so task success is a poor proxy. The provenance surface demonstrably exists and
is queryable (`registry identity <ref>` returns decision history, occurrences, evidence custody);
whether a model uses it needs tasks designed to ask.

**Not evidence against H6. Evidence that this benchmark cannot test H6.**

## H3 — SUPPORTED, but it is the weakest kind of support

> *Persistent identity reduces duplicate/conflicting entities.*

Six observations of one 19-file codebase produced **19 identities and 0 duplicates**, with every
file addressed once regardless of how many times it was seen.

This is real and it is model-independent. It is also the least interesting thing that could have
been true: it shows the addressing scheme does what it says, not that anything downstream benefits.
A gain in bookkeeping is not yet a gain in capability.

## What would change these verdicts

1. **Close ASA#29** — unblocks H1 and H2 entirely, and is the single highest-value change.
2. **Fix P9-1** (`../../temp/` reports) — the registry currently cannot accumulate observations
   past the third, which caps H1 even if ASA#29 closed.
3. **Tasks that require provenance** — H6 is untestable, not unsupported.
4. **Absence-relevant tasks whose answer is not the absence** — de-confounds H4.
5. **On-demand identity resolution** — would give H5 a mechanism it currently lacks.
