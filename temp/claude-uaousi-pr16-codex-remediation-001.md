# PR #16 — Codex-Ratification Findings Remediation

**Run label:** `claude-uaousi-pr16-codex-remediation-001`
**Date:** 2026-08-21
**Role:** Claude implemented the repair (authorised, §17). Claude does **not** close these findings —
a bounded Codex closure ratification follows.

## Provenance

| item | value |
|---|---|
| original candidate SHA | `eba310f6f8e451f380ca363f0ec2c8df906e5147` |
| new candidate SHA | `0edae30a0062735a5e7d9145141f7600eefa4d18` |
| base SHA | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` (unchanged) |
| branch / PR | `programme/usi-manufacturer-application-alpha` / #16 |
| findings source | Codex Pass B (`temp/codex-uaousi-pr16-ratification-pass-b-001.md`), disposition MATERIAL_CONCERN |
| canonical surfaces touched | **none** — `schemas/asa/`, ADRs, `config/upstream-authority-lock.json` all byte-unchanged |

## Files changed

| file | finding |
|---|---|
| `src/main/java/…/staging/StagedRelationshipStore.java` | F-R1 |
| `src/test/java/…/staging/StagedRelationshipTest.java` | F-R1 (5 new tests) |
| `src/main/java/…/runs/RunStore.java` | F-R3 |
| `benchmark/staged/h1_reconstruction.py` | F-R2 |
| `benchmark/staged/analyse_staged.py`, `benchmark/pima/analyse_lanes.py` | F-R4 |
| `benchmark/staged/test_median.py` (new) | F-R4 regression |
| `temp/benchmark/staged_rerun_analysis.json` | F-R4 (regenerated, aggregation only — no benchmark rerun) |

## F-R1 — staged read-path content-address integrity — **FIXED**

The exact repair: one shared `projectionOf(source)` now builds the identity-bearing projection, and
both the writer (deriving `stagedId`) and the new read-path `verifyContentAddress` call it, so a
record cannot carry a content address its own contents would not produce. `list()` now runs three
fail-closed checks — label triple, **content-address re-derivation**, filename — and the method's
Javadoc no longer claims re-derivation the code did not perform (§6). `verifyContentAddress` also
pins `recordVersion`, since the derivation uses the constant.

Why re-derivation and not full JSON-schema validation on read (§4): re-deriving the content address
already detects mutation of **every** meaning-bearing field (participants, sourceRefs, packageId,
typeVersion, identityBindingStatus, identityLiterals, contextualBindings, candidateId, recordedAt).
Adding a runtime JSON-schema validator would require threading the schema path into the store's
constructor for no additional tamper coverage, so it was not added; the read-path guarantee is
documented as content-address + label + filename, not full schema validation (no overclaim).

Tamper tests added (the vector Codex found — mutate a field while keeping id/filename/labels):
`aParticipantTamperWithUnchangedIdAndFilenameFailsClosed`,
`aSourceRefTamperWithUnchangedIdAndFilenameFailsClosed`,
`aPackageIdTamperWithUnchangedIdAndFilenameFailsClosed`, plus
`anUntamperedRecordStillReadsSuccessfully` and the §9 restart test
`stagedMemorySurvivesRestartDeterministically`.

**Live P1 proof** (real app, tamper the stored file, keep stagedId/filename/labels): read now fails
closed —
`Staged relationship content address does not match its contents: stg-cdefc70b070a2b6a expected stg-e422c5a110dddee9`.

## F-R2 — H1 evidence weakness — **FIXED**

`memoryRepoBytesRead` is no longer hard-coded. A `RepoOpenMeter` uses a Python audit hook (fires at
the C level for `io.open`/`pathlib`/`os.open`) to **measure** repository-file opens and bytes per
phase. Limb 3 no longer duplicates limb 1: it starts an **independent second application process
(C)** over the same store and compares its edge set against process B's.

Re-measured (T01, T03) against the existing stores — no benchmark/LLM rerun:

| task | fresh files/bytes (measured) | memory files/bytes (measured) | restart-stable B vs C |
|---|---|---|---|
| T01 | 15 / 5997 | **0 / 0** | **true** |
| T03 | 15 / 5961 | **0 / 0** | **true** |

Limbs now: edge-set equality ✓; memory reads 0 repo files/bytes (measured) ✓; stable across
independent restart ✓; accumulation depth ≥3 ✓ → **H1 SUPPORTED**, now on measured evidence.
H1 remains a durability / IO-reduction result, not a wall-clock-speed result (memory ~2 s vs fresh
~5 ms at this scale — recorded, not spun).

## F-R3 — reference integrity — **DISPOSITION IMPLEMENTED**

Classified rather than blanket-validated:

| reference | class | action |
|---|---|---|
| `RunStore.supersedesRunId` | **MUST_RESOLVE** | `record()` now rejects a supersedesRunId absent from the store |
| `RunStore.packageId` | MAY_BE_HISTORICAL | pointer, not required to resolve (a refused package is legitimately absent) |
| `StagedRelationship.packageId` | MAY_BE_HISTORICAL | relationship-bearing packages are refused admission by design |
| `StagedRelationship.sourceRefs` | MAY_BE_EXTERNAL | not rejected for being non-local |

`unresolvable` is not collapsed into `invalid`; only a dangling local-predecessor claim is refused.

## F-R4 — benchmark median presentation — **FIXED**

`int(statistics.median(...))` → true median in both analysis scripts; display format adjusted;
staged analysis regenerated (ASA 1211.5, PID_F 1711.5, PID_C 1656.0). `test_median.py` guards
even-count medians. **H2 task-success interpretation unchanged** (PID_F 0.583 vs ASA 0.75).

## Test totals (this candidate)

| suite | result |
|---|---|
| Java `mvn clean verify` | **162 / 162 pass** (157 + 5 new staging) |
| Python adapter | **12 / 12 pass** |
| benchmark median regression | **2 / 2 pass** |
| failures / errors / skips | 0 / 0 / 0 |

## Post-repair probes (§14)

| probe | result |
|---|---|
| P1 meaning-bearing tamper, id/filename intact | **FAIL CLOSED** (live + 3 JUnit tests) |
| P2 untouched staged store | **READ SUCCESS** |
| P3 restart durability + measured repo reads | **stable; 0 repo files/bytes on memory path** |
| P4 ASA#29 relationship manufacture | **EVIDENCE_INCOMPLETE / REFUSED / URO_TYPE_AUTHORITY_UNAVAILABLE** (unchanged) |
| P5 significance persistence sweep | **no significance-like fields** |

## Boundary preservation

`STAGED ≠ CANONICAL`, `staging ≠ certification`, ASA#29 fail-closed — all intact. No repair made
staged memory canonical or publication-eligible. ASA#29 remains OPEN upstream (unchanged).

## Remaining bounded findings

None outstanding from F-R1..F-R4. The pre-existing bounded findings from the audit stand as before
and are **not** in this remediation's scope: F-1 (concurrency race/classification, BOUNDED FOR
ALPHA), F-2 (staging error classification — note the store now fails closed with a *specific* content-
address message, though the neighbourhood endpoint still surfaces it as a generic 500), F-3-audit
(authority-lock pointer refresh).

## Independence

Per §17, Claude implemented this and does not self-close the findings. A bounded Codex closure
ratification targeting F-R1..F-R4 + no-boundary-drift is the next step, given the old SHA, new SHA,
diff, and tests.
