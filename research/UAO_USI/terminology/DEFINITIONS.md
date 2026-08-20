# Definitions

**Status:** RESEARCH / NON-AUTHORITATIVE — descriptive of what was built

Terms as this programme uses them. Where ASA governs a term, that governance controls and is noted.

## Identity

| Term | Meaning |
|---|---|
| **uid** | `uao-<12 hex>`, a pure function of `resolution_key`. ASA-governed shape. Derived, never authored. |
| **resolution key** | The durable address. Three namespaces only: `foundry:v0.1:<type>:<label>`, `fixture:<type>:<label>`, `ext:<scheme>:<identifier>`. |
| **semantic type** | Extracted from the key grammar. `null` for `ext:` keys — an external identifier says *which* object, not *what kind*. |
| **external identifier** | A durable third-party identifier. **Evidence about identity, never identity itself.** |
| **alias** | Any name. Routes *toward* identity; can never establish it. |
| **identity digest** | Derived hash over identity-bearing material. Stable across state change. Not a ranking. |
| **state version** | Derived hash over state-bearing material. Moves with state. Not a ranking. |

## Decisions

| Term | Meaning |
|---|---|
| **SAME** | Positive evidence the reference denotes a registered identity. |
| **DIFFERENT** | Positive evidence of contradiction. Never inferred from absence. |
| **UNRESOLVED** | Evidence insufficient. A first-class outcome, not a failure. |
| **identity decision** | The persisted record of one resolution: reference, verdict, reason codes, candidates, evidence. Lives inside an immutable package. |

## Lifecycle

| Term | Meaning |
|---|---|
| **Registered / Superseded / Retired** | ASA-governed `lifecycle_status`. |
| **MERGE / SPLIT** | Foundry-owned. Not ASA concepts; representable only as a mapping layer above the uid derivation. |
| **identity operation** | An append-preserving journal record. Content-addressed `idop-<16 hex>`. |
| **MULTIPLE_UNRECONCILED_VARIANTS** | One identity, several irreconcilable accounts. **Intentionally sticky**; cleared only by a governed reconciliation authority that does not exist. |

## Relationships

| Term | Meaning |
|---|---|
| **URO** | ASA relationship object. Canonical URO count is **always 0** pending ASA#29. |
| **relationship candidate** | Retained unresolved evidence. Participants bound to persistent uids where resolvable. |
| **ALL_PARTICIPANTS_BOUND** | The *identity* half is solved. **Not** publishable — the type-role half remains blocked. |

## Significance

| Term | Meaning |
|---|---|
| **A_x** | Durable local attributes. UAO-owned. Supplied. |
| **R_x** | Governed relationships. URO-owned. Supplied, and **structurally empty**. |
| **C_q, e** | Objective, context, observer, environment, epoch. **Runtime-owned. Never supplied.** |
| **𝓡_v, S_v, Plan, Schedule** | Engine-owned. **Never computed here.** |

## Negative space

| Term | Meaning |
|---|---|
| **OBSERVED** | Found within the stated scope. |
| **ABSENT_WITHIN_SCOPE** | Not found, with certain endpoints and a bounded scope. Earned. |
| **UNKNOWN** | Endpoints uncertain, or scope cannot support a conclusion. |
| **SCOPE_VACUOUS** | The universe is empty *by authority*, so absence in it carries no information. |

## Contested

| Term | Status |
|---|---|
| **USI** | Candidate name for durable semantic identity. **Not adopted** — see `../consensus/`. |
| **UAO** | Universal ASA Object. Authoritative per ADR-0001. |
