# PIMA — Final Handoff

**Run label:** `PIMA-2026-08-20`
**Programme:** UAO Foundry — Persistent Identity Manufacturing Alpha
**Operator:** Claude (lead manufacturing operator)

---

## 1–3. Provenance

| Item | Value |
|---|---|
| Base SHA (canonical accepted main) | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` |
| Candidate SHA | `94ff28966b5e9c20abfe0c8b714b35b4d2278ffc` |
| Branch | `programme/persistent-identity-manufacturing-alpha` |
| Draft PR | `17th2nd/uao-foundry#15` — **not merged, not mergeable by the operator** |
| `main` modified | **No** |

## 4. Architecture summary

A resolution layer built **strictly above** the existing key derivation, which is unchanged.
`uid = sha256(resolution_key)[0..12]` still holds and every pre-existing uid is stable
(`uao-7fde0894bfbc` for the cow fixture, before and after).

Three structural facts from Phase 0 shaped everything:

1. **The ASA UAO projection is a closed schema.** No top-level field was added; all Foundry-owned
   identity material lives under `internal_state.foundry_identity`.
2. **`index.json` is fully derived** and rebuild-verified. Nothing authored may live there — so
   identity decisions live inside immutable packages, and lifecycle operations live in a second
   immutable content-addressed store.
3. **uid is derived from the key**, so two uids can never become one. Merge and split exist only as
   a mapping layer above the derivation.

Full diagram: `docs/OPERATOR-GUIDE-PERSISTENT-IDENTITY.md §6`.

## 5. Persistent identity schema

`schemas/foundry-identity.schema.json` — `canonical_label`, `aliases`, `alias_provenance`,
`resolution_key`, `semantic_type` (nullable and required-present), `external_identifiers`,
`identity_digest`, `state_version`, `source_refs`.

`uid` and `lifecycle_status` are deliberately **not** restated: both are ASA-governed top-level
members, and duplicating them would create two places to disagree.

Both digests are **derived, never authored**; the verifier re-derives them from independently
reconstructed candidate material, and forging either fails verification (mutation-tested).

## 6. Registry changes

`identityRecord()` exact addressing (uid / key / external identifier / alias), per-identity
decision history, state versions, external identifiers, semantic type, lifecycle state, relationship
bindings; an append-preserving `identity-operations/` journal; `significance-inputs`; CLI
`identity`, `supersede`, `retire`, `merge`, `split`, `operations`.

Lookups delegate to `IdentityResolver`, so the registry cannot become a back door to
identity-by-name.

## 7. Relationship-binding changes

Participants bound to persistent uids where resolvable; `typeVersion`, roles, identity literals and
contextual bindings retained. **The publication boundary did not move**: canonical URO count 0,
`URO_TYPE_AUTHORITY_UNAVAILABLE`, `EVIDENCE_INCOMPLETE`, empty `relationship_references`.

## 8. Significance interface

Versioned `A_x`/`R_x` export with an explicit `notSupplied` block. No significance is computed.
`R_x` is structurally empty and says so in a field, with `blockedBy: 17th2nd/ASA#29` and a
plain-language consequence. The forbidden-field prohibition is centralised and extended, with
ADR-0002's four kept separate from the eight Foundry-local additions.

## 9–10. Benchmark design and results

Lanes A/B read from the frozen ASALLM trace, **not re-run, re-graded or altered**; the oracle is
imported from the ASALLM runner, not copied; `~/asallm_empirical` is byte-unchanged at `e7a2afa`.
C/D/E take exactly lane B's file set and vary only what is said about those files.

| lane | n | success | recall | tokens |
|---|---:|---:|---:|---:|
| A similarity | 12 | 0.58 | 0.53 | 581 |
| B relational | 12 | **0.75** | 0.81 | 1211 |
| C + persistent identity | 12 | **0.75** | 0.81 | 1656 |
| D + provenance | 12 | **0.75** | 0.81 | 1888 |
| E + negative space | 12 | 0.83 | 0.81 | 2178 |

## 11. Falsification results

| H | Verdict |
|---|---|
| H1 reduces relationship reconstruction | **NOT TESTABLE** — ASA#29 blocks accumulation |
| H2 relationship precision | **NOT TESTABLE** — same |
| H3 fewer duplicate entities | **SUPPORTED** — 0 duplicates / 6 observations; mechanical |
| H4 negative-space reasoning | **MEASURED, CONFOUNDED** — near-oracular on absence tasks |
| H5 reduces context | **CONTRADICTED** — increases it, monotonically |
| H6 provenance tracing | **NOT TESTABLE by this benchmark** |
| H7 **no material gain** | **NOT REFUTED** |

