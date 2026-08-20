# UAO vs USI — Candidate Distinction Under Investigation

**Status:** RESEARCH / NON-AUTHORITATIVE
**Governed by:** `docs/ADR-0001-UAO-TERMINOLOGY.md` (Accepted, 2026-08-09)
**Ratification state:** NOT RATIFIED. Do not constitutionalise during this programme.

## The candidate distinction

```
USI  =  durable semantic identity / address
UAO  =  ASA-governed object representation associated with that durable identity
```

## What the repository already demonstrates

`CURRENT_STATE.md §3` records that UID is a pure function of `resolutionKey`:

```
uid = "uao-" + sha256(resolutionKey)[0..12]
```

This is evidence *for* the distinction being real rather than cosmetic. The `resolutionKey` is
already doing the job the candidate term "USI" names: it is a durable, portable, provider-agnostic
address, deliberately insulated from session, model, timestamp and wording
(`docs/STABLE-SEMANTIC-IDENTITY.md §3`). The canonical UAO is a *representation* keyed by it, and
one address can already carry several representations — that is precisely what
`MULTIPLE_UNRECONCILED_VARIANTS` records.

So the architecture arguably already contains both concepts; it simply has one name for them.

## What would make the distinction worth adopting

The deciding evidence is not elegance. Per programme §32, it is measurable capability. The
distinction earns adoption only if separating address from representation lets the machine do
something it cannot do today. Candidate tests:

1. **One address, many representations** — already true, via semantic variants.
2. **One address, many modalities** — an address referring to an image, a sensor reading and a
   document without the address itself being text-shaped. Untested.
3. **One address, many models** — Claude and another model resolving to the same address without
   coordination. Untested.
4. **Address survives representation supersession** — the address stays valid when the UAO is
   `Superseded`. Structurally supported by ASA `successor_identity_ref`; unused today.
5. **Address resolvable without the representation being present** — lookup succeeds against the
   registry with no package occurrence retrieved. Untested.

Tests 2–5 are exactly what Phases 1–5 build. If they pass and the benchmark shows no measurable
gain, **H7 stands and the distinction is a naming preference, not an architecture.**

## Implementation-neutral naming policy

Per programme §2, prefer names that do not encode `UAO` into new domain logic, so a future clean
migration stays possible. In practice, for new code introduced by this programme:

| Prefer | Avoid | Reason |
|---|---|---|
| `identity`, `stableIdentity` | `uaoIdentity` | migration-neutral |
| `resolutionKey` | `usiAddress`, `usi` | do not pre-adopt USI |
| `identityDecision` | `uaoDecision` | migration-neutral |
| `lifecycleState` | `uaoLifecycle` | migration-neutral |

Existing canonical names (`uid`, `uao-…`, `foundry_identity`, `resolution_key`) are **unchanged**.
ADR-0001 forbids a cosmetic rename, and `uao-<12 hex>` is pinned by ASA CSS.

## Recommendation status

**No recommendation is made at Phase 0.** The programme's final terminology recommendation is a
Phase 11 deliverable and must cite benchmark evidence from Phase 10, including the possibility
that H7 is confirmed.
