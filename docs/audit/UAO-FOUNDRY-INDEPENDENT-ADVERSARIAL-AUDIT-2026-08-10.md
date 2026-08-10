# UAO Foundry — Independent Adversarial Audit

**Auditor role:** independent assurance operator (not implementation author)
**Date:** 2026-08-10 (Australia/Brisbane)
**Repository:** `17th2nd/uao-foundry`
**Audited commit:** `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb` (accepted `main`)
**Sprint under audit:** Sprint 2026-08-10 — Claude Integration and Release Readiness
**Canonical `main` was not modified.** All work performed in disposable `/tmp` directories against a throwaway registry.

---

## 1. Audit target and provenance

| Item | Value |
|---|---|
| Live `main` (cloned) | `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb` |
| Live root tree hash | `293f920962cc63c829b24285b6f0ea092ff7b4b5` |
| Backup bundle tree hash at same SHA | `293f920962cc63c829b24285b6f0ea092ff7b4b5` — identical |
| Sprint candidate head | `c8e9289b0e054a43f0ef824e18f6cf350238efb3` |
| Tracked files at target | 97 (manifest, tar and `git ls-tree` all agree) |

Live GitHub, the post-sprint backup bundle, and the backup manifest are in exact three-way agreement on the audited state.

### 1.1 Repository and accession integrity — VERIFIED

`main` is a clean linear history of seven squash-merge commits:

```
cb20687 foundry: complete Claude integration and release-readiness sprint (#9)
f1edd9c foundry: add registry-aware semantic delta v0.1 (#7)
fcb4deb foundry: add immutable verified-package registry v0.1 (#6)
6e3a071 foundry: add vendor-neutral live provider protocol (#5)
1cd6033 foundry: complete functional manufacturing core v0.1 (#2)
d99bc3f foundry: establish manufacturing foundation v0.1 (#1)
2414956 Initial commit
```

No backup-control or recovery artefact entered the authoritative line:

| Commit | Role | Ancestor of `main`? |
|---|---|---|
| `d4fb46eb…` | PR #10 head (post-sprint backup control) | No |
| `819c429a…` | PR #8 head (pre-sprint backup control) | No |
| `fd737a68…` | backup workflow commit | No |

`c8e9289b` (candidate head) is correctly *not* an ancestor — expected under squash merge.

**Note (INFORMATIONAL):** `git ls-remote` shows no `refs/pull/*/merge` refs, consistent with PRs #8 and #10 now being **closed**. They are described in both backup manifests as standing DO-NOT-MERGE control PRs. Nothing is merged and nothing is wrong, but the manifests describe a state that no longer holds.

### 1.2 Evidence pack verification

| Set | Result |
|---|---|
| `asa-authority/SHA256SUMS.txt` | All 4 entries OK |
| `ci-logs/SHA256SUMS.txt` | All 5 entries OK |
| Audit evidence ZIP | `bec3f81ce94b0bdc06bce584e793973f31024d561f2274553f20712b9a44b0c9` |
| Post-sprint backup ZIP | `04a55875…` — matches GitHub artifact digest |
| Pre-sprint backup ZIP | `12421bd0…` — matches GitHub artifact digest |

**Bounded caveat.** The ASA export's checksums were supplied inside the pack. This establishes internal consistency and transport integrity, **not authenticity**. Without independent read access to `17th2nd/ASA` I cannot confirm these files are what sits at `908c5255fb3144c2a2e3f48c993d031e347d1695`. All ASA findings below are conditional on the export being faithful.

### 1.3 Backup and recovery — VERIFIED

Both backup packs verified end-to-end, independently of the Actions runner that produced them:

- Outer ZIP digests match the recorded GitHub artifact digests exactly.
- `sha256sum -c SHA256SUMS.txt` passes inside both packs.
- Post-sprint source tar: 97 files, rooted at `uao-foundry-cb20687d…/`. Manifest list, tar contents and `git ls-tree` at the target SHA are byte-for-byte identical.
- Pre-sprint: 85 files at `f1edd9c7`, same result.
- Both git bundles clone cleanly and contain their target commits with matching messages.

