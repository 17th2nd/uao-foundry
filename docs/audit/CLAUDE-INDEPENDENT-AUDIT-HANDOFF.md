# Independent Claude Audit Handoff — UAO Foundry Sprint 2026-08-10

**Audit mode:** READ-ONLY / INDEPENDENT / NO DESIRED VERDICT  
**Repository:** `17th2nd/uao-foundry`  
**Pull request:** `#9` — `Foundry sprint: Claude integration and release readiness`  
**Frozen pre-sprint base:** `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`  
**Known fully-green implementation head:** `3e09a26571211327c4f45bd26147f2f9dedea3a8`  
**Audit target:** the exact final PR #9 head, or the final merged `main` commit if the audit is performed after merge.

This packet requests an independent audit. It does **not** request approval, defense of the design, or a particular outcome. A finding that invalidates a sprint claim is a successful audit result if supported by reproducible evidence.

## 1. Mandatory authority-first reading

Before evaluating code, **read the repository constitution/governing authority surface first wherever one exists.** Do not infer authority from implementation, examples, branch names, generated files, issue text or this audit packet.

For the Foundry, begin with:

```text
README.md
docs/UAO-MANUFACTURING-ARCHITECTURE-v0.1.md
docs/ADR-0001-UAO-TERMINOLOGY.md
docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md
docs/UPSTREAM-DEPENDENCIES.md
docs/PROVIDER-PROTOCOL.md
docs/REGISTRY.md
docs/SEMANTIC-DELTA.md
docs/STABLE-SEMANTIC-IDENTITY.md
config/upstream-authority-lock.json
```

Where a claim depends on ASA, independently inspect the current authoritative ASA surface rather than accepting the Foundry's summary. At minimum inspect the authority identified by the lock record and any current repository authority/constitution instructions, including as relevant:

```text
17th2nd/ASA
specification/core/canonical_source.json
00_Governance/ADR/ADR-002_Relationship_Ontology_and_Ownership.md
toolchain/COMPILER_FANOUT_HANDOFF.md
```

Do not treat draft/review SPEC-0018 material as current implementation authority merely because it contains richer type definitions.

## 2. Read-only constraint

Do not edit, commit, push, merge, rebase, amend, tag, close issues, change branch protection, regenerate authority files, or repair findings during this audit unless separately authorized by the project owner.

You may create an external audit report outside the repository if needed. Prefer reproducing commands in a temporary clone/worktree.

## 3. Establish the exact audit object

Record before analysis:

- repository URL;
- exact audited commit SHA;
- PR #9 base SHA and head SHA, if auditing the PR;
- whether the worktree is clean;
- all changed paths from base `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`;
- whether the audited head is an ancestor of / identical to the claimed merged `main` state;
- workflow files and workflow run IDs relied upon.

Do not accept `docs/audit/SPRINT-2026-08-10-EXECUTION-REPORT.md` as proof by itself. Use it as a claim register to test.

## 4. Backup and recoverability audit

Independently assess whether the pre-sprint backup evidence is sufficient and reachable.

Claimed pre-sprint recovery surfaces:

```text
GitHub preservation branch:
backup/main-2026-08-10-f1edd9c7

Google Drive folder:
https://drive.google.com/drive/folders/1SocYM9vLcNgWbMJZ4hKI0vvj6lDZZ6Fr

Pre-sprint ZIP:
https://drive.google.com/file/d/1Z6Llgl3jyrv58ddArr46MNPjoR5WYlRB/view

Recovery manifest:
https://docs.google.com/document/d/1bYAdpgk7V7eX0kzo2DuiZeqT4Njj6v1TeFFqgox_aZ8/edit
```

If you have access, verify:

- ZIP can be obtained;
- `SHA256SUMS.txt` validates;
- the source archive contains the base tree;
- the Git bundle can be cloned;
- the bundle contains exact base SHA `f1edd9c7...`;
- backup material does not masquerade as semantic authority.

If Drive access is unavailable to the auditor, record that as an audit limitation rather than assuming reachability.

## 5. Authority and terminology audit

Test independently:

1. UAO remains the prototype term.
2. Nothing in this sprint silently renames UAO to USI.
3. The later UAO→USI review remains deferred to a clean university/research/partnership migration.
4. The Foundry's checked-in ASA schemas remain validation projections rather than claims of independent ASA authority.
5. No provider, registry or example file is treated as constitutional/semantic authority.

Report any wording that overstates authority even if runtime behavior is correct.

## 6. Relationship Type / URO authority audit

Investigate `17th2nd/uao-foundry#3` and `17th2nd/ASA#29` independently.

Test the sprint claim that current ASA authority still lacks the general machine-readable arbitrary-domain Relationship Type registry required to validate:

