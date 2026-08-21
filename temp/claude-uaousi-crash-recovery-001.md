# UAO / USI Manufacturing Alpha — Crash Recovery Report

**Run label:** `claude-uaousi-crash-recovery-001`
**Mode:** read-only post-crash discovery
**Operator:** Claude (assurance/recovery)
**Date:** 2026-08-21
**Discovery window:** ~06:12–06:16 AEST; repository state preserved unmodified throughout.
The only file written by this run is this report. No git state was altered; `mvn clean verify`
regenerated only the gitignored `target/` build output, as the standing test procedure requires.

---

## A. Repository identity

| Item | Value |
|---|---|
| path | `/home/brock-gerand/uao-foundry` |
| remote | `origin = https://github.com/17th2nd/uao-foundry.git` (verified) |
| default branch | `main` (origin/HEAD → origin/main) |
| current branch | `programme/usi-manufacturer-application-alpha` |
| HEAD | `0456eea22c947c503829bdb32305cefe1cfe1c5c` |
| origin/main | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` — **identical to the known canonical base; main has NOT advanced** |
| local main | `cb20687d` — behind origin/main by 1, never diverged; irrelevant to the programme |
| known programme base | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` (confirmed ancestor of all programme work) |

Governance read first, per the mandatory rule: `README.md` (governing rule + authority
boundary), ADR-0001..0006, `config/upstream-authority-lock.json`. No repository file named
"constitution" exists; the ADR set plus the authority lock is the operative governance surface.

## B. Crash classification

### LOCAL UNCOMMITTED WORK RECOVERABLE

All committed work is pushed and CI-green. The crash caught one coherent, in-flight work
package (staged-relationship store, "directive §18") in the working tree only: 3 modified
tracked files + 4 untracked source/schema files + 1 generated `.class`. Nothing staged, no
interrupted git operation, no corruption, no orphaned commits, no divergence.

## C. Branch topology

Linear, no forks, no rebase, no rewrite:

```text
2bc2871d  canonical base (= origin/main, unchanged)
   └─ f8b93f2 … c43e92a   programme/persistent-identity-manufacturing-alpha   (14 commits, PIMA phases 0–11)
         └─ 5690f93 … 0456eea   programme/usi-manufacturer-application-alpha  (5 commits, USI Q1+Q2)
```