**Recovery finding (MEDIUM, process).** The loose unpacked upload lost material to filename collision: `uao-foundry-full-history.bundle`, `BACKUP-MANIFEST.txt` and `SHA256SUMS.txt` are shared filenames between the two packs, and only the **pre-sprint** copy of each survived. `git bundle list-heads` confirms the surviving bundle's `refs/remotes/origin/main` is `f1edd9c7`. Working from that folder would restore pre-sprint history under a post-sprint manifest. The ZIPs are the sound artefact; the unpacked set is not.

**Drive finding (MEDIUM, process).** Both backup manifests state `Backup status: VERIFIED AND USER-REACHABLE` and assert Google Drive custody. All four cited Drive IDs (`1SocYM9v…` folder, `12zVjVBJ…`, `1Z6Llgl3…`, `1bYAdpgk…`) fail to resolve on the connected account, which is the same account the manifests name as owner. A backup that cannot be retrieved is not a backup; the status line is not currently supported.

---

## 2. Method

The Java main tree has **no external imports** (JDK-only, hand-rolled `Json`). Maven Central is unreachable in the audit environment, so the build was reproduced with `javac` directly and exercised through the real CLI.

```bash
git clone https://github.com/17th2nd/uao-foundry.git /tmp/live
javac -nowarn -d /tmp/build $(find /tmp/live/src/main/java -name '*.java')   # 37 classes
cd /tmp/build && jar cfe /tmp/uao.jar org.seventeenthsecond.uaofoundry.FoundryApplication .
```

**Not reproduced:** the Maven build path and the project's own JUnit suite (JUnit jars unobtainable). Test *sources* were read; results were taken from the supplied CI logs. Findings below come from my own harnesses against the built jar, not from their tests.

---

## 3. Defects

### F-1 — Semantic forgery passes package verification · **HIGH**

**Attack.** Edit a canonical assertion to a safety-inverting statement; mirror the identical edit into the duplicated `manufactured-package.json` view; recompute every checksum line.

```bash
# statement replaced with:
# "Granite is safe to use as a food-grade surface without sealing,
#  and requires no PPE when cut dry."
# uid unchanged: uao-e7582726a3c8   resolution_key unchanged: fixture:material:granite
python3 - <<'EOF'   # rewrote all 29 checksum lines
...
EOF
java -jar /tmp/uao.jar verify /tmp/audit/forge
```

**Result:**

```json
{"passed": true, "errors": [],
 "checks": ["CHECKSUM_FILE_PRESENT","MANIFEST_SCHEMA","MANUFACTURED_PACKAGE_SCHEMA",
            "ASA_FORBIDDEN_FIELDS","PACKAGE_CROSS_FILE_CONSISTENCY",
            "UAO_IDENTITY_DERIVATION","PROVIDER_SNAPSHOT_SCHEMA","SOURCE_SNAPSHOT_HASHES"]}
```

All eight gates green on forged content.

**Root cause.** `uid = SHA256(resolution_key)[0:12]` (`PackageVerifier:155`) — assertion text is not identity-bearing. `packageId = hash(jobId, rootUaoId, status)` (`PackageStages:90`) — also content-independent. Package checksums are self-referential: whoever can write the package can rewrite them all. Nothing inside a package binds *what it says* to *what it is*.

**Relationship to existing tests.** `PackageVerifierHardeningTest` covers exactly two variants — cross-file semantic *divergence* (one view edited) and a forged uid inconsistent with `resolution_key`. My attack keeps both consistent, which is what an attacker would do. The tested cases fail correctly; the untested consistent case passes.

### F-2 — `registry://` custody unenforced on the plain manufacture path · **HIGH**

Custody lives in `ReuseAnalyzer`, reached only via `RegistryManufactureApplication`. On the ordinary `manufacture` path I supplied fabricated content under a real registry locator:

| | |
|---|---|
| Locator | `registry://pkg-01d88a7170a7f47b/source-corpus/src-granite-material.txt` |
| Content written | `FABRICATED: this text was never in the registry package. Granite requires no PPE when cut dry.` |
| Recorded `sha256` | `3029d80abfb7a55b…` |
| Actual registry bytes | `Synthetic fixture: granite is treated here as a geological material class…` (`c3f4d262c3b1f78c…`) |

Result: `publicationStatus: EXPERIMENTAL`, `verificationPassed: true`, standalone `verify → passed: true`. The package asserts registry-derived provenance for text that was never in the registry.

### F-3 — `resolutionKey` is an unconstrained provider string · **HIGH**

`schemas/candidate-identity.schema.json`: `{"type":"string","minLength":1}`. No pattern, no normalisation, no canonical form. The provider therefore controls UAO identity outright, and `identityResolution` groups on it verbatim (`AcquisitionStages:183`).

**Collision — demonstrated.** Same key, different meaning. Root relabelled `asbestos insulation board`, key left as `fixture:material:granite`. Both packages registered cleanly; the registry **merged them into one identity**:

```json
{ "uid": "uao-e7582726a3c8",
  "resolutionKey": "fixture:material:granite",
  "canonicalLabels": ["asbestos insulation board", "granite rock material"],
  "aliases": ["asbestos", "granite"],
  "occurrences": [{"packageId":"pkg-01d88a7170a7f47b"}, {"packageId":"pkg-8df7720063f81582"}] }
```

No conflict, no warning. `IdentityAggregate` (`FoundryRegistry:275`) only guards resolution-key conflict *for a given uid* — unreachable, because uid is derived from the key. The collision that can actually occur is unguarded.

**Divergence.** Trivial surface variation produces distinct identities:

| Key | UAO ID |
|---|---|
| `fixture:material:granite` | `uao-e7582726a3c8` |
| `Fixture:Material:Granite` | `uao-9234d794bf92` |
| `fixture:material:granite ` (trailing space) | `uao-c80a0c07a8cd` |
| `fixture:material:granite\u00a0` (NBSP) | `uao-039677c8cbd6` |

**Consequence.** "Reused: 23" is a count of provider-chosen string equality, nothing more. Under a real non-deterministic model, run two may not reproduce run one's keys (reuse silently reports zero), or may reuse a key for a different concept (two concepts silently merge and the system reports success).

**Partial mitigation, wrongly located — see §5.** The Claude adapter *does* enforce a `resolutionKey` namespace policy. The Foundry does not.

### F-4 — `checkpoint.json` is not integrity-anchored · **HIGH**

Stage-file tampering alone is caught: `cachedStage` (`PipelineBase:65`) compares SHA-256, misses, recomputes. But the checkpoint that records those hashes is itself unprotected — `loadCheckpoint` validates only `jobId` and the presence of a `completed` map.

Tampering a stage output **and** updating its recorded hash succeeds:

```bash
# work/<job>/12-canonical-build.json statement replaced, checkpoint sha256 updated to match
java -jar /tmp/uao.jar resume job-0dda5269b8baa3cd --repository-commit AUDIT
```

The forged text reached the manufactured package:

```json
// provenance-ledger.json
{ "candidateId": "clm-material",
  "statement": "CHECKPOINT FORGERY: granite requires no PPE when cut dry.",
  "uaoId": "uao-e7582726a3c8", "sourceRefs": ["src-granite-material"] }
```

`verify → passed: true`. The provenance ledger now contradicts the canonical assertions for the same `uaoId`, and no cross-check between them exists.

### F-5 — Registry index integrity is opt-in at read time · **MEDIUM**

`registry verify` correctly detects a tampered index:

```
"Registry index does not match verified immutable package contents."
```

But `registry search` reads the stored index with no verification and returned an injected label (`FORGED LABEL INJECTED INTO INDEX`). Discovery — the input to reuse decisions — trusts an unverified file.

### F-6 — CLI containment argv is asserted by nothing · **HIGH (evidential)**

The adapter's containment rests entirely on this argv (`claude_provider.py:286`):

