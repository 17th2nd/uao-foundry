# Negative Space Binding

**Status:** RESEARCH / NON-AUTHORITATIVE — implemented and tested, wired into nothing
**Phase:** 7
**Implementation:** `negativespace/ExpectedRelationship.java`, `negativespace/NegativeSpaceEvaluator.java`

## 1. The three distinctions

```
MISSING  ≠ FALSE      not finding it is not evidence it is untrue
UNKNOWN  ≠ ABSENT     not having looked properly is not the same as having looked and found nothing
EXPECTED ≠ OBSERVED   expecting it does not make it so, and never manufactures it
```

An expectation records a relationship someone believes should exist, so that its **absence** can be
evaluated. Recording one is not a claim it exists, and evaluating one never creates it — verified
by `evaluatingAnExpectationManufacturesNothing`, which tree-hashes the registry before and after.

## 2. Absence is only informative under two conditions

Per §17, negative-space evidence requires **identity certainty** plus a **bounded observation
scope**. Either failing yields `UNKNOWN`, never `ABSENT_WITHIN_SCOPE`.

| Situation | Evaluation | Why |
|---|---|---|
| an endpoint does not resolve | `UNKNOWN` | an absence between things you cannot pin down says nothing about the world |
| an endpoint has unreconciled variants | `UNKNOWN` | resolution refuses, so identity is not certain |
| the registry is empty | `UNKNOWN` | nothing was observed because nothing was there to observe |
| bounded scope, endpoints certain, not found | `ABSENT_WITHIN_SCOPE` | earned |
| found | `OBSERVED` | — |

Every result carries its `observationScope`: registry index hash, package count, package ids,
`bounded: true`, and a caveat that a relationship stated outside this registry is not observed here.
**An absence reported without its scope is uninterpretable** — the reader cannot distinguish a
thorough search from none at all.

## 3. The finding: over certified relationships, absence is currently vacuous

**This is the substantive result of Phase 7.**

Canonical URO publication is fail-closed pending ASA#29, so the certified relationship set is empty
**by authority**, not **by observation**. Every expectation evaluated against it would return
"absent" — and would be right, and would mean nothing, because a universe in which nothing can
exist reports every absence identically.

Reporting `ABSENT_WITHIN_SCOPE` there would be the worst thing this component could do: technically
true, trivially derived, and readable as though the Foundry had looked and found nothing. So the
certified universe returns:

```
evaluation:  SCOPE_VACUOUS
reasonCodes: [URO_TYPE_AUTHORITY_UNAVAILABLE]
explanation: "The certified relationship set is empty by authority rather than by observation, so
              every expectation would evaluate as absent and no absence would carry information."
```

**Negative-space reasoning over certified relationships is not available until ASA#29 closes.** No
amount of Foundry work changes that, and any benchmark lane claiming negative-space gains over
certified relationships today would be measuring an artefact.

## 4. The candidate universe is real and usable now

Retained, identity-bound relationship candidates *are* a genuine observation universe — they exist,
they vary, and searching them can succeed or fail meaningfully. So `Universe.CANDIDATE` evaluates
properly today.

Every such result is marked `certifying: false`. Observing a candidate is evidence that **someone
asserted** the relationship, not that **ASA governs** it. This is what lets the machinery be
exercised, tested and trusted before the authority arrives, without the two ever being conflated.

Matching requires **both** endpoints, **both** roles and the **type version** to agree. A
subject-side match alone would let an unrelated relationship satisfy the expectation.

## 5. Why this depends on Phase 5

Negative space is only possible because relationship candidates are now bound to persistent uids.
Before that, candidates pointed at bundle-local `cid-` handles, so an expectation stated in terms
of persistent identity could not be checked against them at all.

## 6. Deliberately not done

- **No expectation store.** Expectations are evaluated, not persisted into the registry. Persisting
  them would make "things we think should exist" part of the durable record, which needs governance
  the programme has not granted.
- **No expectation generation.** Nothing infers what relationships *ought* to exist. That is
  ontology work, and §1 of the programme rules it out.
- **No completeness model.** `bounded: true` means "the scope is stated", not "the scope is
  complete enough for this particular question". Judging sufficiency needs a domain model the
  Foundry does not have, so the caveat is stated and the judgement left to the reader.
