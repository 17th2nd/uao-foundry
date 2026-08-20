# PIMA Execution Log

**Run label:** `PIMA-2026-08-20`
**Programme:** UAO Foundry — Persistent Identity Manufacturing Alpha
**Base SHA:** `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e`
**Branch:** `programme/persistent-identity-manufacturing-alpha`
**Operator:** Claude (lead manufacturing operator)

Reports in `temp/` are operator working records. They carry no authority.

---

## Phase 0 — Repository truth

**Status:** COMPLETE

Governance preconditions verified against the repository, not recollection:

- `origin/main` = `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` — **confirmed after fetch**
- working tree clean — confirmed
- `mvn -B -ntp clean verify` → BUILD SUCCESS, 35/35 tests — confirmed
- UAO terminology authoritative (ADR-0001 Accepted) — confirmed
- `17th2nd/ASA#29` OPEN — confirmed via `gh`
- `17th2nd/uao-foundry#3` OPEN — confirmed via `gh`
- `relationshipTypeRoleAuthority: NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE` — confirmed

**No material authority change. Programme proceeds.**

### Deviations recorded

1. Local clone `main` was stale at `cb20687d`; `HEAD` was on `sprint/2026-08-10-audit-remediation-r1`.
   Fetched; programme branch cut from the canonical SHA directly.
2. Workstation has no JDK (JRE only) and no Maven. Provisioned Temurin JDK 21 + Maven 3.9.9
   **into the session scratchpad only**. No system install, no repository change.
3. `pytest` absent — Python adapter tests are CI-only evidence on this workstation.

### Principal Phase 0 finding

`uid = "uao-" + sha256(resolutionKey)[0..12]`. Identity is *derived*, never *resolved*. There is
no identity decision, no evidence for sameness, and no mapping layer — so MERGE is structurally
impossible without one. The canonical UAO schema is closed (`additionalProperties: false`), so
all Foundry-owned identity material must live under `internal_state`. The registry index is fully
derived from immutable packages and verified by rebuild-and-compare, so non-derivable identity
material cannot live in `index.json`.

### Finding P0-1 (real defect, latent)

`externalIdentifiers` is declared in `schemas/candidate-identity.schema.json`, supplied by all
fixtures and by the Claude adapter, and **read by no Java code**. A provider that correctly
supplies a durable external identifier has that evidence silently discarded. Fail-open behaviour
in an otherwise fail-closed codebase. Scheduled as the first Phase 1 increment.

**Deliverables:** `research/UAO_USI/CURRENT_STATE.md`, `README.md`, `terminology/UAO_VS_USI.md`.

---

## Phase 1 — Identity kernel

**Status:** COMPLETE
**Tests:** 51/51 green (35 baseline preserved + 16 new). `BUILD SUCCESS`.

### Built

| Component | File |
|---|---|
| External identifier discipline | `identity/ExternalIdentifiers.java` |
| Identity/state digest derivation | `identity/IdentityProjections.java` |
| Kernel assembly | `identity/IdentityKernel.java` |
| Decision space | `identity/IdentityDecision.java`, `IdentityResolution.java`, `IdentityReference.java` |
| Resolution API | `identity/IdentityResolver.java` |
| Kernel contract | `schemas/foundry-identity.schema.json` |
| Semantic type extraction | `identifiers/ResolutionKeys.semanticType` |

Wired through `AcquisitionStages.identityResolution`, `CanonicalStages.canonicalBuild`,
`PackageVerifier` (independent re-derivation) and `FoundryRegistry` (index + lookup).

### Closed

- **G-1 / Finding P0-1** — `externalIdentifiers` now reaches the canonical package, the registry
  index and search as the `EXTERNAL_IDENTIFIER` match kind.
- **G-2** — resolution layer added above key derivation; derivation itself unchanged.
- **G-3** — `identity_digest` / `state_version` materialise identity-vs-state separation.
- **G-9** — external identifier lookup available in registry and resolver.

### Deliberately not done

`resolveCandidate` is implemented and tested but **not yet called during manufacture**. Wiring it
would change manufacture behaviour, and any reuse it caused must be evidenced by an
append-preserving decision record, which is Phase 2. Recorded rather than quietly deferred.

### Evidence quality note

All 16 new tests passed on first run, which alone is weak evidence. The two tests guarding the
derived digests were mutation-tested: disabling the verifier's kernel reconstruction check made
exactly those two fail, and no others. The check is therefore load-bearing rather than incidental.

### Preserved

`uao-7fde0894bfbc` for the cow fixture is byte-identical to the baseline, confirming the identity
derivation was not disturbed. Fail-closed URO behaviour, forbidden-field rejection, content
addressing, registry rebuild-and-compare and transactional admission are all untouched and green.

