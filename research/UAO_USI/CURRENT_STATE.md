# CURRENT_STATE — Repository Truth at Canonical Main

**Status:** RESEARCH / NON-AUTHORITATIVE OPERATOR RECORD
**Creates authority:** No
**Programme:** Persistent Identity Manufacturing Alpha (PIMA)
**Base SHA:** `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e`
**Branch:** `programme/persistent-identity-manufacturing-alpha`
**Date established:** 2026-08-20
**Run label:** `PIMA-2026-08-20`

This document records what the repository *actually contains* at the canonical base, established
by reading the repository rather than by recollection. Every claim below is traceable to a file
at the base SHA. Phase 1 implementation must not begin from any assumption not recorded here.

---

## 1. Governance preconditions — verified

| Precondition | Required | Observed | Verdict |
|---|---|---|---|
| `origin/main` resolves to canonical SHA | `2bc2871d…` | `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` | **PASS** |
| Working tree clean | clean | clean | **PASS** |
| Current tests pass | green | 35/35 Java tests, `BUILD SUCCESS` | **PASS** |
| UAO terminology authoritative | UAO | ADR-0001 Accepted; USI deferred to clean migration | **PASS** |
| ASA#29 unresolved | OPEN | `17th2nd/ASA#29` state `OPEN`, updated 2026-08-09 | **PASS** |
| No new Relationship Type role authority | none | `config/upstream-authority-lock.json` → `relationshipTypeRoleAuthority: NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE`; `17th2nd/uao-foundry#3` OPEN | **PASS** |

**Authority has not materially changed. The programme proceeds against current, not stale, assumptions.**

### 1.1 Local clone discrepancy (resolved, recorded)

At programme start the local clone's `main` was stale at `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb`
(the pre-remediation `#9` merge) and `HEAD` was on `sprint/2026-08-10-audit-remediation-r1` at
`92f9247f`. A `git fetch --all` advanced `origin/main` `cb20687..2bc2871` and additionally
revealed two new upstream refs:

- `origin/backup-control/post-remediation-2bc2871d`
- `origin/backup/main-2026-08-11-post-remediation-2bc2871d`

Both are backup pins of the canonical head, not new authority. The programme branch was cut from
the fetched canonical SHA directly, not from the stale local `main`.

### 1.2 Toolchain gap (environment, not repository)

The workstation has **no Maven and no JDK** — only a JRE (`/usr/lib/jvm/java-21-openjdk-amd64`
contains `java` but no `javac`). `mvn` is absent and `~/.m2` does not exist. A build attempt
against the system Java fails with `release version 21 not supported`.

Resolution: Maven 3.9.9 and Temurin JDK 21.0.12.1+1 were provisioned **into the session
scratchpad only**. No system package was installed, no repository file was changed, and nothing
was written into the clone. This is an operator-environment fact worth recording because it means
*the canonical repository cannot be built on this workstation as-shipped* — a clean-room setup
step for the Phase 11 handoff.

Python `pytest` is also absent, so `adapters/claude-code/tests` cannot be executed locally; those
tests are currently CI-only evidence.

---

## 2. What the repository is

A **Java 21 / Maven** manufacturing system, `org.seventeenthsecond:uao-foundry:0.1.0`, with
**zero runtime dependencies** (JUnit 5.11.4 is test-scope only). 29 main source files,
4 test classes, 5,249 total lines. All JSON parsing, canonicalisation, hashing and JSON-Schema
validation are hand-rolled in-repo (`json/Json.java`, `validation/SchemaValidator.java`,
`util/Hashes.java`). This is a deliberate supply-chain posture and must be preserved: **do not
introduce a third-party dependency to implement persistent identity.**

### 2.1 Verified baseline

```
mvn -B -ntp clean verify   →  BUILD SUCCESS
  SemanticDeltaTest                7/7
  FoundryRegistryTest              7/7
  FoundryApplicationTest          11/11
  PackageVerifierHardeningTest    10/10
  TOTAL                           35/35
```

Fixture manufacture smoke, same compiled JAR:

```
manufacture cow --fixture src/test/resources/fixtures/biological-cow.json
→ rootUaoId uao-7fde0894bfbc, publicationStatus EXPERIMENTAL, verificationPassed true
```

---

## 3. The identity substrate as it exists today

This is the single most important section for the programme. The finding that governs every
later phase is:

> **UID is a pure function of `resolutionKey`.**

`AcquisitionStages.identityResolution` (line 209):

```java
String uaoId = StableIdentifiers.forText("uao", 12, entry.getKey());   // entry.getKey() == resolutionKey
```

which is `"uao-" + sha256(resolutionKey)[0..12]`. Candidate identities are grouped by
`resolutionKey` into a `TreeMap`; each group becomes exactly one UAO.

### 3.1 Consequences that constrain the design

