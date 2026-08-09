# Sprint 2026-08-10 — Claude Integration and Release Readiness

**Status:** IMPLEMENTATION COMPLETE — AUDIT PACKET READY — FINAL HEAD CI/MERGE PENDING  
**Base authority:** `main` at `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`  
**Sprint branch:** `sprint/2026-08-10-claude-integration-release-readiness`  
**Pull request:** `#9`  
**Fully-green implementation head:** `3e09a26571211327c4f45bd26147f2f9dedea3a8`  
**Terminology:** UAO remains the prototype term; the existing UAO→USI clean-migration review decision is unchanged.

## 1. Preconditions

Before this sprint, the accepted Foundry state was backed up independently as:

- a GitHub preservation branch pinned to the exact base SHA;
- a source-tree `git archive`;
- a full Git bundle;
- an explicit file manifest;
- SHA-256 inventories verified in GitHub Actions;
- a ZIP copied into the project owner's Google Drive folder `UAO Foundry Backups`;
- a human-readable Drive backup manifest.

The backup was independently unpacked, hash-verified and restored from the Git bundle before sprint implementation began.

The Drive ZIP and manifest are recovery artifacts, not semantic authority.

## 2. Upstream authority boundary

The arbitrary-domain Relationship Type role source remains absent from current ASA authority. The dependency is tracked in:

- `17th2nd/uao-foundry#3`;
- `17th2nd/ASA#29`.

The authority sweep included current ASA `main`, CSS, compiler handoff, ADR/specification material, CC-0 bootstrap/type data, preserved/review branches, File Library and the parallel Drive corpus. The result was a confirmed upstream source/governance gap rather than a misplaced Foundry file.

This sprint SHALL NOT invent Relationship Type authority. Generic non-empty relationship candidates continue to fail closed with `URO_TYPE_AUTHORITY_UNAVAILABLE`; the Claude adapter rejects non-empty relationship candidates before returning its provider bundle.

## 3. Sprint goals and disposition

### G1 — Real Claude Code provider adapter — IMPLEMENTED

Provided a vendor-specific adapter behind the existing vendor-neutral Foundry process protocol.

The adapter:

- consumes exactly one Foundry provider-protocol envelope on stdin;
- invokes Claude Code non-interactively;
- requests schema-constrained structured output;
- restricts model-facing built-in tools to `WebSearch,WebFetch` and applies additional permission denies;
- disables Claude session persistence for manufacturing calls;
- emits exactly one Foundry provider bundle on stdout;
- preserves the Foundry as the sole canonicalisation/publication boundary;
- captures adapter/model/version provenance as non-authoritative source-strategy notes;
- binds registry context when supplied;
- restores `registry://` evidence bytes from the verified immutable registry rather than trusting model-transcribed bytes.

**Boundary:** CI proves the process integration with a fake Claude executable. A real authenticated workstation/network Claude run remains required external evidence after merge/audit.

### G2 — Stable semantic identity discipline — IMPLEMENTED

Documented and enforced adapter-level guidance:

1. reuse an exact registered `resolutionKey` when reusing an existing Foundry identity;
2. otherwise prefer a durable external identifier when one exists;
3. otherwise manufacture a deterministic Foundry resolution key from semantic type + canonical label;
4. reject obvious UUID/timestamp/model/session/conversation/turn material as new semantic identity.

The Foundry, not the model, determines resulting canonical UAO IDs from candidate `resolutionKey` values.

A separate document, `docs/STABLE-SEMANTIC-IDENTITY.md`, records scope/collision/ambiguity limitations and explicitly does not pre-adopt USI terminology.

### G3 — Operator workflow — IMPLEMENTED

Added:

```text
scripts/preflight-live.sh
scripts/manufacture-claude.sh
```

Preflight validates Java 21, Maven, Python, Claude Code presence, adapter syntax, required schemas, release JAR creation and any existing registry.

The manufacture wrapper creates a temporary exact executable launcher for the checked-in Python adapter rather than weakening the Java provider executable-path invariant.

No API keys, OAuth tokens or credential files were added to the repository.

### G4 — Independent regression gates — IMPLEMENTED / GREEN AT IMPLEMENTATION HEAD

Added CI proving:

- adapter protocol parsing;
- structured-output extraction;
- exact registry-source byte restoration;
- failure on malformed Claude output;
- failure on command error;
- failure on ephemeral semantic keys;
- failure on non-empty relationship candidates;
- no accidental stdout contamination;
- full Java Foundry manufacture through a fake Claude executable;
- package verification;
- registry-aware semantic delta;
- existing Maven/provider/registry/reuse regressions remain green.