---

## Phase 2 — Identity provenance

**Status:** COMPLETE
**Tests:** 61/61 green (35 baseline + 16 Phase 1 + 10 Phase 2). `BUILD SUCCESS`.

### Built

- `schemas/identity-decision.schema.json` — closed reason-code vocabulary, `SAME` requires a uid.
- `AcquisitionStages.identityDecisions` — one evidence-bearing record per resolved identity,
  emitted into `identity-resolution.json` and therefore into every package.
- `PackageVerifier.verifyIdentityDecisions` — internal-consistency verification.
- `IdentityResolver.resolveCandidate` now **wired into manufacture** (the Phase 1 deferral is closed).

### Closed

- **G-5** — SAME/DIFFERENT/UNRESOLVED persisted per identity.
- **G-6** — decision evidence and reason codes persisted with candidate and source refs.

### Design decision worth recording

Decisions live inside immutable packages rather than in a journal beside the registry index.
A journal would need a *rule* against rewriting; package immutability makes rewriting
structurally impossible, since any edit breaks the content digest and checksum inventory.
Append-preservation therefore comes from the existing architecture rather than from new discipline.

### Honest verification boundary

A package cannot prove whether the registry held a match at manufacture time — that state is
deliberately not copied into the package. Identity decisions are therefore excluded from the strict
reconstruction comparison and checked for internal consistency instead. The exclusion is narrow and
documented; a `SAME` decision must still bind the uid its own key derives, so a package cannot
claim to have reused a different registered identity.

### New fail-closed behaviour

Manufacture stops on `EXTERNAL_IDENTIFIER_CONTRADICTION` — a candidate whose durable external
identity contradicts what is registered under the same address. The complementary cross-key match
is recorded as a merge candidate and explicitly not acted on.

---

## Phase 3 — Registry upgrade

**Status:** COMPLETE
**Tests:** 68/68 green. `BUILD SUCCESS`.

### Built

- **Identity history in the index.** Each identity now aggregates the decision records contributed
  by every package occurrence — derivable from package content, so the rebuild-and-compare
  invariant is untouched.
- **`FoundryRegistry.identityRecord(IdentityReference)`** — exact persistent-identity addressing,
  delegating resolution to `IdentityResolver` so a lookup obeys the same evidence rules as a
  manufacture-time decision. The registry cannot become a back door to identity-by-name.
- **`RegistryApplication identity <reference>`** — CLI surface; infers reference kind from shape;
  exit 4 on a considered UNRESOLVED, distinct from success and from error.
- **Alias provenance** (`alias_provenance`) — every name with the candidate and sources behind it,
  reconstructed independently by the verifier. Closes G-7 except for time-awareness.

### Incidental fix

`RegistryApplication` usage text referenced `uao-foundry-0.1.0-SNAPSHOT.jar`; the built artifact is
`uao-foundry-0.1.0.jar`. Same class of stale-JAR-name defect the 2026-08-10 sprint fixed in CI.

### Closed

- **G-8** — identity material that is *derivable* now lives in the index; non-derivable material
  was avoided entirely rather than forced in.
- **G-9** — external identifier lookup, at both resolver and registry-CLI level.
- **G-7** — alias provenance (time-awareness remains open and is recorded as such).

### Evidence note

Adding `alias_provenance` initially broke 20 tests, because the verifier's independent
reconstruction did not produce the new field. That is the reconstruction discipline working: the
package could not carry identity material the verifier had not derived for itself. Mirroring the
assembly in `PackageVerifier` restored green.

---

## Q1 QUARTER GATE — identity substrate

**Status:** REACHED. Draft PR open for independent audit. Not merged.

| Item | Evidence |
|---|---|
| Base SHA | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` |
| Q1 head SHA | `7ea8d33824d8e61a0e2f18936196591fc99c96e6` |
| Branch | `programme/persistent-identity-manufacturing-alpha` |
| Draft PR | `17th2nd/uao-foundry#15` |
| Java tests | 68/68 |
| Python adapter tests | 12/12 |
| CI workflows on the exact head | **5/5 success** |

CI workflows, all `success` on this exact SHA:
UAO Foundry CI · independent-audit remediation · provider protocol · semantic delta · Claude Code adapter.

### Q1 gate requirements

| Required | Status |
|---|---|
| persistent identity model | `identity/IdentityKernel`, `schemas/foundry-identity.schema.json` |
| aliases | present; provenance-bearing; **time-awareness open** |
| state/version separation | `identity_digest` / `state_version`, both derived and verifier-checked |
| provenance | `identity-decision.schema.json`, one record per identity, append-preserving |
| registry lookup | uid / resolution key / external identifier / alias, plus decision history |
| tests green | 68/68 Java, 12/12 Python, 5/5 CI |