1. **There is no identity resolution layer.** There is only key *derivation*. Two references are
   "the same identity" if and only if a provider supplied the byte-identical canonical
   `resolutionKey`. Nothing in the Foundry decides sameness; the provider does, upstream, and
   the Foundry merely hashes the result.

2. **`SAME` / `DIFFERENT` / `UNRESOLVED` does not exist.** The decision space is binary and
   implicit. There is no record anywhere in the repository answering *"why do we think these
   references are the same object?"* — only the key itself, which is an assertion, not evidence.

3. **MERGE is structurally impossible today.** Because UID is derived from the key, two UIDs can
   never become one without either (a) rewriting immutable packages — forbidden, or (b) adding a
   *mapping layer above the key*. Only (b) is admissible. Merge/split therefore cannot be UAO
   fields; they must be Foundry-owned registry-level identity relations.

4. **Aliases and external identifiers do not route to identity.** They are recorded and indexed
   for *search*, but no manufacture-time path consults them to resolve an incoming reference to
   an existing identity. Section 5 below records that `externalIdentifiers` is worse than inert.

5. **`resolutionKey` continuity is the only reuse mechanism**, and it is guarded by lexical
   name continuity (`FoundryRegistry.validateIdentityContinuity`) and by semantic-variant digest
   equality (`ReuseAnalyzer`). Both are conservative refusal guards, not resolution.

### 3.2 `ResolutionKeys` — the canonical key grammar

`identifiers/ResolutionKeys.java` enforces three namespaces and nothing else:

```
foundry:v0.1:<type-slug>:<label-slug>     ^foundry:v0\.1:[a-z0-9._-]+:[a-z0-9._-]+$
fixture:<type-slug>:<label-slug>          ^fixture:[a-z0-9._-]+:[a-z0-9._-]+$
ext:<scheme>:<identifier>                 ^ext:[a-z][a-z0-9._-]*:[^\s]+$
```

Discipline enforced: NFKC-normalised input required (rejects, does not silently normalise);
no whitespace; `foundry:`/`fixture:` lowercased wholly; `ext:` lowercases only the scheme and
preserves identifier case. Any other namespace is rejected.

The `ext:` namespace is the **already-governed seam for external identifiers**. Phase 1 must use
it rather than invent a parallel mechanism.

### 3.3 The canonical UAO is a closed schema

`schemas/asa/uao.schema.json` is `additionalProperties: false` and is a pinned, explicitly
non-authoritative validation projection of ASA CSS 2026.1 at ASA main `908c5255…`. Permitted
top-level members are exactly:

```
uid  lifecycle_status  successor_identity_ref  internal_state
assertions  relationship_references  provenance  disclaimer
```

**This is a hard boundary. The persistent identity kernel cannot add top-level UAO fields.**
Everything the Foundry owns must live inside `internal_state`, which is the one deliberately
open object (`{"type": "object"}` with no further constraint). Today `internal_state` contains
exactly one member, `foundry_identity`, holding `canonical_label`, `aliases`, `resolution_key`,
`source_refs`.

### 3.4 ASA already supplies part of the lifecycle

`lifecycle_status` is a governed enum: `Registered | Superseded | Retired`, and the schema
carries a conditional — `Superseded` **requires** `successor_identity_ref`. Canonical build
currently hard-codes `"Registered"` for every UAO and never emits a successor reference.

This matters for Phase 4: **SUPERSEDE and RETIRE already have ASA-governed canonical
representations that the Foundry is not yet using.** MERGE and SPLIT do not, and must not be
invented into the canonical UAO. The correct split of work is therefore:

| Operation | Canonical home | Foundry-owned home |
|---|---|---|
| SUPERSEDE | `lifecycle_status: Superseded` + `successor_identity_ref` (**ASA-governed, exists**) | provenance of the decision |
| RETIRE | `lifecycle_status: Retired` (**ASA-governed, exists**) | provenance of the decision |
| MERGE | *none — do not invent* | registry-level identity relation + provenance |
| SPLIT | *none — do not invent* | registry-level identity relation + provenance |

Note also that `SemanticVariants.digest` already includes `lifecycle_status` and
`successor_identity_ref` in the meaning-bearing projection, so lifecycle transitions correctly
register as new semantic variants rather than silently reusing.

---

## 4. The registry as it exists today

`registry/FoundryRegistry.java`, on-disk layout `<root>/packages/<packageId>/` plus
`<root>/index.json`.

### 4.1 The index is fully derived, never authored

`index()` rebuilds the index from the immutable packages on **every read** and refuses to return
a stored index that does not canonically equal the rebuild. `register()` re-verifies, copies the
tree, then rebuilds and rewrites the whole index, deleting the copied tree on any failure
(transactional admission). `requireReusablePackage` re-runs the full `PackageVerifier` on every
package on every index build.