**Headline: good relationship extraction was already doing the work.** Full record:
`research/UAO_USI/falsification/`.

## 12. Tests and CI evidence

| Evidence | Result |
|---|---|
| Java tests | **120/120** (35 baseline preserved + 85 new) |
| Python adapter tests | **12/12** |
| CI on the Q1 head `720d9ea` | **5/5 workflows success** |
| CI on the Q2 head `ca0f4e9` | **5/5 workflows success** |
| Demonstration-identity leak guard | clean |
| Byte-determinism, resume (14 stages), tamper rejection | pass |
| ASA#29 fail-closed URO boundary | preserved and re-verified |

Two derived-field checks were **mutation-tested**: disabling the verifier's kernel reconstruction
failed exactly the two digest tests; restoring the `alias_provenance` defect failed exactly the
cross-source reuse test.

## 13. Known limitations

1. **P9-1 (open, pre-existing)** — the registry cannot accept a third observation of unchanged
   material. 69 of 114 cumulative manufactures refused. Caps H1 independently of ASA#29. **Not
   fixed:** every option is a design decision on an audited surface; options and a recommendation
   are recorded.
2. **No persistent relationship graph** — relationship-bearing packages are inadmissible.
3. **Alias time-awareness and validity intervals absent** — one missing time model, twice.
4. **ASA-canonical supersession never emitted** — recognised but unused.
5. **Live provider untested** — deterministic fixtures only.
6. **Benchmark n=12 per lane**, one codebase, tasks not designed for identity continuity.
7. **Repository not buildable on a stock workstation** — JRE only, no Maven.

## 14. ASA#29 status

**OPEN**, unchanged since 2026-08-09. Authority lock still reports
`NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE`. No new Relationship Type role authority appeared.

This programme adds one observation for whoever closes it: **the Foundry can already resolve
relationship participants to persistent identities without any type authority.** Only role
validation is blocked. A staged closure admitting identity-bound relationship candidates into the
registry — while still refusing to certify them — would unblock accumulation and H1/H2 ahead of full
role-schema governance.

## 15. UAO vs USI recommendation

> **Retain UAO. Do not adopt USI. Do not schedule the rename.**

On the evidence, not on preference: lanes B, C and D score identically, so the deciding question
answers zero. Three of five pre-registered tests for the distinction pass, which shows the mechanism
works but not that anything downstream benefits. Because H1/H2 could not be tested at all, the
finding is **"no demonstrated gain"**, not "no gain".

Full reasoning: `research/UAO_USI/consensus/UAO_USI_CONSENSUS_CANDIDATE.md`. ADR-0001 stands
unmodified.

## 16. Next programme — recommended order

1. **Resolve P9-1.** Cheapest, unblocks accumulation, needs only a repository-owner decision.
2. **Escalate the staged-closure option on ASA#29.** Highest value; unblocks H1/H2 and points 2–5 of
   `ASA_INTERFACE.md §2`.
3. **Re-run C/D/E after 1 and 2.** The only way to convert "not testable" into a real answer.
4. **Design provenance-requiring and identity-continuity tasks.** H6 is untestable, not unsupported.
5. **Live-provider field evidence.** One authenticated Claude manufacture into a disposable registry.
6. **Then, and only then, revisit terminology.**

Do **not** proceed to a persistent external demonstration registry: §33 withholds that authority and
nothing here changes it.

## 17. Operator report

This file, plus `temp/PIMA-2026-08-20-EXECUTION-LOG.md` (per-phase log) and
`temp/benchmark/` (raw data). Research record: `research/UAO_USI/`.

---

## Operator conduct notes

- **The ASALLM harness was first written into `~/asallm_empirical/`** because the shell working
  directory had drifted. Moved into this repository and the stray directory removed before any run;
  that repository's tracked state was verified clean and `HEAD` unchanged. Modifying it was never
  authorised.
- **Two `.gitkeep` files** were removed by a local `rm -rf work dist` CI replication and restored.
- **JDK 21 and Maven** were provisioned into the session scratchpad only. No system package was
  installed and nothing was written into the clone.
- **Two comparability corrections were applied against the programme's own interest** — excluding
  reasoning-effort variants that gave lane B four extra runs, and excluding one errored baseline run
  rather than scoring it incorrect.