- `programme/persistent-identity-manufacturing-alpha` **exists** locally and remotely, both at
  `c43e92a2240f` (PR #15 head). This is the branch the directive named.
- `programme/usi-manufacturer-application-alpha` is its **direct descendant** (ancestry
  verified with `merge-base --is-ancestor`): the sanctioned successor programme, at
  `0456eea22c94` locally, remotely, and as PR #16 head — all three identical.
- Other refs (`sprint/*`, `foundry/*`, `backup*/`, `transport/*`, `audit-export/*`) all
  predate the programme and are inert.
- Winner chosen by ancestry and content, not timestamp: the USI branch contains every PIMA
  commit plus the Q1/Q2 application work.

## D. Uncommitted state (crash residue — preserved, untouched)

Nothing staged. No deletions. No conflicts.

**Modified tracked files** (36 insertions, 1 deletion; full diff reviewed):

| File | Change |
|---|---|
| `src/main/java/org/seventeenthsecond/usifoundry/UsiApiServer.java` | +2: `GET /api/staged-relationships/<ref>` route |
| `src/main/java/org/seventeenthsecond/usifoundry/UsiFoundryConfig.java` | +7/−1: `staged-relationships/` home directory, a **sibling** of the registry, never a child |
| `src/main/java/org/seventeenthsecond/usifoundry/UsiFoundryService.java` | +27: wires `StagedRelationshipStore`, stages candidates post-manufacture, exposes the neighbourhood view; comments state explicitly that no publication decision changes and nothing enters the registry index |

**Untracked files** (all end cleanly; none truncated):

| File | mtime | Assessment |
|---|---|---|
| `src/main/java/org/seventeenthsecond/uaofoundry/staging/StagedRelationshipStore.java` | 04:54 | coherent implementation work |
| `src/test/java/org/seventeenthsecond/uaofoundry/staging/StagedRelationshipTest.java` | 04:55 | coherent test work (8 tests) |
| `schemas/staged-relationship.schema.json` | 04:53 | valid JSON; titled "…(Non-Canonical)"; requires `authorityStatus`, `certifying` |
| `benchmark/scale/RegistryScale.java` | 04:51 | coherent scale-probe source |
| `benchmark/scale/RegistryScale.class` | 04:51 | **generated output** (compiled from the above) |

Classification of the residue: **coherent implementation work**, mid-package. It implements the
"staged closure" study path recommended in the PIMA handoff §14/§16-2, citing "Directive §18"
(the USI programme directive, which is operator-held, not a repository file). The last file
write was 04:55; the crash therefore occurred after 04:55, roughly 18 minutes after the last
commit (04:37).

## E. Lost-work search

- `git reflog --all --date=iso`: strictly linear — clone (08-11) → sprint commit → PIMA
  checkout (08-20 20:23) → 14 PIMA commits → USI checkout (08-21 04:03) → 3 USI commits →
  HEAD. **No resets, no detached HEAD, no amends, no branch movements outside current refs.**
- `git fsck --full --no-reflogs --unreachable`: **empty output, exit 0.** No dangling or
  unreachable commits/blobs, no corruption.
- No `MERGE_HEAD` / `CHERRY_PICK_HEAD` / `REVERT_HEAD` / `REBASE_HEAD` / `rebase-merge/` /
  `rebase-apply/` / `sequencer/` / `BISECT_LOG` / `index.lock` / any stale `*.lock` in `.git/`.

**Orphaned work: NONE. Interrupted git operation: NONE.**

## F. Programme phase matrix

19 commits after base (14 PIMA + 5 USI), each mapped from actual file evidence:

| Phase | Status | Evidence |
|---|---|---|
| 0 — Repository truth | COMMITTED, PUSHED, AUDIT PENDING | `f8b93f2`; Phase-0 findings restated in handoff §4 |
| 1 — Identity kernel | COMMITTED, PUSHED, TESTED | `5b538dc`; `schemas/foundry-identity.schema.json`, kernel under `internal_state.foundry_identity` |
| 2 — Identity provenance | COMMITTED, PUSHED, TESTED | `dd33ea1`; evidence-bearing SAME/DIFFERENT/UNRESOLVED decisions, append-preserving |
| 3 — Registry upgrade | COMMITTED, PUSHED, TESTED | `7ea8d33`; uid/key/alias/external-id addressing, occurrence history, variant handling |
| 4 — Merge/split/supersession | COMMITTED, PUSHED, TESTED | `c39aa69`; mapping layer above uid derivation, `identity-operations/` journal |
| 5 — Relationship binding | COMMITTED, PUSHED, TESTED | `61ba337`; participants bound to persistent uids, ASA#29 boundary unmoved |
| 6 — Significance interface | COMMITTED, PUSHED, TESTED | `5f96434`; versioned A_x/R_x export, `R_x` empty with `blockedBy: 17th2nd/ASA#29` |
| 7 — Negative space | COMMITTED, PUSHED, TESTED | `ca0f4e9`; research-level (`NegativeSpaceTest`), no invented canonical authority |
| 8 — Functional manufacturer | COMMITTED, PUSHED, TESTED | operator console `44709bb` + USI application shell `4c8d03e` |
| 9 — ASALLM benchmark | COMMITTED, PUSHED | `653842f`; lanes A–E, n=12; A/B read frozen trace, not re-run |
| 10 — Ablation/falsification | COMMITTED, PUSHED | H1–H7 verdicts recorded; H7 "no material gain" NOT REFUTED; `research/UAO_USI/falsification/` |
| 11 — Product handoff | COMMITTED, PUSHED | `94ff289`/`c43e92a` handoff + operator guide; PR #15 draft |
| **Successor (USI Q1)** | COMMITTED, PUSHED, TESTED, CI GREEN | ADR-0004/0005/0006, P9-1 fix (69→0 refusals), run-record store |
| **Successor (USI Q2)** | COMMITTED, PUSHED, TESTED, CI GREEN | application shell, six views, UI evidence, 2 real defects fixed |
| **Successor (§18 staging)** | **PARTIAL — UNCOMMITTED (the crash residue)** | working tree only; compiles; its 8 tests pass |

No phase is marked from documents alone; each mapping was checked against committed files.
"AUDIT PENDING" applies to the whole programme: both PR #15 and PR #16 are drafts explicitly
awaiting independent audit (Q-gate reports: "Claude does not self-certify acceptance").

## G. Implemented architecture (demonstrably present)

- **Persistent identity:** stable `uid = sha256(resolution_key)[0..12]` unchanged; aliases with
  provenance; external identifiers; `state_version` separate from state; ASA-governed
  `lifecycle_status` not duplicated.
- **Provenance:** SAME/DIFFERENT/UNRESOLVED decisions persisted with evidence, append-preserving
  history (the Q2 identity-inspector screenshot shows an early UNRESOLVED preserved beside two
  later SAME decisions).
- **Registry:** exact addressing on uid / resolution key / alias / external identifier;
  occurrence history; `MULTIPLE_UNRECONCILED_VARIANTS` fail-closed variant handling; lookups
  delegate to `IdentityResolver`.
- **Merge/split/supersession:** explicit CLI operations, non-destructive, journaled.
- **Relationship binding:** persistent-uid participants in candidates; publication boundary
  unmoved (canonical URO count 0).
- **Significance interface:** the supply chain is preserved — the application exports A_x/R_x
  only; `R_x` structurally empty with `blockedBy: 17th2nd/ASA#29`; "The application never
  computes significance" (`UsiFoundryService`). Forbidden fields (`score`,
  `significance_value`, `belief`, `stance` + 8 Foundry-local additions) rejected from canonical
  structures; prohibition centralised and asserted by `SignificanceBoundaryTest` (12 tests).