**This is the deepest architectural constraint on Phase 3.** Any persistent identity material —
identity decisions, alias provenance, merge/split records — is *not derivable from package
content* and therefore **cannot be an index field**. It requires a separate, independently
verified, append-only store alongside `index.json`, and the index-equality check must continue
to pass untouched.

### 4.2 Index shape

```
registryVersion, packages[ {packageId, rootUaoId, publicationStatus, packageDigest, path} ],
identities[ {uid, resolutionKey, canonicalLabels[], aliases[], semanticVariantStatus,
             occurrences[ {packageId, canonicalPath, semanticVariantDigest} ]} ]
```

`semanticVariantStatus` is `SINGLE_VARIANT` when all occurrence digests agree, otherwise
`MULTIPLE_UNRECONCILED_VARIANTS`. Occurrences are preserved in full; none is chosen, ranked,
unioned or discarded.

### 4.3 Lookup surface — actual vs. required

| Required by programme §15 | Present today |
|---|---|
| exact UID lookup | **yes** — `search()` match kind `UID` |
| resolutionKey lookup | **yes** — match kind `RESOLUTION_KEY` |
| alias lookup | **yes** — match kind `ALIAS` |
| external identifier lookup | **no** — no external identifier reaches the index |
| identity history | **no** — occurrences only; no decision history |
| state/version occurrences | **partial** — package occurrences exist; no state/version concept |
| provenance | **partial** — package-level; no identity-decision provenance |
| semantic variant state | **yes** |
| immutable package occurrence history | **yes** |
| future relationship bindings | **no** |

`search()` also has a fifth match kind, `TOKEN`, whose fallback is substring-symmetric
(`c.contains(q) || q.contains(c)`). This is **discovery ranking only** and never feeds identity
resolution; it must stay that way.

---

## 5. Finding P0-1 — `externalIdentifiers` is declared but inert

`schemas/candidate-identity.schema.json` declares:

```json
"externalIdentifiers": { "type": "object", "additionalProperties": {"type": "string"} }
```

All four fixtures supply it (always `{}`), and the Claude adapter test emits it. **No Java code
reads it.** `grep -rn externalIdentifiers src/main` returns nothing.

`identityResolution` consumes `candidateId`, `label`, `resolutionKey`, `root`, `aliases`,
`sourceRefs` — and silently drops `externalIdentifiers`. It never reaches `foundry_identity`,
never reaches the canonical UAO, never reaches the registry index, and is not searchable.

**Severity: real but latent.** Today the field is always empty, so nothing is being lost. But a
provider that correctly supplies a durable external identifier — precisely the behaviour
`STABLE-SEMANTIC-IDENTITY.md §2.2` asks for — has that evidence discarded without warning. This
is a fail-*open* silent drop in a codebase that is otherwise rigorously fail-closed.

This is the natural first increment of Phase 1 and closes a genuine gap rather than adding
speculative machinery.

---

## 6. Relationship handling as it exists today

`AcquisitionStages.relationshipConstruction` is 19 lines and does exactly one thing: for every
candidate relationship it emits an unresolved finding and returns an empty canonical URO set.

```java
finding.put("code", "URO_TYPE_AUTHORITY_UNAVAILABLE");
out.put("authorityStatus", "CURRENT_CSS_STRUCTURE_AVAILABLE_TYPE_ROLE_AUTHORITY_UNAVAILABLE");
out.put("canonicalUros", List.of());
```

`publicationDecision` then yields `EVIDENCE_INCOMPLETE` / `eligible: false` whenever any
unresolved relationship exists. `relationship_references` on every canonical UAO is hard-coded
`List.of()`. The Claude adapter refuses non-empty relationship candidates even earlier.

**Critically: participants are never bound to resolved UIDs.** `candidate-relationship.schema.json`
binds participants by `candidateIdentityRef` (`^cid-…`) — a *local bundle handle*, explicitly
declared non-semantic by `STABLE-SEMANTIC-IDENTITY.md §4`. The unresolved finding records only
`candidateId` and the reason code; the participant identities are dropped from the finding
entirely.

This is exactly the Phase 5 gap: a relationship candidate can and should be bound to persistent
UIDs **without** manufacturing a canonical URO. Resolving `cid-x → uao-…` is an *identity*
operation, not a *relationship-type* operation, and needs no ASA#29 authority. The fail-closed
canonical boundary (`canonicalUros == 0`, `URO_TYPE_AUTHORITY_UNAVAILABLE`,
`EVIDENCE_INCOMPLETE`) is preserved untouched; only the evidence quality of the retained
candidate improves.

The relationship candidate schema already carries the ASA#29 extension seams:
`typeVersion` (`^asa\.core/[a-z_]+@[0-9]+$`), named `role` per participant, `identityLiterals`,
`contextualBindings`.