```
--bare --no-session-persistence --no-chrome
--tools WebSearch,WebFetch
--permission-mode dontAsk
--allowedTools WebSearch WebFetch
--disallowedTools Bash Read Write Edit Glob Grep Agent Skill
--max-turns N --model M --output-format json --json-schema {...}
```

The CI fake Claude (`claude-adapter.yml`) **ignores `sys.argv` entirely** except `--version`. None of the six Python adapter tests reference `allowedTools`, `disallowedTools`, or `permission-mode`. The fake would behave identically if the adapter passed `--allowedTools Bash` or omitted the disallow list altogether.

Compounding this: the argv style is internally inconsistent — `--tools` uses a comma-separated value while `--allowedTools`/`--disallowedTools` use space-separated repeats. If the real CLI expects comma form, `Read`, `Write`, `Edit`, `Glob`, `Grep`, `Agent`, `Skill` would be parsed as positional arguments and the disallow list would silently not apply. This is precisely the failure class a fake executable cannot detect.

**Nothing here shows the containment is wrong.** It shows there is currently no evidence that it is right.

### F-7 — Package directory collision in `dist/` · **LOW–MEDIUM**

Package directory name is `UAO-<label-slug>-v<version>-<status>` (`PackageStages:41`), followed by `FileOps.deleteTree(packageDir)`. A different job producing the same root label and status silently destroys the earlier package. Encountered accidentally during this audit.

### F-8 — Tamper rejection is silent · **INFORMATIONAL**

A rejected checkpoint stage produces no message. The only signal is `resumedStages` falling (14 → 13). An operator would not notice, and under F-4 the count is not affected at all.

---

## 4. What held under attack

| ID | Property | Evidence |
|---|---|---|
| H-1 | **URO fail-closed** | `relationshipConstruction` unconditionally returns `canonicalUros = List.of()` and emits `URO_TYPE_AUTHORITY_UNAVAILABLE` per candidate (`AcquisitionStages:230-244`). Not a conditional — no provider input can flip it. |
| H-2 | **Publication containment** | `publicationDecision` derives status purely from Foundry-computed state (`CanonicalStages:214`). Provider cannot set it. Any unresolved relationship forces `EVIDENCE_INCOMPLETE`, `eligible: false`. |
| H-3 | **Registry immutability** | Registering the F-1 forged package under its existing `packageId` rejected: *"Registry package-id collision with different immutable content."* Tree-hash comparison over all regular files. |
| H-4 | **Registry-aware custody + traversal** | `resolveRegistrySource` performs known-package lookup, `normalize()` + `startsWith` containment, existence check, and hash match. Because `PackageVerifier.verifySourceSnapshots` independently requires the snapshot bytes to hash to the same declared value, snapshot bytes are cryptographically bound to registry bytes on that path. Sound. |
| H-5 | **Candidate quarantine** | Malformed evidence quarantined with precise schema errors, excluded from canonical build, forced `QUARANTINED` / ineligible. No downstream re-entry observed. |
| H-6 | **Resume determinism** | Clean resume reproduced a byte-identical package (`diff -ru` clean; 14 stages reused). Naive stage tampering rejected. |
| H-7 | **Domain independence** | Main tree is JDK-only with no domain vocabulary; CI greps `src/main/java` for demonstration identities and for TAFE terms. Genuinely domain-independent. |
| H-8 | **Adapter registry byte restoration** | `_restore_registry_sources` overwrites model-supplied `content` with adapter-held bytes and forces `license` and `sourceClass`. CI proves it adversarially: the fake returns `FAKE MODEL TRANSCRIPTION MUST BE REPLACED BY ADAPTER` and the workflow greps the output for `UAO-FOUNDRY-REGISTRY-SNAPSHOT`. Log line 619 confirms. This is a well-designed test. |
| H-9 | **Adapter relationship refusal** | Non-empty relationship candidates are refused at the adapter *and* forced to `[]`, independently of the Java fail-closed path. Genuine defence in depth. |

---

## 5. Provider and adapter containment

### 5.1 What the adapter enforces (code, not comments)