Release-readiness review additionally strengthened `PackageVerifier` so an internally re-hashed semantic forgery is rejected even after a complete checksum inventory rewrite.

### G5 — Demonstration readiness — IMPLEMENTED

Added a non-authoritative TAFE demonstration guide at:

```text
examples/tafe/README.md
```

It shows how an operator can manufacture a first course/topic identity and then rerun a related identity against the same registry to surface actual semantic reuse.

The guide requires showing the real generated `reuse-report.json`; it does not promise a predetermined reuse count.

CI verifies the demonstration strings do not enter production Java or generic adapter logic.

### G6 — Documentation and audit handoff — IMPLEMENTED

Produced:

```text
docs/audit/SPRINT-2026-08-10-EXECUTION-REPORT.md
docs/audit/CLAUDE-INDEPENDENT-AUDIT-HANDOFF.md
```

The execution report records backup, authority investigation, exact implementation scope, test evidence, supported/unsupported claims and residual required evidence.

The audit handoff requests a neutral read-only independent audit with no desired verdict and requires repository/authority-first reading before implementation assessment.

## 4. Additional release-readiness work discovered during the sprint

### 4.1 Stale specialized CI artifact paths

Two pre-existing workflows still invoked:

```text
target/uao-foundry-0.1.0-SNAPSHOT.jar
```

although Maven produces:

```text
target/uao-foundry-0.1.0.jar
```

The provider and semantic-delta workflow failures occurred only at the stale filename after successful Java builds/tests. Both workflow paths were corrected; both suites subsequently passed.

### 4.2 Semantic package-verification hardening

The verifier was strengthened to cross-check duplicated semantic package views and recompute every canonical Foundry UAO UID from its stored `resolution_key` using the same deterministic identifier rule used by manufacture.

Negative tests mutate semantic package data, recompute all package checksums, and still require verification failure. The tests explicitly ensure the failure is not merely a stale-checksum error.

## 5. Non-goals preserved

This sprint does not:

- create or ratify ASA Relationship Type definitions;
- close ASA#29 by local Foundry design;
- rename UAO to USI;
- claim external research content is true merely because a package verifies;
- create an ALA learner model;
- make a production-hosting/security certification claim;
- weaken existing fail-closed publication or package-verification gates;
- claim a real authenticated Claude service run occurred in GitHub CI;
- tag/release `v0.1.0` before independent audit and real-provider smoke evidence.

## 6. Acceptance-gate status

At implementation head:

```text
3e09a26571211327c4f45bd26147f2f9dedea3a8
```

all four independent suites completed successfully:

| Workflow | Run ID | Status |
|---|---:|---|
| UAO Foundry CI | `31337650100` | SUCCESS |
| UAO Foundry provider protocol | `31337650099` | SUCCESS |
| UAO Foundry semantic delta | `31337650105` | SUCCESS |
| UAO Foundry Claude Code adapter | `31337650103` | SUCCESS |

The exact final PR head will differ because this sprint record and audit packet are documentation commits. **Merge remains prohibited until the same four workflows pass on that exact final PR head.**

Acceptance requirements remain:

1. every existing Foundry workflow passes on the exact final PR head;
2. the new Claude-adapter workflow passes on the exact final PR head;
3. Maven `clean verify` passes;
4. the same compiled Java core remains domain-independent;
5. fake-Claude end-to-end manufacture reaches a structurally valid package;
6. registry reuse is demonstrated without model-controlled canonical reuse claims;
7. malformed/provider-failure/tamper controls fail closed;
8. documentation matches actual command surfaces;
9. the URO authority dependency remains explicit and unresolved unless ASA#29 is independently closed by upstream authority;
10. the audit packet exists before merge.

## 7. Post-merge controls

After a tested exact-head merge:

- create a second post-sprint backup artifact from the final `main` SHA;
- independently verify its checksums/source archive/Git bundle;
- copy the backup ZIP into the existing Google Drive `UAO Foundry Backups` folder;
- create a post-sprint Drive recovery manifest containing the final main SHA and restore evidence;
- retain the neutral Claude audit packet for the project owner's independent audit;
- run one real authenticated Claude Code workstation smoke before deciding whether to freeze/tag `v0.1.0`;
- run the TAFE demonstration against a persistent demo registry and report actual reuse evidence;
- keep arbitrary URO publication fail-closed until ASA#29 is resolved through upstream authority.
