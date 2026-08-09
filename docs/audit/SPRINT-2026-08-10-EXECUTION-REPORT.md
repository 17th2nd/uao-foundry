# Sprint 2026-08-10 — Execution Report

**Repository:** `17th2nd/uao-foundry`  
**Pull request:** `#9`  
**Frozen pre-sprint base:** `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`  
**Implementation head audited by this report:** `3e09a26571211327c4f45bd26147f2f9dedea3a8`  
**Status at report creation:** IMPLEMENTATION COMPLETE / ALL FOUR CI SUITES GREEN / FINAL DOCUMENTATION PASS PENDING

This report records what was done. It is evidence for independent review and does not instruct the reviewer what verdict to reach.

## 1. Pre-sprint backup gate

No sprint implementation began until the accepted base state was preserved independently.

### 1.1 GitHub preservation

Preservation branch:

```text
backup/main-2026-08-10-f1edd9c7
```

The backup workflow was introduced only on the preservation branch and targeted the exact accepted base SHA, not the temporary workflow commit.

Backup control PR `#8` existed only to make the Actions artifact retrievable through the connected tooling. It was closed without merge after recovery verification.

GitHub Actions manufactured:

- source-tree `git archive` pinned to the base commit;
- full Git bundle;
- tracked-file manifest;
- `SHA256SUMS.txt`.

The runner verified the internal checksums before publishing the artifact.

Artifact name:

```text
uao-foundry-backup-f1edd9c7-2026-08-10
```

GitHub artifact digest:

```text
sha256:12421bd0df0026611a1af6129d2a80255b0c33b4ba91d58cdade83b9fba477cd
```

Internal payload hashes:

```text
038ca92e558179b08e78b2e65f4decece80005104b20c208cbe766eb289a9f2e  uao-foundry-source-f1edd9c7aee51fc28f2c7908a75e81efb87b618c.tar.gz
f73b54ec8b1a4f6d011499b324c5dd89c0004a31cf22d3176b99fa8f6d02cf4d  uao-foundry-full-history.bundle
c53ce21ed5e2eae6a4c29b0a4579d83df388658f532029acb8358e7cec8118ed  BACKUP-MANIFEST.txt
```

### 1.2 Independent restore check

Outside the Actions runner, the downloaded artifact was unpacked and checked again:

- internal SHA-256 inventory passed;
- source archive was readable;
- Git bundle cloned successfully;
- cloned bundle contained base commit `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`;
- target base contained 85 tracked files.

### 1.3 Google Drive recovery copy

Drive folder:

```text
https://drive.google.com/drive/folders/1SocYM9vLcNgWbMJZ4hKI0vvj6lDZZ6Fr
```

Exact backup ZIP:

```text
https://drive.google.com/file/d/1Z6Llgl3jyrv58ddArr46MNPjoR5WYlRB/view
```

Human-readable Drive recovery manifest:

```text
https://docs.google.com/document/d/1bYAdpgk7V7eX0kzo2DuiZeqT4Njj6v1TeFFqgox_aZ8/edit
```

Drive metadata was checked after upload and confirmed that the ZIP is owned by the project owner's account and is inside the intended backup folder.

## 2. Relationship-authority investigation

Before implementing live relationship generation, the outstanding Foundry issue was re-investigated across:

- current ASA `main` and CSS;
- compiler fan-out handoff;
- ADR-002 and current implementation specifications;
- CC-0 bootstrap/type definitions;
- preserved/review branches;
- File Library records;
- connected Google Drive authority/review corpus.

The investigation concluded that the arbitrary-domain Relationship Type role registry is a genuine current source/authority gap, not merely a misplaced Foundry file.

Two tracking surfaces now exist:

```text
17th2nd/uao-foundry#3
17th2nd/ASA#29
```

ASA#29 specifies the machine-readable authority needed to close the dependency: type identifiers/versions, roles, cardinalities, participant kinds, identity effects, literals, symmetry, lifecycle/compatibility and deterministic validation semantics.

The sprint therefore did **not** invent relationship authority. Non-empty arbitrary-domain relationship candidates remain fail-closed.

## 3. Sprint branch and scope

Sprint branch:

```text
sprint/2026-08-10-claude-integration-release-readiness
```

The branch was created directly from the frozen base commit.

The implementation diff at head `3e09a265...` is 25 small connector commits over 16 changed files. The high commit count reflects repository-API atomic file writes, not 25 independent architectural changes.

### 3.1 Changed implementation/CI files