- **Protocol preconditions.** Refuses to run unless `constraints.canonicalWriteAllowed is False` and `responseRole == "INTERMEDIATE_PROVIDER_BUNDLE_ONLY"`.
- **Output constraint.** `--json-schema` with the Foundry bundle schema; all 11 required bundle fields re-checked after return.
- **Relationships.** Hard refusal on non-empty, then forced `[]`.
- **`resolutionKey` policy.** New keys must be `ext:*` or `foundry:v0.1:*`; `EPHEMERAL_KEY_PATTERN` rejects UUIDs, model/session/turn tokens and timestamps. Registry-catalog keys pass through unchanged.
- **Registry evidence.** Bounded (default 8 files / 1 MB, capped at 100 / 8 MB), path-contained against the registry root, UTF-8 enforced, exact bytes restored post-response.
- **Resource bounds.** `max-turns` 1–100, timeout 1–3600s, optional `--max-budget-usd`. Java side additionally caps stdout at 16 MB and stderr at 256 KB, and rejects timeouts outside 1s–1h.
- **Output discipline.** stdout carries protocol data only; diagnostics to stderr.
- **Normalisation.** `identitySeed`, `fixedClock`, `knowledgeHorizon` are overwritten from the envelope — the model cannot set them.

This is a well-constructed containment layer, and the Java side re-validates everything the adapter returns. The trust boundary is genuinely enforced twice.

### 5.2 Architectural inversion — **MEDIUM**

The `resolutionKey` discipline that makes cumulative reuse coherent (F-3) exists **only in the adapter** — that is, inside the component being contained. The Foundry, which owns identity, accepts any non-empty string from any provider. A fixture, a different provider command, or a hand-written request bypasses the policy entirely; that is exactly how I produced the F-3 collision. Identity policy belongs in the Foundry's schema and identity-resolution stage, with the adapter as a redundant early check.

### 5.3 Residual adapter observations

- **Environment inheritance (LOW).** `_claude_environment()` copies the full parent environment into the subprocess. Credentials and unrelated secrets flow through unfiltered.
- **Wrapper fallback (LOW).** If `structured_output` and `result` are both absent but all 11 required fields appear at the wrapper top level, the whole wrapper is accepted as the bundle — a small loosening of the schema-constrained guarantee.
- **`--permission-mode dontAsk` (INFORMATIONAL).** Non-interactive by necessity. Safe only to the extent the tool allowlist actually applies — which is F-6.

---

## 6. ASA authority findings

Checked against the supplied export at `908c5255`.

**The authority-lock is accurate.**

| Lock field | Verified against export |
|---|---|
| `canonicalSchemaVersion: 2026.1` | Matches CSS `$meta.schema_version` |
| `canonicalSpecificationSource` | Path matches |
| `relationshipArchitecture` (ADR-002) | Path matches |
| `compilerSpecification` (ASA-SPEC-0004) | Path matches |
| `relationshipTypeRoleAuthority: NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE` | **Confirmed accurate** |
| `asaMainCommitReviewed: 908c5255…` | Not independently verifiable |

**Basis for the confirmation:**

- **CSS** defines `URO` structurally: `identity.type_version` constrained to `^asa\.core/[a-z_]+@\d+$`, and `participants[]` with `role` as a **free-form string** — no cardinality, no permitted participant kinds, and no Relationship Type registry anywhere in the file.
- **ADR-002** §3.4, §3.5, §6, §5 assign role schemas, per-role cardinality, symmetry, and identity-bearing field declarations to Relationship Type *versions* — as doctrine. No machine-readable instance exists.
- **ASA-SPEC-0004** (line 109-110) requires the compiler to *validate participant roles against the declared relationship type*. That requirement is **currently unsatisfiable**, because the declared types do not exist.

**The Foundry does not create a parallel authority.** `config/upstream-authority-lock.json` is explicitly marked `authorityCreatedHere: false` and *"evidence/provenance only"*. Validation projections stay within CSS structure. This is the strongest and most honest part of the system: it refuses to manufacture authority it does not possess, and it says so accurately.

---

## 7. Relationship-heavy qualification experiment