- type versions;
- named participant/contextual roles;
- min/max cardinalities;
- participant kinds;
- identity-bearing roles/literals;
- symmetry/order semantics;
- compatibility/version behavior.

Confirm that:

- generic Java Foundry preserves unsupported relationship candidates as unresolved and excludes them from canonical URO publication;
- the Claude adapter fails earlier on non-empty relationship candidates;
- no sprint file invents a substitute type authority;
- CC-0/test type definitions are not silently promoted into general authority.

If the required authority actually exists, identify the exact authoritative path, commit and standing; that would invalidate the current upstream-gap claim and should be reported prominently.

## 7. Claude Code CLI integration audit

Independently compare the adapter command line against current official Claude Code CLI documentation/version available at audit time.

Inspect:

```text
adapters/claude-code/claude_provider.py
adapters/claude-code/README.md
```

Test whether the claimed controls actually mean what the sprint says they mean, especially:

- non-interactive print mode;
- structured JSON / JSON Schema output;
- `--tools WebSearch,WebFetch` as an availability restriction;
- `--permission-mode dontAsk`;
- `--allowedTools` and `--disallowedTools` interaction;
- `--bare`;
- `--no-session-persistence`;
- `--no-chrome`;
- model and turn limits;
- optional budget flag.

Check for deprecated/invalid flags or semantics that changed after the sprint.

Do not infer that these flags constitute a complete security sandbox. Audit the narrower claim only: the adapter intends to expose Claude to web research, not repository/shell mutation tools.

## 8. Credential and process-boundary audit

Search the entire PR diff and history for:

- API keys;
- OAuth tokens;
- cookies;
- provider credential files;
- accidental environment dumps;
- hard-coded local secrets;
- unsafe shell interpolation.

Inspect the temporary executable launcher created by `scripts/manufacture-claude.sh` and determine whether it preserves the existing Java exact-executable provider invariant without opening an unintended command-injection path.

Test spaces/metacharacters in repository paths where practical.

## 9. Provider authority/custody audit

Trace one provider transaction end-to-end.

Verify that:

- provider protocol explicitly denies canonical write authority;
- Claude returns only the intermediate provider bundle;
- Java schema/candidate validation still executes after the adapter;
- Java identity resolution creates canonical UAO IDs;
- Java verification/publication remains downstream;
- provider snapshot is packaged/checksummed;
- resume replays provider snapshot rather than re-invoking original provider.

Attempt to identify any path by which a provider could directly mark its own content canonical or publication-eligible without Foundry gates.

## 10. Registry evidence custody audit

Trace a registry-aware transaction.

Attempt to falsify the claim that a model cannot alter prior registry evidence while retaining a trusted `registry://` locator.

At minimum test/inspect:

1. registry verifies before live provider invocation;
2. adapter bounds registry evidence by file/byte limits;
3. evidence path cannot escape registry root;
4. adapter restores exact prior bytes after model output;
5. Java `ReuseAnalyzer` independently resolves the `registry://` locator;
6. SHA-256 of provider source bytes matches prior immutable package bytes;
7. malformed/prefix-only/unregistered locators fail closed.

Check for symlink/path-canonicalization issues, package-ID confusion, duplicate occurrence ambiguity and TOCTOU assumptions.

## 11. Stable semantic identity audit

Read:

```text
docs/STABLE-SEMANTIC-IDENTITY.md
```

Check implementation against the stated discipline:

- exact registered `resolutionKey` reuse;
- external key shape `ext:<scheme>:<identifier>`;
- new local key shape `foundry:v0.1:<semantic-type>:<canonical-label>`;
- obvious UUID/model/session/timestamp material rejection;
- local candidate IDs are not cross-package identity;
- no fuzzy registry heuristic silently merges distinct identities.

Challenge the policy for collisions, Unicode/case/normalization, aliases, external-identifier drift and model-created semantic-type/label variation.

Distinguish a useful v0.1 discipline from a claim of universal identity equivalence.

## 12. Semantic-delta audit

Verify that reuse is computed by the Foundry rather than accepted from model text.

Inspect:

```text
src/main/java/org/seventeenthsecond/uaofoundry/reuse/
schemas/reuse-report.schema.json
.github/workflows/semantic-delta.yml
```

Test:

- identical UAO IDs classify as reuse;
- new UAO IDs classify as new;
- registry state participates in transaction identity;
- `registry://` evidence must resolve/hash correctly;
- provider cannot force `reusedUaoCount` by self-report;
- prior provenance remains in immutable prior packages.

## 13. Package verifier hardening audit

Inspect:

```text
src/main/java/org/seventeenthsecond/uaofoundry/verifier/PackageVerifier.java
src/test/java/org/seventeenthsecond/uaofoundry/verifier/PackageVerifierHardeningTest.java
```

Reproduce, or devise stronger versions of, the re-hashed-forgery negative controls.

