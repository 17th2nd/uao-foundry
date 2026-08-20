# USI Foundry — §18 Persistent Relationship Staging

**Run label:** `claude-uaousi-staged-relationships-001`
**Programme:** USI Foundry — Manufacturing Application Alpha (post-crash continuation)
**Branch:** `programme/usi-manufacturer-application-alpha`
**Operator:** Claude (lead manufacturing operator)
**Date:** 2026-08-21

---

## Provenance

| Item | Value |
|---|---|
| Canonical base (`origin/main`, unchanged) | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` |
| Recovered head (pre-crash, Q2 gate) | `0456eea22c947c503829bdb32305cefe1cfe1c5c` |
| Recovery report commit | `2609b45` — `temp/claude-uaousi-crash-recovery-001.md` |
| §18 package commit | `f1c9c50c1abe990502438d4134f0ac333ea80f86` |
| Draft PR | #16 (not merged; merge withheld per directive) |
| Crash residue discarded | **None** — every recovered file is in the package |

The crash-recovered work (store, tests, schema, service/config/API wiring, scale probe) was
preserved byte-for-byte where it was already correct; completion added only the surfaces the crash
interrupted: documentation, operator UI, status exposure, application tests, smoke mode and CI.

## Files changed (17 across the two commits)

New: `docs/RELATIONSHIP-STAGING.md`, `schemas/staged-relationship.schema.json`,
`staging/StagedRelationshipStore.java`, `staging/StagedRelationshipTest.java` (8 tests),
`benchmark/scale/RegistryScale.java`, `temp/claude-uaousi-crash-recovery-001.md`.
Modified: `UsiFoundryService` (staging hook, neighbourhood view, status count),
`UsiFoundryConfig` (sibling directory), `UsiApiServer` (one GET route), `app.js` (identity
inspector panel + status view), `UsiApplicationTest` (+2 tests), `smoke.py` (`--staging`),
`usi-application.yml` (one CI step), `README.md`, `docs/REGISTRY.md`, `app/README.md`,
`.gitignore` (generated-class hygiene).

## Relationship staging architecture

```text
manufacture → unresolved-items.json (immutable package, unchanged)
                    │  copy, never re-derive
                    ▼
<home>/staged-relationships/stg-<16 hex>.json     ← sibling of registry/, never child
        content-addressed · append-preserving · idempotent
        schema-pinned: NON_CANONICAL_CANDIDATE_MEMORY /
        URO_TYPE_AUTHORITY_UNAVAILABLE / certifying:false
```

- Read path re-derives every content address and fails closed on a record that lost its
  non-canonical labelling or whose file name mismatches its id.
- No `VALIDATED`/`REJECTED`/`SUPERSEDED` dispositions exist — each would be a
  relationship-authority judgement the Foundry must not make while ASA#29 is open. The lifecycle
  is `retained` (in the package) → `staged` (in the store), both derivable from artefacts.
- Negative-space room preserved: the store records observed assertions only; nothing encodes
  `EXPECTED`, and absence of a record is not evidence of absence.
- Ownership boundary documented in `docs/RELATIONSHIP-STAGING.md`: identity registry / relationship
  staging implemented here; ASA Relationship Type authority implemented nowhere.

## Identity-binding behaviour

Participants carry the persistent uid **only where the relationship-construction stage already
resolved it** (`binding: RESOLVED`, `uao-<12 hex>`); unresolved participants stay
`binding: UNRESOLVED` with no uid, and resolution is never forced — asserted by the application
test, which checks one resolved and one unresolved participant survive staging unchanged. The
neighbourhood view (`GET /api/staged-relationships/{ref}`) requires an exactly resolved identity
and refuses everything else with `IDENTITY_AMBIGUITY` (HTTP 409) rather than guessing.

## ASA#29 boundary evidence

Authority re-checked this run: `17th2nd/ASA#29` **OPEN**, unchanged since 2026-08-09;
`config/upstream-authority-lock.json` untouched (`git diff` clean on all authority surfaces).

Mechanised evidence that staging does not weaken the boundary:

- `stagingChangesNoPublicationDecisionAndNoRegistryAdmission` — tree hash of the package identical
  before/after staging; publication stays `EVIDENCE_INCOMPLETE`; canonical URO count 0; registry
  admission still throws.
- `theStagingStoreNeverEntersTheRegistryIndex` — index canonical-JSON identical before/after;
  registry re-verifies.