### Locally replicated CI gates (before push)

- demonstration-identity leak guard — clean
- cross-domain manufacture (cow/granite/pie) — 3/3 EXPERIMENTAL + verified
- byte-determinism — identical trees
- snapshot-only resume — 14 stages resumed, verification passed
- tamper negative control — rejected
- ASA#29 fail-closed URO boundary — `canonicalUros = 0`, `URO_TYPE_AUTHORITY_UNAVAILABLE`, `EVIDENCE_INCOMPLETE`

### Operator note

Two tracked `.gitkeep` files (`dist/`, `work/`) were removed by the local `rm -rf work dist` CI
replication and restored before proceeding. Python `__pycache__` directories produced by the
adapter tests are untracked and were cleaned; they are not covered by `.gitignore`.

---

## Phase 4 — Merge / split / supersession / retirement

**Status:** COMPLETE
**Tests:** 83/83 green (15 new adversarial). `BUILD SUCCESS`.

### Built

- `identity/IdentityOperation.java` + `schemas/identity-operation.schema.json` — content-addressed
  (`idop-<16 hex>`), shape-validated per kind, mandatory justification and reason codes.
- Append-preserving journal at `<registry>/identity-operations/`, a **second immutable
  content-addressed store** beside `packages/`, so the derived-index invariant is untouched.
- Lifecycle derivation into the index (`lifecycleState`, `successorUids`, `lifecycleOperationId`).
- Lifecycle refusal in `IdentityResolver`; reuse refusal in `ReuseAnalyzer`.
- CLI: `supersede`, `retire`, `merge`, `split`, `operations`.

### Closed

- **G-10** — merge and split now exist, as a mapping layer above the uid derivation.
- **G-11** — supersession and retirement are recorded (ASA-canonical *emission* still unused; noted).

### Central design decision

**Resolution is not redirected.** After `SUPERSEDE A → B`, asking for A yields
`UNRESOLVED / IDENTITY_SUPERSEDED` naming B, not B itself. Silent redirection would change what a
later manufacture produces without anyone requesting the change — the destructive rewrite §10
forbids, arriving by the back door.

### Two real bugs caught by the adversarial tests

1. **False cycle on every merge.** A `MERGE` names its survivor among its subjects, and the
   lifecycle walk treated the survivor as a link in a supersession chain pointing at itself. Fixed
   by excluding subjects that are also targets of their own operation.
2. **Lifecycle reason codes missing from the decision schema.** Manufacture against a registry
   holding a retired identity failed schema validation. Fixed by extending the closed enum.

Neither would have been found without writing the attacks first.

### Deliberately not done

No automatic semantic reconciliation. A recorded merge does not union, rank, choose or discard
assertions and does not clear `MULTIPLE_UNRECONCILED_VARIANTS`. Relationship effects are deferred
to Phase 5, when relationship candidates are bound to persistent uids.

---

## Phase 5 — Relationship binding

**Status:** COMPLETE
**Tests:** 90/90 green (7 new). `BUILD SUCCESS`.

### Built

- `schemas/unresolved-relationship.schema.json` — retained candidates now carry `typeVersion`,
  bound participants, `identityBindingStatus`, identity literals, contextual bindings, sources.
- Participant binding to persistent uids in `relationshipConstruction`, mirrored in the verifier's
  independent reconstruction.
- Registry `relationshipBindings` per identity, marked `canonicalUroPublished: false` /
  `blockedBy: URO_TYPE_AUTHORITY_UNAVAILABLE`.

### Closed

- **G-12** — relationship participants bound to persistent identity.

### The distinction that made this possible

Identity binding and type-role authority are **separable**. Resolving `cid-x → uao-y` is an
identity operation needing no ASA#29 authority; validating that `container` is a legal role of
`asa.core/contains@1` does. Previously both were treated as blocked together, and the retained
finding discarded its participants — retaining a relationship "as evidence" while throwing away
the part saying what it was about.

**The publication boundary did not move.** `canonicalUros = 0`, `URO_TYPE_AUTHORITY_UNAVAILABLE`,
`EVIDENCE_INCOMPLETE`, empty `relationship_references`. One test exists solely to stop
`ALL_PARTICIPANTS_BOUND` being misread as publishable.

### Finding P5-1

`relationship-bearing-cow.json` names participant `cid-species`, which is **not among its candidate
identities**. The Foundry accepted this silently for as long as the fixture has existed, because
participants were discarded. Candidate *claims* with an unmapped subject throw; relationships did
not. Now visible as `UNRESOLVED` / `PARTIALLY_BOUND`. Test fixture only — no manufactured knowledge
affected — but it is exactly the defect class the binding closes.