```text
.github/workflows/claude-adapter.yml
.github/workflows/provider-protocol.yml
.github/workflows/semantic-delta.yml
adapters/claude-code/claude_provider.py
adapters/claude-code/tests/test_claude_provider.py
scripts/manufacture-claude.sh
scripts/preflight-live.sh
src/main/java/org/seventeenthsecond/uaofoundry/verifier/PackageVerifier.java
src/test/java/org/seventeenthsecond/uaofoundry/verifier/PackageVerifierHardeningTest.java
```

### 3.2 Changed documentation/example files

```text
README.md
adapters/claude-code/README.md
docs/IMPLEMENTATION-ROADMAP.md
docs/STABLE-SEMANTIC-IDENTITY.md
docs/UPSTREAM-DEPENDENCIES.md
docs/sprints/SPRINT-2026-08-10-CLAUDE-INTEGRATION-RELEASE-READINESS.md
examples/tafe/README.md
```

The audit documents themselves are added after the implementation head and are intentionally not included in the implementation diff above.

## 4. Claude Code adapter

A vendor-specific adapter was added behind the already accepted vendor-neutral process protocol:

```text
adapters/claude-code/claude_provider.py
```

### 4.1 Authority boundary

The adapter cannot write canonical UAOs/UROs or publication decisions through the provider protocol.

The data path remains:

```text
request + registry context
        ↓
Claude Code research/evidence proposal
        ↓
provider bundle
        ↓
Java schema/candidate validation
        ↓
identity resolution
        ↓
canonical UAO construction
        ↓
verification/publication
        ↓
optional verified registry admission
```

### 4.2 Claude process boundary

The adapter invokes Claude Code non-interactively and uses:

- structured JSON output + JSON Schema;
- `--bare`;
- no session persistence;
- bounded turns and adapter-side timeout;
- hard tool availability restricted to `WebSearch,WebFetch`;
- `permission-mode dontAsk`;
- defense-in-depth denies for shell/filesystem/subagent/skill tools.

No Claude credentials are stored in the repository.

### 4.3 Registry evidence custody

Registry matches are resolved by adapter code from `UAO_FOUNDRY_REGISTRY_ROOT`, not trusted from model-transcribed content alone.

For a `registry://` citation:

1. the adapter resolves the bounded occurrence under the verified registry root;
2. reads exact bytes;
3. supplies bounded evidence to Claude;
4. after Claude responds, discards model-transcribed content for that locator;
5. restores the exact registry bytes into the provider bundle;
6. the Java semantic-delta layer independently resolves/hashes the same registry locator again.

This prevents a model from changing prior evidence while retaining a trusted-looking `registry://` prefix.

## 5. Stable semantic identity discipline

Live candidate identity keys follow:

1. exact existing registry `resolutionKey` when genuine reuse is selected;
2. `ext:<scheme>:<identifier>` for an appropriate durable external identifier;
3. `foundry:v0.1:<semantic-type>:<canonical-label>` otherwise.

The Claude adapter rejects obvious model/session/timestamp/UUID-derived new keys.

Candidate/source handles remain local bundle references and do not become semantic identity.

The adapter prompt also asks for separable evidence-backed reusable component identities where materially warranted, while explicitly warning against speculative enumeration merely to manufacture apparent reuse.

## 6. Operator workflow

Added:

```text
scripts/preflight-live.sh
scripts/manufacture-claude.sh
```

`preflight-live.sh` checks Java 21, Maven, Python, Claude Code, adapter syntax, JAR packaging and existing registry verification.

`manufacture-claude.sh` builds/uses the release JAR and manufactures a temporary `0700` provider launcher whose sole action is to execute the checked-in Python adapter. This preserves the Java command-provider requirement for an exact executable even if repository APIs/clones do not preserve the adapter source execute bit.

## 7. TAFE demonstration surface

Added:

```text
examples/tafe/README.md
```

It is documentation only. CI explicitly rejects the example qualification/topic strings if they appear in production Java or the generic Claude adapter.

The demonstration tells the operator to show the actual generated `reuse-report.json`; it does not promise that a particular semantic overlap will exist before manufacture/evidence proves it.

## 8. Package-verifier hardening

During release-readiness review, an existing trust gap was identified: a package could previously have semantic files edited and all checksums recomputed, while the verifier mostly proved internal hash/schema validity.

`PackageVerifier` now additionally verifies:

- `manufactured-package.uaos` exactly matches `canonical-identities.json`;
- `manufactured-package.uros` matches `canonical-relationships.json`;
- embedded request matches `manufacturing-request.json`;
- embedded publication decision matches `publication-decision.json`;
- embedded verification record matches `verification-report.json`;
- manifest root/status agrees with the corresponding package records;
- manifest root UAO exists in canonical identities;
- canonical UAO UIDs are unique;
- every Foundry UAO UID equals `uao-` + the first 12 hex characters of SHA-256 over its stored `resolution_key`, using the same deterministic identifier routine as manufacture.

Negative tests deliberately alter semantic files, recompute the complete checksum inventory, and still require verification failure. This distinguishes semantic consistency checks from ordinary stale-checksum detection.

## 9. CI defect discovered and repaired

Opening PR #9 exposed two pre-existing workflow defects:

```text
.github/workflows/provider-protocol.yml
.github/workflows/semantic-delta.yml
```

Both still invoked:

```text
target/uao-foundry-0.1.0-SNAPSHOT.jar
```

while Maven produces:

```text
target/uao-foundry-0.1.0.jar
```

The Java tests/package step succeeded; the specialized workflow failed only when attempting the stale filename. Both workflows were corrected to the release artifact name and subsequently passed.

No semantic/core rule was changed to make those gates pass.

## 10. Independent CI evidence at implementation head

Exact implementation head:

```text
3e09a26571211327c4f45bd26147f2f9dedea3a8
```

All four suites completed successfully:

| Workflow | Run ID | Result |
|---|---:|---|
| UAO Foundry CI | `31337650100` | SUCCESS |
| UAO Foundry provider protocol | `31337650099` | SUCCESS |
| UAO Foundry semantic delta | `31337650105` | SUCCESS |
| UAO Foundry Claude Code adapter | `31337650103` | SUCCESS |

The core suite includes Java unit/integration tests, package creation, cross-domain manufacture, byte determinism/resume and tamper rejection.

The provider suite includes live command-provider manufacture, deletion of the original provider before resume, snapshot-only resume and provider-snapshot tamper rejection.

The semantic-delta suite seeds/verifies a registry, performs registry-backed reuse manufacture and verifies the resulting package/registry.

The Claude-adapter suite includes:

- Python adapter unit tests;
- all Java tests;
- executable packaging;
- fake-Claude process invocation through the same adapter path;
- first registry-aware manufacture + registration;
- second manufacture proving registered UAO reuse and registry source reuse;
- package and registry verification;
- demo-term isolation from generic production code;
- Python bytecode cleanliness.

## 11. Claims supported by this sprint

The evidence supports these claims:

- the Java Foundry accepts a vendor-neutral external provider without transferring canonical authority;
- the Claude adapter can be tested end-to-end through the same process boundary using a fake Claude executable;
- provider snapshots remain package evidence and resume input;
- registry context can lead to exact stable-identity reuse;
- `registry://` evidence is restored/verified against immutable registry bytes;
- semantic delta is computed by the Foundry, not accepted as model self-report;
- internally re-hashed semantic package forgery is rejected by the hardened verifier;
- TAFE demonstration terms are not hard-coded into production implementation.

## 12. Claims NOT supported yet

This sprint does **not** prove:

- that a real authenticated Claude Code installation accepts every current CLI option on the project owner's workstation;
- that live Claude research is factually correct merely because the provider bundle/package is structurally valid;
- that the TAFE example will produce a specific reuse count before it is actually run with evidence;
- that arbitrary-domain URO relationships may be canonically published;
- that the adapter/Foundry is a production security-certified sandbox;
- that `v0.1.0` should be tagged/released before independent audit and a real-provider smoke run;
- that UAO should already be renamed USI.

## 13. Required next evidence after merge

1. Restore/verify the final post-sprint backup.
2. Run `bash scripts/preflight-live.sh` on the intended workstation.
3. Perform one real authenticated Claude manufacture.
4. Verify the resulting package and preserve its `provider-snapshot.json`.
5. Run the TAFE demonstration against a persistent demo registry and inspect the actual reuse report.
6. Obtain an independent Claude audit of the sprint PR/final main state using the separate audit handoff.
7. Address any audit findings before an immutable v0.1.0 release/tag decision.
8. Keep arbitrary URO publication fail-closed until ASA#29 is independently resolved upstream.

## 14. Final PR-head rule

This report is itself a documentation commit after implementation head `3e09a265...`. The exact final PR head therefore cannot be embedded in this file without creating another head recursively.

The independent auditor must use the current head of PR #9 (and eventual merge commit) as the exact audit target. The final PR head must receive the same four green workflows before merge.