The intended checks include:

- duplicated package UAO/URO/request/publication/verification views agree;
- root identity consistency;
- deterministic `resolution_key` → UAO UID recomputation;
- semantic forgery remains rejected even when the attacker recomputes every checksum in the package.

Look for any other semantic duplicated views not cross-checked, including identity-resolution/provenance/source/provider-snapshot surfaces.

## 14. Registry hardening audit

Inspect registry registration, verification and rebuild behavior.

Test whether the registry could silently aggregate:

- same UAO UID with different resolution keys;
- same package ID with different content;
- symlinked files;
- path traversal;
- colliding aliases/labels;
- duplicate package occurrences;
- altered package contents followed by index rebuild.

Classify any remaining collision risk separately from ordinary cryptographic collision assumptions.

## 15. TAFE demonstration isolation audit

Inspect:

```text
examples/tafe/README.md
```

Verify that demonstration identities such as `Certificate III Electrotechnology` and `Electric Vehicle Maintenance` appear only in example/test/documentation surfaces and do not create domain branches in generic implementation.

Check that the guide does not promise a reuse result before the actual `reuse-report.json` exists.

## 16. CI/release-engineering audit

Audit every workflow relevant to PR #9.

The sprint discovered and repaired stale `0.1.0-SNAPSHOT.jar` paths in provider/semantic-delta workflows. Confirm no stale artifact-name assumptions remain.

At minimum independently verify the exact final PR head receives successful runs for:

```text
UAO Foundry CI
UAO Foundry provider protocol
UAO Foundry semantic delta
UAO Foundry Claude Code adapter
```

Do not use workflow status from an older commit to approve the final head.

Review the workflow tests themselves for vacuity: ensure each asserted property could actually fail if the protected behavior regressed.

## 17. Real-provider limitation

The CI intentionally uses a fake Claude executable; it proves adapter/process/Foundry integration but not a live authenticated Claude service interaction.

Treat these as separate claims:

- **mechanized adapter integration:** testable in CI;
- **real Claude workstation/network compatibility:** requires an authenticated external smoke run.

Flag any sprint wording that conflates them.

## 18. Documentation/audit-claim audit

Cross-check:

```text
README.md
docs/IMPLEMENTATION-ROADMAP.md
docs/audit/SPRINT-2026-08-10-EXECUTION-REPORT.md
this handoff
```

Build a claim/evidence matrix. Mark each material claim:

- VERIFIED;
- PARTIALLY VERIFIED;
- UNVERIFIED;
- CONTRADICTED;
- OUT OF SCOPE.

Pay particular attention to words such as `canonical`, `verified`, `immutable`, `authority`, `safe`, `deterministic`, `reuse`, `complete`, `release-ready`, and `universal`.

## 19. Recommended reproduction commands

Use a clean clone/worktree and exact audited commit. At minimum:

```bash
mvn -B -ntp clean verify
mvn -B -ntp package -DskipTests
python3 -m unittest discover -s adapters/claude-code/tests -p 'test_*.py' -v
```

Then reproduce the relevant workflow command surfaces rather than assuming GitHub's green badge proves the intended invariant.

If a real authenticated Claude Code environment is available to the independent auditor, run:

```bash
bash scripts/preflight-live.sh
```

A real manufacture should be treated as additional external-integration evidence, not as a substitute for the deterministic/fake-provider tests.

## 20. Required audit output

Return an external report containing:

1. exact repository + commit audited;
2. authority documents read and their standing;
3. reproduction environment;
4. tests/commands actually run;
5. verdict chosen independently from:
   - `PASS`
   - `PASS WITH FINDINGS`
   - `FAIL`
6. findings ordered by severity:
   - Critical
   - High
   - Medium
   - Low
   - Informational
7. for every finding:
   - exact path/line or artifact;
   - reproduction steps;
   - expected vs actual behavior;
   - authority/invariant affected;
   - whether it blocks merge, release or only documentation;
8. claim/evidence matrix;
9. residual-risk list;
10. independent merge/release recommendation;
11. SHA-256 of the final external audit report if practical.

Do **not** soften a reproducible defect because the sprint was intended to be complete. Do not elevate a preference into a defect without naming the violated authority or stated acceptance invariant.

## 21. Current known limitations to verify, not merely repeat

The sprint authors currently acknowledge:

- arbitrary-domain URO role authority is unresolved upstream;
- live Claude factual correctness is not proven by package structure;
- CI uses fake Claude, not the project owner's authenticated Claude account;
- a specific TAFE reuse count is not known until a real demonstration run;
- the adapter is not a general security certification;
- UAO→USI rename remains deferred;
- immutable release/tag should follow independent audit + real-provider smoke, not precede them.

The auditor should independently determine whether that list is complete.
