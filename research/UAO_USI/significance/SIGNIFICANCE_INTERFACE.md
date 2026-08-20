# Significance Interface

**Status:** CANDIDATE INTERFACE — versioned, Foundry-owned
**Interface version:** `0.1.0`
**Formulation status:** `RESEARCH_CANDIDATE_NOT_RATIFIED_BY_ASA`
**Implementation:** `significance/SignificanceInputs.java`, `FoundryRegistry.significanceInputs(…)`
**CLI:** `RegistryApplication significance-inputs <reference>`

## 1. The rule

> **UAO/USI supplies durable inputs to significance and never stores significance.**

Significance is transient. It is computed for an objective `q`, in a context `C_q`, at an epoch
`e` — none of which the Foundry knows or should know. A significance result baked into identity
state would be one runtime's judgement frozen into what looks like a durable fact, and every later
reader would inherit it as one.

What a significance computation *produces* may legitimately become durable — new evidence, changed
state, a new or superseded relationship, a validity update. Those arrive through ordinary
manufacture and lifecycle paths. **The result itself never does.**

## 2. Ownership split (programme §14), enforced

```
UAO/USI-owned  →  A_x   identity, durable attributes, state/version, provenance, source refs
URO-owned      →  R_x   relationship type, roles, state, provenance, validity
Runtime-owned  →  q, C_q, observer, environment, time, resources        ← NOT SUPPLIED
Engine-owned   →  𝓡_v, S_v, ⟨G,C↑,C↓,U,E⁺,E⁻,X,V⟩, Plan, Schedule       ← NEVER COMPUTED
```

The export carries `A_x` and `R_x`, and a `notSupplied` block naming the other two halves
explicitly. Naming what it does not own is the only place `R_v`, `S_v`, `Plan` or `Schedule` may
appear in the payload; the supplied halves are asserted free of them by test.

## 3. Shape

```json
{
  "significanceInterfaceVersion": "0.1.0",
  "formulationReference": "R_v(x,q,e) = muQ.F_v(Q; A_x, R_x, C_q, e)",
  "formulationStatus": "RESEARCH_CANDIDATE_NOT_RATIFIED_BY_ASA",
  "uid": "uao-…",
  "A_x": {
    "identity": {uid, resolutionKey, semanticType, canonicalLabels, aliases, externalIdentifiers},
    "state":    {lifecycleState, stateVersions, semanticVariantStatus},
    "assertions": [ … ],
    "assertionEpistemicStatus": "DEFERRED_ON_RECORD",
    "provenance": {occurrences, identityDecisionHistory}
  },
  "R_x": {
    "canonicalRelationships": [],
    "complete": false,
    "authorityStatus": "URO_TYPE_AUTHORITY_UNAVAILABLE",
    "blockedBy": "17th2nd/ASA#29",
    "consequence": "… considers the object in isolation.",
    "unpublishedRelationshipCandidates": [ … ]
  },
  "notSupplied": { "runtimeOwned": {…}, "significanceEngineOwned": {…}, "rule": "…" }
}
```

`assertionEpistemicStatus: DEFERRED_ON_RECORD` is carried deliberately. Assertions are **recorded
statements, not established truths**, and a consumer weighing them must not read them as verified
fact.

## 4. `R_x` is structurally empty, and says so loudly

Canonical URO publication is fail-closed pending ASA#29, so `R_x.canonicalRelationships` is always
`[]` and `complete` is always `false`.

**This is the most important thing the interface reports.** The current ASA direction is
`𝓡_v → S_v → Plan → Schedule`, and `𝓡_v` takes `R_x` as an argument. A significance architecture
that depends on relationships is being handed an empty relationship set. Any result computed from
these inputs today is a result about an object **considered in isolation** — which is why the
payload says exactly that, in a field, rather than leaving a consumer to infer it from an empty
array.

Identity-bound but unpublished relationship candidates are exposed separately as
`unpublishedRelationshipCandidates`, so the evidence of what `R_x` would contain is visible without
the two sets ever merging.

## 5. Fail-closed refusals

| Case | Why refused |
|---|---|
| unreconciled semantic variants | `A_x` would be assembled by silently unioning mutually inconsistent accounts of one object — exactly what the variant policy forbids |
| non-active lifecycle state | invites a computation over an object the registry has recorded as no longer standing on its own |
| anything short of an exact resolution | an alias or ambiguous match would supply the attributes of a guess |

The first two are guarded twice — once at resolution, once inside the export — so a future caller
that bypasses resolution cannot bypass the guard. Both layers are tested directly.

## 6. Versioning

Two versions, deliberately separate:

- `significanceInterfaceVersion` — this supply surface's contract.
- `formulationReference` / `formulationStatus` — the significance formulation it is shaped for,
  explicitly **not ratified**.

A consumer binding to this is binding to a moving target and is told so in the payload.

## 7. What is not here

- No significance is computed. Not partially, not as a hint, not as an ordering.
- Neither kernel digest is a significance value — both are content addresses over disjoint
  projections, carrying no ordering meaning. Asserted by test.
- No transfer semantics are applied; see `TRANSFER_SIGNATURE_CANDIDATE.md`.
