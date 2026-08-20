# UAO vs USI — Evidence-Based Recommendation

**Status:** RESEARCH RECOMMENDATION — not a governance decision
**Phase:** 11
**Governed by:** `docs/ADR-0001-UAO-TERMINOLOGY.md` (Accepted 2026-08-09)

## Recommendation

> **Retain UAO. Do not adopt USI. Do not schedule the rename.**

Not "not yet, pending a clean migration" — that is already ADR-0001's position and would be a
restatement, not a finding. The recommendation here is stronger and rests on measurement: **the
architecture has not yet demonstrated that durable semantic identity is a capability worth its own
name.**

## The deciding question, and its answer

§32 sets the test:

> What measurable capability does persistent identity add to the relational ASA/ASALLM system?

Measured (`../falsification/FAILED_HYPOTHESES.md`):

| lane | task success | prompt tokens |
|---|---:|---:|
| B relational extraction | **0.75** | 1211 |
| C + persistent identity | **0.75** | 1656 |
| D + provenance | **0.75** | 1888 |

**Zero.** Adding persistent identity, then provenance on top of it, changed task success by exactly
nothing while costing 37% and 56% more context. Good relationship extraction was already doing the
work.

That is the answer to the deciding question, and it does not support elevating the concept to a
name of its own.

## What the tests from `../terminology/UAO_VS_USI.md §2` returned

Those five tests were written before any of this was built, to say in advance what would make the
distinction worth adopting.

| # | Test | Result |
|---|---|---|
| 1 | One address, many representations | **PASS** — semantic variants, pre-existing |
| 2 | One address, many modalities | **UNTESTED** — schema admits any `ext:` scheme; nothing built |
| 3 | One address, many models | **UNTESTED** — model-agnostic by construction, never demonstrated |
| 4 | Address survives supersession | **PASS** — Phase 4; the address resolves and reports its fate |
| 5 | Address resolvable without the representation | **PASS** — `identity <ref>` answers from the index alone |

Three of five pass. On the pre-registered criterion that is a respectable result for the
*mechanism* — and it is exactly the situation the criterion was written to distrust, because §32
says the deciding evidence is measurable capability, not mechanism. The mechanism works; nothing
downstream is better for it.

## Why the negative result is not fatal to the idea

Stated plainly so the recommendation is not read as more than it is. Two of the strongest arguments
for persistent identity **could not be tested at all**:

- **H1/H2 are blocked by ASA#29.** A relationship-bearing package is inadmissible, so no persistent
  relationship graph can be accumulated. The claim that persistent identity saves you from
  re-deriving a relational graph cannot even be posed.
- **P9-1 caps accumulation independently.** The registry refuses a third observation of unchanged
  material, so "observe the same codebase repeatedly and get cheaper each time" is untestable for a
  second, unrelated reason.

Persistent identity's case rests on *accumulation over time*. Both mechanisms that would let it
accumulate are broken or blocked. The measurement therefore says **"no demonstrated gain"**, not
**"no gain"** — and a name should follow demonstration, not potential.

## What would justify revisiting

In descending order of value:

1. **ASA#29 closes** and a persistent relationship graph becomes accumulable. Re-run C/D/E; H1 and
   H2 become answerable.
2. **P9-1 is resolved** so repeated observation works.
3. **Tasks that require identity continuity across sessions** — the current benchmark tests
   repository reasoning within a single session, which is not what persistent identity is for.
4. **Cross-model or cross-modal demonstration** — tests 2 and 3 above.

If, after 1–3, lanes C/D still equal lane B, the honest conclusion is that USI names a bookkeeping
convenience and the terminology question is closed for good.

## Implementation-neutral naming held

No rename was performed. New code uses migration-neutral names (`identity`, `resolutionKey`,
`identityDecision`, `lifecycleState`); existing canonical names (`uid`, `uao-…`, `foundry_identity`,
`resolution_key`) are unchanged, as ADR-0001 requires and as ASA CSS pins. A future migration
remains technically possible and is not made harder by anything built here.

## Status

A research recommendation from the operator who built and measured the thing, which is a reason to
weigh it and also a reason to check it. It creates no authority and amends no ADR. ADR-0001 stands
unmodified.