- **Negative space:** present as research-level evaluation only; no canonical authority minted.
- **Identifier discipline:** canonical uid stays `uao-<12 hex>` (ADR-0005 Option A); the
  reserved `uao-X⟷usi-X` mapping is tested to be called by **no** production code.

## H. Tests (exact, this run)

| Suite | Result |
|---|---|
| Java `mvn -B -ntp clean verify` (working tree, incl. crash residue) | **155 run / 155 pass / 0 fail / 0 error / 0 skip — BUILD SUCCESS** |
| — of which `StagedRelationshipTest` (uncommitted) | 8/8 |
| — implied committed-head count | 147, matching the Q2 gate report exactly |
| Python `adapters/claude-code/tests` | **12 run / 12 pass — OK** |

Toolchain note: the workstation has a JRE only (no `javac`, no `mvn`) — known limitation #7
from the PIMA handoff. JDK 21.0.12.1 + Maven 3.9.9 were provisioned **into the session
scratchpad only**, with an isolated Maven repo, exactly as the prior operator's conduct notes
prescribe. Nothing was installed on the system or written into the clone; test artifacts went
to gitignored `target/` only.

## I. GitHub state

| Item | State |
|---|---|
| PR #16 — "USI Foundry — Manufacturing Application Alpha (Q1 + Q2)" | OPEN, draft, head `0456eea` = local HEAD = remote branch head |
| PR #15 — "Persistent Identity Manufacturing Alpha — Phases 0–11" | OPEN, draft, head `c43e92a` = local/remote PIMA head |
| CI on `0456eea` | **6/6 workflows success** (CI, application, adapter, semantic delta, provider protocol, audit remediation) |
| CI history | one `USI Foundry application` failure on `4c8d03e`, superseded by the fix commit `0456eea` ("assert the run-record delta rather than the total") — a resolved, explained failure, not a loose end |
| origin/main | `2bc2871d` — untouched by the programme |
| **Everything committed is pushed.** | Local and remote agree on every programme ref. |