Constructed a realistic Certificate III Electrotechnology shape in a disposable workspace: 17 candidate identities (qualification + 10 units of competency + 6 concepts), 17 claims, 26 relationships (10 `contains`, 6 `prerequisite_of`, 10 `teaches`), with jurisdiction contextual bindings.

```
publicationStatus : EVIDENCE_INCOMPLETE
canonical UAOs    : 17
canonical UROs    : 0
unresolved items  : 26  (26 of 26)
relationship_references on every UAO : []
eligible          : false
reason            : "Relationship candidates await authoritative
                     Relationship Type role declarations."
exit code         : 4
```

**Every relationship failed closed. One hundred percent of the qualification structure is absent from the manufactured package.** What survives is 17 disconnected identities — no packaging rules, no prerequisite chain, no unit-to-concept mapping.

Because *any* unresolved relationship forces `EVIDENCE_INCOMPLETE`, **a realistic qualification manufacture is structurally incapable of reaching `EXPERIMENTAL`** under current ASA authority.

**Why this has not surfaced before:** all three CI fixtures (`biological-cow`, `material-granite`, `cultural-pie`) contain **zero** relationships. The fail-closed path is exercised by exactly one unit test and never by the cross-domain manufacture on which the "proven" claims rest. The proposed operator UI mock showing `Unresolved items: 2` is off by an order of magnitude for this class of subject.

---

## 8. CI evidence assessment

All four supplied logs correspond to candidate head `c8e9289b0e054a43f0ef824e18f6cf350238efb3`, contain no `##[error]` entries, and report `BUILD SUCCESS`. JUnit: **20 tests, 0 failures, 0 errors, 0 skipped** (`FoundryApplicationTest` 11, `SemanticDeltaTest` 4, `FoundryRegistryTest` 3, `PackageVerifierHardeningTest` 2).

**What the workflows genuinely prove** (read from definitions, not run IDs): cross-domain manufacture across three unrelated fixtures on one compiled binary; byte-identical repeat manufacture; resume with `resumedStages >= 10`; tamper negative control; a production-code grep barrier against demonstration identities; adapter registry-byte restoration under an actively hostile fake (H-8).

**What they do not prove:**

- **Tool containment** — F-6. The fake ignores argv.
- **Semantic reuse** — the reuse test manufactures the *same seed twice*, so `reusedUaoCount >= 1` follows from trivially identical `resolutionKey`s. It proves plumbing, not semantic identity.
- **Relationship handling** — no fixture contains a relationship (§7).
- **Consistent-view forgery resistance** — F-1.
- **Live Claude behaviour, or correctness of any retrieved knowledge.**

---

## 9. Claim classification

**Mechanically verified:** deterministic repeat manufacture; checkpoint/resume; cross-domain fixture manufacture; naive tamper rejection; registry immutability against content substitution; candidate quarantine containment; URO fail-closed; publication-status containment; adapter restoration of registry evidence bytes; JSON Schema contract enforcement.

**Architecturally supported by current authority:** UAO structural conformance to CSS; the ASA-subordination posture and authority-lock accuracy; `registry://` custody on the registry-aware path.

**Demonstrated only under fixtures / fake Claude:** the entire provider protocol; adapter processing; semantic reuse machinery; semantic-delta accounting. Additionally — and this is new — *all* cross-domain manufacture evidence, since every fixture is relationship-free and therefore never reaches the one gate a real subject hits.

**Overstated as currently written:**
- *"package semantic-forgery protection"* — defeated by F-1.
- *"cryptographically verified `registry://` reuse"* — true on one of two paths (F-2).
- *"reuse before reinvention"* / *"semantic delta"* — rest entirely on an unconstrained provider string (F-3).
- *"universal / cumulative knowledge reuse"* — not established as a semantic property.
- *Claude adapter restricts Claude Code to a research surface* — plausible and well-written, but currently unevidenced (F-6).

**Blocked:** arbitrary URO manufacture — correctly, verifiably, and more consequentially than the roadmap assumes (§7).

**Not established by anything in this system:** correctness of external knowledge. Package verification is a structural property. A fully verified package can assert that granite requires no PPE — I built one.