- `everyStagedRecordIsLabelledNonCanonicalByConstruction` — a record claiming `certifying: true`
  fails schema validation.
- `aRecordStrippedOfItsNonCanonicalLabellingFailsClosed` — tampered store refuses to read.
- CI step greps every staged record for the labelling and greps the registry index for `stg-`
  leakage on a live application.
- Application flow test: relationship-bearing manufacture → `REFUSED` +
  `URO_TYPE_AUTHORITY_UNAVAILABLE` + staged count 1 + registry verification `PASS`.

## Significance-boundary evidence

`SignificanceBoundary.collect` now also sweeps the staged neighbourhood response — no
`significance`/`importance`/`priority`/`attention_weight`/`allocation_score`/`reasoning_tier`/
`schedule_priority`-class field appears in staged records or any API surface. `R_x` remains
structurally empty with `blockedBy: 17th2nd/ASA#29`; staging feeds nothing into it. The store's
schema (`additionalProperties: false`) makes the forbidden fields unwritable, not merely unwritten.

## Tests

| Suite | Result |
|---|---|
| Java `mvn -B -ntp clean verify` | **157 run / 157 pass / 0 fail / 0 error / 0 skip** |
| — inherited (Q2 head) | 147 |
| — `StagedRelationshipTest` (recovered) | 8 |
| — new application tests | 2 (staged neighbourhood + unresolved refusal, incl. UI-wording contract) |
| Python adapter | **12 / 12 pass** |
| Frontend tests | none as a separate suite; UI is exercised via packaged-resource assertions and the CI browser-less smoke |
| Integration (local live app) | smoke `--staging` **5/5 ok** against a running instance; store/labels/index checked on disk |
| Failures / errors / skips | 0 / 0 / 0 |

## CI runs (head `f1c9c50`)

All seven triggered workflow runs completed **success**: UAO Foundry CI, provider protocol,
semantic delta, Claude Code adapter, independent-audit remediation, and USI Foundry application
(twice — push and PR triggers). The new staging step was inspected in the log, not assumed from
the badge: all five `--staging` smoke assertions ran `ok` against the live CI application, the
sibling-store and labelling disk checks executed, and the index-leak grep found nothing.

A follow-up commit (this report + a UI-wording assertion added to the staging application test)
re-triggers CI; its result is checked after push and appended below if anything differs.

## Scale probe results (§48 probe, observed measurements only)

`RegistryScale` compiled against the shipped jar, run at a disposable scratchpad root:

```text
    size   admit ms/pkg       index ms      search ms     address ms     index KB
       5           19.4              8             10              9            8
      25           36.5             31             33             30           41
      50           62.6             59             60             62           83
     100          120.6            117            119            118          166
```

Reading: every per-operation cost (admission, index read, search, exact address) grows **linearly
with registry size**, because each currently re-reads/rebuilds the full deterministic index; total
accumulation cost is therefore quadratic. At Alpha scale (hundreds of identities) this is
comfortably usable; at tens of thousands it will not be. No claim beyond these observations is
made; index incrementalisation is future engineering, not a defect in current guarantees.

## Known limitations

1. Staged records are append-only with no governed reconciliation — deliberate: any disposition
   vocabulary would be invented relationship authority.
2. The neighbourhood view is exact-uid only; no transitive traversal, no cross-identity graph
   query. Measurement first, graph engine later (if ever authorised).
3. Re-observing an identical candidate in a new manufacture creates a new record (differing
   `recordedAt`/`packageId`); observation-time/validity intervals remain the "one missing time
   model" the PIMA handoff already recorded.
4. Per-operation registry cost is linear in registry size (above).
5. Live-provider staging untested; deterministic fixtures only, as before.
6. UI evidence screenshots (`temp/ui-evidence/`) predate the staged panel; the panel's presence
   and wording are test-asserted rather than screenshot-evidenced.

## Next programme stage

With P9-1 resolved (Q1) and §18 staging landed, the PIMA handoff's recommended order (§16) makes
the **C/D/E benchmark re-run** the next authorised stage: accumulation was the stated blocker that
made H1/H2 *not testable*, and staged candidate memory is exactly the substrate the re-run needs.
Falling due after that: provenance/identity-continuity task design (H6) and one authenticated
live-provider manufacture into a disposable registry.

**Q-gate discipline unchanged:** awaiting independent audit; Claude does not self-certify
acceptance. PR #16 remains the integration/assurance surface and is not merged.