## J. Temp report reconciliation

| Report | Claim | Git/CI evidence | Verdict |
|---|---|---|---|
| `PIMA-2026-08-20-FINAL-HANDOFF.md` | candidate `94ff289`, branch, PR #15, main unmodified, 120/120 + 12/12 | branch tip is `c43e92a` = `94ff289` + two doc-only commits (handoff SHA correction); PR #15 head matches; main untouched | **MATCHES** (tip/candidate delta is documentation-only, self-declared) |
| `PIMA-2026-08-20-EXECUTION-LOG.md` | per-phase log | phase mapping in §F reproduced from commits independently | consistent |
| `claude-usifoundry-q1-001.md` (Q1+Q2 gate) | Q1 133/133; Q2 147/147; P9-1 69→0; head lineage; ASA#29 OPEN | committed-head count 147 re-derived this run (155 − 8 uncommitted); CI 6/6 green on `0456eea`; ASA#29 confirmed OPEN | **MATCHES** |
| Any report claiming §18 staging complete | — | none exists; the residue is newer than every report | consistent — the crash predates any claim |

**No mismatches.** Operator prose and repository state agree everywhere they overlap.

## K. Last known good point

**`0456eea22c947c503829bdb32305cefe1cfe1c5c`** (tip of
`programme/usi-manufacturer-application-alpha`).

Why: linear descendant of the canonical base through the complete PIMA programme; pushed;
PR #16 head; 6/6 CI workflows green; 147/147 Java + 12/12 Python locally re-verified as part
of this run's 155/155; both Q-gate reports reconcile against it.

## L. Safest continuation point

**Branch `programme/usi-manufacturer-application-alpha` at `0456eea`, in this worktree, with
the current uncommitted §18 staging work left exactly in place.**

The residue is not damage — it is a coherent, compiling, self-tested work package (155/155
with it present) that stops mid-stream: the store, schema, test, API route and service wiring
exist; still missing are at minimum documentation (`README`/`REGISTRY`/ADR treatment of
staging), frontend exposure of the new endpoint, CI coverage, and the commit itself. Do not
reset, stash, or clean; resume by completing and committing this package.

ASA#29 check: upstream authority is **unchanged** (`17th2nd/ASA#29` OPEN, last updated
2026-08-09; authority lock still `NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE`; `uao-foundry#3`
OPEN). The crash-era code does **not** weaken the boundary: staged records are schema-marked
non-canonical with required `authorityStatus`/`certifying` fields, live beside — never inside —
the registry, change no publication decision, and the store throws if a record loses its
non-canonical labelling. Old assumptions therefore still apply.

## M. Recovery actions required (LIST ONLY — none performed)

1. Decide whether the §18 staged-relationship package proceeds. If yes: finish documentation +
   frontend + CI touches, commit the 4 source/schema files and 3 modified files on
   `programme/usi-manufacturer-application-alpha`, push, letting CI re-verify PR #16.
2. Decide the fate of `benchmark/scale/RegistryScale.java` (commit as a scale probe or
   discard); its `.class` sibling is generated output and should not be committed either way.
3. If the §18 package is instead abandoned, that is an explicit owner decision — record it
   before any clean.
4. Independent audit of PR #15 and PR #16 remains outstanding (both drafts, both gates state
   "awaiting independent audit").
5. Optional hygiene: fast-forward local `main` to `2bc2871d` (it is 1 behind, no divergence).
6. Confirm whether a §18 directive authority exists in writing outside the repo; the residue
   cites "Directive §18", which this discovery could not verify against a repository document.
