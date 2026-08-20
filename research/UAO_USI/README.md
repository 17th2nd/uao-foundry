# UAO / USI Research Workspace

**Status:** RESEARCH / NON-AUTHORITATIVE
**Creates ASA authority:** No
**Creates Foundry canonical authority:** No
**Programme:** Persistent Identity Manufacturing Alpha (PIMA)
**Base SHA:** `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e`

## What this directory is

Working research material for the persistent-identity programme. Everything here is a
*candidate* unless a document explicitly records that repository or ASA governance has
ratified it. Nothing in this tree may be cited as ASA authority, and nothing here overrides
`docs/ADR-0001-UAO-TERMINOLOGY.md`, `docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md` or
`config/upstream-authority-lock.json`.

The `research/` root exists so that non-canonical status is structurally obvious. Canonical
documentation remains in `docs/`; canonical contracts remain in `schemas/`.

## Status labels

Every document in this tree carries a status header using exactly one of:

| Label | Meaning |
|---|---|
| `RESEARCH / NON-AUTHORITATIVE` | Investigation. May be wrong. Not implemented. |
| `CANDIDATE INTERFACE` | Proposed contract, versioned, implemented behind a seam. |
| `IMPLEMENTED — FOUNDRY-OWNED` | Built and tested. Foundry authority only, not ASA authority. |
| `BLOCKED ON UPSTREAM AUTHORITY` | Cannot proceed; names the blocking dependency. |

## Reading order

1. [`CURRENT_STATE.md`](CURRENT_STATE.md) — Phase 0 repository truth. **Read first.**
2. [`terminology/UAO_VS_USI.md`](terminology/UAO_VS_USI.md) — the candidate distinction under investigation.
3. `identity/` — the persistent identity model and its lifecycle.
4. `provenance/` — how identity decisions are evidenced.
5. `relationships/` — binding relationships to persistent identity under the ASA#29 constraint.
6. `significance/` — the `A_x`/`R_x` supply interface. The Foundry never computes significance.
7. `experiments/` — the falsifiable programme, including H7 (no material gain).
8. `falsification/` — negative results, preserved.

## Hard boundaries

- The repository is **not** renamed to USI. `ADR-0001` governs.
- UAO/USI **never stores significance**. See `significance/SIGNIFICANCE_INTERFACE.md`.
- Arbitrary-domain canonical URO manufacture stays **fail-closed** pending `17th2nd/ASA#29`.
- No document here may be promoted to `docs/` without an explicit governance decision.