---

## 10. Remediation priority

Ordered by risk to the TAFE demonstration and to the integrity claims.

1. **Bind semantic content to identity** (F-1). Record a content digest over canonical assertions/claims in the manifest and recompute it in the verifier. Until then "verified package" means only "internally consistent".
2. **Move `registry://` custody into the core acquisition stage** (F-2), so it applies on every manufacture path rather than only the registry-aware one.
3. **Constrain `resolutionKey` in the Foundry** (F-3, §5.2). Schema pattern plus Unicode/case/whitespace normalisation; make the registry **reject** rather than silently merge a key whose canonical-label set diverges. Keep the adapter check as redundancy. This gates the entire cumulative-reuse story.
4. **Assert the containment argv** (F-6). Make the CI fake validate `sys.argv` against the expected allow/disallow sets and fail if absent; separately, verify the real CLI's expected value format (comma vs repeated) before any live run.
5. **Anchor the checkpoint** (F-4). Digest or sign `checkpoint.json`; cross-check `provenance-ledger` statements against canonical assertions in the verifier.
6. **Verify the registry index on read** (F-5), not only on demand.
7. **Add a relationship-bearing fixture to CI** (§7). Cheap, and would have surfaced the qualification result months ago.
8. **Housekeeping:** `dist/` collision (F-7); loud tamper rejection (F-8); environment scrubbing and wrapper-fallback tightening (§5.3); correct the backup manifests' Drive claims and the standing-PR description (§1.1, §1.3).

---

## 11. Verdict

| Layer | Verdict |
|---|---|
| Repository and accession integrity | **VERIFIED** |
| Backup and recovery evidence | **VERIFIED** (loose unpacked set and Drive claims excepted — §1.3) |
| UAO manufacturing core — domain independence, determinism, resume, quarantine | **VERIFIED** |
| ASA authority-lock accuracy | **VERIFIED WITH BOUNDED FINDINGS** (conditional on an export I cannot authenticate) |
| Arbitrary URO manufacture | **BLOCKED BY ASA AUTHORITY DEPENDENCY** — correctly and honestly enforced |
| Package verification as an integrity guarantee | **NOT VERIFIED** — defeated by F-1 |
| `registry://` evidence custody | **NOT VERIFIED** on the plain path (F-2); **VERIFIED** on the registry-aware path |
| Cumulative reuse / semantic delta as a semantic property | **NOT VERIFIED** (F-3) |
| Work-directory / resume integrity | **NOT VERIFIED** (F-4) |
| Claude adapter tool containment | **NOT VERIFIED** — design is sound, evidence is absent (F-6) |
| Realistic qualification manufacture | **BLOCKED** — 26/26 relationships unresolved, package structurally ineligible |
| Live Claude workstation operation | **NOT YET FIELD-VERIFIED** |

**Overall: CONDITIONAL PASS as an engineering artefact; NOT READY for external demonstration on its current integrity claims.**

The architecture is sound and the authority discipline is real — the fail-closed behaviour is the most trustworthy thing in the system, and the double containment of relationship emission is genuinely well built. What is not yet true is the *evidential* layer around it: three of the four headline integrity claims can be defeated by an attacker who reads the code, and the fourth is untested.

The decision to audit before populating a persistent registry was correct, and for a sharper reason than originally stated: a Cert III run would have populated an immutable store under F-3's identity regime, then built a demonstration on `reuse-report.json` figures that F-3 renders unsound — and every resulting package would have been ineligible for publication anyway.

---

## 12. Audit scope limits

- No independent access to `17th2nd/ASA`; all ASA findings conditional on the supplied export.
- The project's Maven build and JUnit suite were not executed (Maven Central unreachable); test sources were read and CI logs used for results.
- Live Claude Code was not exercised — no authenticated installation available, and doing so was out of scope.
- CI log *bodies* were read for results and errors; individual step transcripts were not exhaustively reviewed.
- `SchemaValidator` was exercised through the pipeline but not independently fuzzed.
- No remediation was designed or applied. `main` was not modified.