### Honest limitation

A package carrying a relationship candidate is `EVIDENCE_INCOMPLETE` and therefore not
registry-admissible, so cross-package relationship tracing is **latent** rather than demonstrable
today. The test asserts the inadmissibility rather than pretending otherwise.

---

## Phase 6 — Significance interface

**Status:** COMPLETE
**Tests:** 102/102 green (12 new). `BUILD SUCCESS`.

### Built

- `significance/SignificanceBoundary.java` — the two-tier prohibition, centralised. Previously the
  forbidden set was duplicated verbatim in `PipelineBase` and `PackageVerifier`, so the two could
  drift apart silently.
- `significance/SignificanceInputs.java` — versioned `A_x` / `R_x` supply surface.
- `FoundryRegistry.significanceInputs(...)` + `RegistryApplication significance-inputs`.
- `research/UAO_USI/significance/` — `SIGNIFICANCE_INTERFACE.md`, `SIGNIFICANCE_INPUT_MODEL.md`,
  `TRANSFER_SIGNATURE_CANDIDATE.md` + `.schema.json`.

### Closed

- **G-13** — `A_x` / `R_x` export exists, is versioned, and computes no significance.
- **G-16** — the programme's eight additional significance field names are now rejected, kept
  **separate** from ADR-0002's four so a Foundry-local tightening cannot be mistaken for ASA
  authority. Reported distinctly and tested.

### The most important thing the interface reports

`R_x` is **structurally empty** and says so in a field, not by an empty array. The ASA direction is
`𝓡_v → S_v → Plan → Schedule` and `𝓡_v` takes `R_x` as an argument — so a significance
architecture that depends on relationships is currently being handed nothing. `R_x` carries
`complete: false`, `authorityStatus`, `blockedBy: 17th2nd/ASA#29` and a plain-language
`consequence` stating that any result computed from these inputs considers the object in isolation.

### Placement correction made during the phase

The transfer-signature candidate schema was first written into `schemas/`, then moved to
`research/UAO_USI/significance/`. `schemas/` is tree-hashed into every job's `configurationHash`,
so a research artefact there would have altered the manufacturing configuration of every job —
making an unratified proposal part of the deterministic identity of real work.

### Two test defects found and fixed (behaviour was correct in both)

1. A crude "payload contains no `R_v`" assertion flagged the `notSupplied` block, which names
   engine-owned outputs precisely in order to disclaim them. Narrowed to the supplied halves.
2. The unreconciled-variant refusal fires at *resolution*, not inside the export. The export's own
   guard is therefore unreachable from the registry path; it is now exercised directly so a future
   caller bypassing resolution cannot bypass the guard.

### Gap recorded, not filled

**Validity declarations are not modelled.** ASA lifecycle gives a *status*, not a validity
interval; nothing records "valid from T1 to T2". This is the same missing time model as alias
time-awareness — one absence surfacing twice.

---

## Phase 7 — Negative space

**Status:** COMPLETE
**Tests:** 111/111 green (9 new). `BUILD SUCCESS`.

### Built

`negativespace/ExpectedRelationship.java` (content-addressed `exp-<16 hex>`, mandatory rationale)
and `negativespace/NegativeSpaceEvaluator.java`. Research-level: wired into no manufacturing,
publication or admission path.

### Closed

- **G-14** — expected-relationship records and absence-state evaluation exist.

### THE FINDING — absence over certified relationships is currently vacuous

Canonical URO publication is fail-closed pending ASA#29, so the certified relationship set is empty
**by authority**, not **by observation**. Every expectation evaluated against it would return
"absent" — correctly, and meaninglessly, because a universe in which nothing can exist reports
every absence identically.

The evaluator therefore returns `SCOPE_VACUOUS`, not `ABSENT_WITHIN_SCOPE`. Reporting absence there
would be technically true, trivially derived, and readable as though the Foundry had looked.

**Consequence for Phase 9/10:** benchmark lane E (negative space) cannot show a genuine gain over
*certified* relationships today. Any measured improvement would be an artefact of the empty set.
Lane E must therefore either run over the *candidate* universe with results explicitly marked
non-certifying, or be reported as blocked. This must not be quietly glossed when the benchmark runs.

### Usable today

The *candidate* universe — retained, identity-bound relationship candidates — is a real observation
universe and evaluates properly. Every result is marked `certifying: false`: observing a candidate
means someone asserted the relationship, not that ASA governs it.

This phase is only possible because Phase 5 bound candidates to persistent uids; before that,
candidates pointed at bundle-local handles and could not be checked against an expectation stated
in persistent identity.
