# ASA Interface — What This Programme Depends On and What It Must Not Touch

**Status:** RESEARCH / NON-AUTHORITATIVE
**Authority lock:** `config/upstream-authority-lock.json` — ASA main `908c5255…`, CSS `2026.1`

## 1. Consumed, never amended

| ASA surface | Used for | Programme's treatment |
|---|---|---|
| CSS UAO primitive (`schemas/asa/uao.schema.json`) | canonical shape | **closed schema** — no top-level field added; all Foundry material lives under `internal_state` |
| `lifecycle_status` enum | supersession / retirement | consumed as-is; richer states rejected rather than bolted on |
| `successor_identity_ref` | supersession target | recognised; emission not yet wired |
| URO n-ary role structure | relationship shape | recorded verbatim, validated against nothing |
| ADR-0002 §5 forbidden fields | significance boundary | enforced; kept **separate** from Foundry-local additions |
| `epistemic_class` deferral | assertion status | `DEFERRED_ON_RECORD` carried into the significance export |

## 2. Blocked on ASA

**`17th2nd/ASA#29` — governed Relationship Type role authority.** Verified OPEN at programme start
and unchanged.

Consequences measured by this programme, in increasing severity:

1. No canonical URO is ever published. *(known before this programme)*
2. A relationship-bearing package is `EVIDENCE_INCOMPLETE` and **inadmissible to the registry**, so
   no persistent relationship graph can be accumulated. *(established in Phase 9)*
3. Negative-space reasoning over certified relationships is **vacuous**: the certified set is empty
   by authority, so every absence looks identical. *(established in Phase 7)*
4. `R_x` in the significance interface is **structurally empty**, so any significance computed from
   Foundry inputs considers the object in isolation. *(established in Phase 6)*
5. Benchmark hypotheses **H1 and H2 are not testable**. *(established in Phase 10)*

Point 2 is the one that was not previously visible: the gap does not merely stop *publication*, it
stops *accumulation*, which is the whole premise of a persistent relational substrate.

## 3. Not created here

This programme creates **no ASA authority**. It does not mint Relationship Types, does not extend
`lifecycle_status`, does not ratify the significance formulation, and does not promote USI. Where
it tightens behaviour — the additional rejected significance field names — the tightening is
Foundry-local, labelled as such in the error text, and only ever narrows what the Foundry emits.

## 4. What ASA would need to supply to unblock this

Unchanged from `docs/UPSTREAM-DEPENDENCIES.md`, and this programme adds no new requirement. The
programme's evidence strengthens the case for the *existing* closure definition rather than
proposing a different one: per Relationship Type version, a machine-readable declaration of role
names, cardinality, permitted participant kinds, identity-bearing flags, symmetry and ordering,
plus version resolution.

One addition worth recording for whoever closes ASA#29: the Foundry can already resolve
relationship *participants* to persistent identities without any type authority (Phase 5). Only
role validation is blocked. A staged closure that admitted identity-bound relationship candidates
into the registry — while still refusing to certify them — would unblock accumulation, points 2–5
above, and H1/H2, ahead of full role-schema governance.
