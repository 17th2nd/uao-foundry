# USI Foundry — Q1 Gate Report

**Run label:** `claude-usifoundry-q1-001`
**Programme:** USI Foundry — Manufacturing Application Alpha
**Branch:** `programme/usi-manufacturer-application-alpha`
**Operator:** Claude (lead manufacturing operator)
**Date:** 2026-08-21

---

## Phase A — Repository truth and lineage

Verified against the repository, not the directive's stated values. **No drift.**

| Item | Stated | Observed | Verdict |
|---|---|---|---|
| canonical `main` | `2bc2871d…` | `2bc2871d…` | match |
| Alpha candidate | `c43e92a2…` | `c43e92a2…` | match |
| successor branch | exists | `982acf5a…` | exists on origin |
| PR #15 | open draft | OPEN, draft, head `c43e92a2` | match |
| ASA#29 | unresolved | **OPEN**, unchanged since 2026-08-09 | match |
| `uao-foundry#3` | — | OPEN | unchanged |

### Lineage assessment (directive §58)

`git merge-base --is-ancestor c43e92a2 982acf5a` → **true**. The successor branch is a direct
descendant of the Alpha head, so **all of PR #15's work is present as ancestry**. No rebase,
cherry-pick or forward-port was required, and none was performed. The Alpha's empirical results,
tests and findings are intact and untouched.

Two commits already existed on the successor branch, both governance-only:

- `5690f93` ADR-0003 — adopt USI as canonical target terminology
- `982acf5` ADR-0001 marked *Superseded*, original decision preserved verbatim

Baseline on `982acf5a`: **120/120 Java, 12/12 Python, BUILD SUCCESS.**

> **Note on the terminology decision.** The Alpha's evidence-based recommendation was to retain
> UAO. ADR-0003 overrides it, which is the owner's call and is explicitly reaffirmed by this
> directive. Per §35, name choice and architectural value are separate questions; this programme
> implements the naming decision and continues measuring the architecture independently.

## Phase B — Governance

| ADR | Decision |
|---|---|
| **0004** UAO compatibility and migration policy | three layers migrated independently: product language now, implementation and serialised format unchanged; adapters not forks; history superseded not rewritten |
| **0005** USI identifier strategy | **Option A** — canonical uid stays `uao-<12 hex>` |
| **0006** Run evidence and the immutable package boundary | resolves P9-1 by moving volatile evidence out, not by loosening the guard |

### The identifier finding (ADR-0005)

**The prefix is ASA-pinned, not a Foundry choice.** `schemas/asa/uao.schema.json` is a
non-authoritative projection of ASA CSS 2026.1, and ADR-0002 §2 requires preserving the
`uao-<12 hex>` shape under the rule that Foundry schemas "must not widen, narrow or reinterpret"
the CSS. Emitting `usi-` canonically would be the Foundry reinterpreting an ASA primitive.

**Option C is blocked upstream. Only ASA can unblock it**, and closing ASA#29 does *not* — it is a
separate CSS question.

Option B (dual `usiId` + `legacyUaoId`) was assessed and **rejected on the directive's own
criteria**: the two values would differ only by prefix over identical hex, adding no information
while doubling the forms in circulation. Backward compatibility, registry compatibility, package
verification and external references are each made *worse* by it.

Adopted instead: the application API uses the product field name `usiId` carrying the **canonical
value unchanged**, beside an explicit `identifierScheme: "legacy-uao"`. The field names the role;
the value names the scheme. `UsiIdentifiers` implements the reserved `uao-X ⟷ usi-X` mapping,
tested for round-trip fidelity, and **a test asserts no production code calls it**.

## Phase C — P9-1 resolved

### Before and after, same experiment

| | refusals | P9-1 collisions | intended fail-closed |
|---|---:|---:|---:|
| Alpha (before) | 77 / 114 | **69** | 8 |
| After the fix | **8 / 114** | **0** | 8 |

**All 69 P9-1 refusals eliminated. All 8 intended refusals preserved.** The fail-closed guard for
content changing under a stable address is untouched and still fires.

### How

`reuse-report.json` is no longer attached to the package. `ReuseAnalyzer.analyze` still computes it
unchanged, including every cryptographic check on `registry://` evidence; it is now recorded in a
`RunStore` **beside** the registry. Neither `PackageContentDigest.CORE_FILES` nor either collision
guard was modified — the defect was removed, not tolerated.

`attachAndVerify` now throws, deliberately, so that reintroducing the defect fails loudly rather
than silently.

### Run records (directive §44, §45)

Content-addressed `run-<16 hex>`, append-preserving, idempotent on identical content, refused on an
id collision with different content. A completed run is never edited; a correction appends a record
carrying `supersedesRunId`. Stored **beside** the registry, not inside it, so run evidence is
structurally incapable of influencing the rebuild-verified registry index — asserted by test.

Timestamps are supplied by the caller, as with identity operations, so runs are reproducible.

## Evidence

| Check | Result |
|---|---|
| Java tests | **133/133** (120 preserved + 13 new) |
| Python adapter tests | 12/12 |
| 10 repeated cumulative manufactures | **all REGISTERED**, registry verifies |
| Repeated packages byte-identical | yes |
| Genuine collision still refused | yes |
| Legacy package with embedded report | verifies, registers, searches, reused |
| New + legacy coexist in one registry | yes, both verify |
| Canonical uid shape | `uao-<12 hex>` everywhere |
| Reserved mapping called by production code | **no** |

## Changes to previously-green surfaces, declared

1. **`.github/workflows/semantic-delta.yml`** — `test -f "$package_path/reuse-report.json"` became
   `test ! -f …` plus assertions that the run record exists and carries the expected counts. This
   is an artefact-location assertion, **not an adversarial control**; its intent is preserved and
   strengthened (it now checks the counts, which it did not before).
2. **`SemanticDeltaTest` and `OperatorConsoleTest`** — two assertions follow the evidence to its
   new location and additionally assert it is *not* in the package.
3. **`README.md`, `docs/REGISTRY.md`, `docs/SEMANTIC-DELTA.md`** — corrected; the semantic-delta
   doc carries an explicit relocation notice.

No adversarial control was removed or weakened.

## Q1 gate (directive §51)

| Required | Status |
|---|---|
| USI ADR | ADR-0003 (pre-existing on branch) |
| UAO compatibility ADR | **ADR-0004** |
| identifier strategy | **ADR-0005** |
| P9-1 fix | **done, measured 69 → 0** |
| run-record separation | **done** |
| all core tests green | **133/133** |

**Q1 MET.** Awaiting independent audit; Claude does not self-certify acceptance (§56).