---

## 7. Significance boundary as it exists today

`PipelineBase.rejectForbiddenFields` / `collectForbiddenFields` recursively reject four field
names from canonical structures, per ADR-0002 §5:

```
score   significance_value   belief   stance
```

`epistemic_class` is emitted as the constant `DEFERRED_ON_RECORD`; the Foundry does not mint the
deferred vocabulary. `verification` records the check `FORBIDDEN_FIELD_REJECTION`.

**Gap against programme §27:** the programme's forbidden list is broader than ADR-0002's —
`significance`, `importance`, `priority`, `urgency_score`, `attention_weight`, `reasoning_tier`,
`allocation_score`, `historical_significance`. None of these is currently rejected. Extending
the rejected set is a **Foundry-owned defensive tightening**, not an ASA amendment, and is
admissible because it only narrows what the Foundry will emit. It must be implemented so that
ADR-0002's four remain the ASA-derived core and the programme's additions are clearly labelled
as Foundry-local defence in depth.

There is no significance computation anywhere in the repository, and no `A_x` / `R_x` export
surface. Phase 6 builds the export from existing data; nothing needs removing.

---

## 8. Preserved security properties (programme §28) — baseline located

| Control | Location at base |
|---|---|
| provider projection reconciliation | `verifier/PackageVerifier.java` (580 lines) |
| package content addressing | `verifier/PackageContentDigest.java` |
| immutable source custody | `pipeline/AcquisitionStages`, `provider/SnapshotProvider` |
| registry index verification | `FoundryRegistry.index()` rebuild-and-compare |
| semantic variant detection | `registry/SemanticVariants.java` |
| checkpoint re-derivation | `pipeline/PipelineBase`, `FoundryPipeline` |
| resolutionKey canonical discipline | `identifiers/ResolutionKeys.java` |
| transactional registry admission | `FoundryRegistry.register()` rollback path |
| fail-closed provider containment | `provider/CommandProvider.java`, adapter argv assertions |
| minimum Claude version / env allowlist / MCP denial | `adapters/claude-code/claude_provider.py` |
| no significance persistence | `PipelineBase.rejectForbiddenFields` |

All are load-bearing and none may be weakened for demonstration convenience.

---

## 9. Ordered gap list carried into Phase 1+

| # | Gap | Phase |
|---|---|---|
| G-1 | `externalIdentifiers` accepted, silently dropped (Finding P0-1) | 1 |
| G-2 | No identity kernel distinct from key derivation; no `resolutionKey`→identity indirection | 1 |
| G-3 | No `stateVersion` / no identity-vs-state separation | 1 |
| G-4 | No `lifecycleState` beyond hard-coded `Registered` | 1 / 4 |
| G-5 | No identity decision record; `SAME`/`DIFFERENT`/`UNRESOLVED` absent | 2 |
| G-6 | No identity-decision evidence or reason codes | 2 |
| G-7 | Aliases carry no provenance and are not time-aware | 2 |
| G-8 | Registry cannot store non-derivable identity material | 3 |
| G-9 | No external-identifier lookup in registry | 3 |
| G-10 | MERGE/SPLIT structurally impossible without a mapping layer | 4 |
| G-11 | SUPERSEDE/RETIRE available in ASA schema but unused by Foundry | 4 |
| G-12 | Relationship participants never bound to persistent UIDs | 5 |
| G-13 | No `A_x`/`R_x` export interface | 6 |
| G-14 | No expected-relationship / negative-space records | 7 |
| G-15 | No operator-facing manufacture/search/reuse CLI surface | 8 |
| G-16 | Programme-scope forbidden significance fields not rejected | 1 (test) / 6 |
| G-17 | Repository not buildable on this workstation as-shipped | 11 |

---

## 10. Design rules adopted from repository truth

These are binding on the remainder of the programme and derive from §3–§7 above, not from taste.

1. **No new top-level canonical UAO fields.** The ASA projection is closed. Foundry-owned
   identity material lives under `internal_state`.
2. **No third-party dependency.** The zero-dependency posture is a security property.
3. **Nothing non-derivable goes in `index.json`.** The rebuild-and-compare invariant must keep
   passing byte-for-byte.
4. **Identity resolution becomes explicit and evidence-bearing**, layered *above* `resolutionKey`
   rather than replacing it. The existing derivation stays exactly as it is.
5. **Merge/split are mapping-layer operations**, never rewrites of an immutable package.
6. **Relationship *identity* binding is separable from relationship *type* authority.** The first
   is in scope now; the second remains blocked on ASA#29 and stays fail-closed.
7. **Every addition is fail-closed by default**, matching the surrounding code's posture.
8. **USI is not adopted.** Implementation-neutral naming is preferred where it costs nothing, but
   no rename occurs and no USI abstraction is constitutionalised.
