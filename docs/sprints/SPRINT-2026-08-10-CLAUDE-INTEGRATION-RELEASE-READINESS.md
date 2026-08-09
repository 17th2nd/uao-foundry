# Sprint 2026-08-10 — Claude Integration and Release Readiness

**Status:** IN PROGRESS  
**Base authority:** `main` at `f1edd9c7aee51fc28f2c7908a75e81efb87b618c`  
**Sprint branch:** `sprint/2026-08-10-claude-integration-release-readiness`  
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

The Drive ZIP and manifest are recovery artifacts, not semantic authority.

## 2. Upstream authority boundary

The arbitrary-domain Relationship Type role source remains absent from current ASA authority. The dependency is tracked in:

- `17th2nd/uao-foundry#3`;
- `17th2nd/ASA#29`.

This sprint SHALL NOT invent Relationship Type authority. Non-empty relationship candidates continue to fail closed with `URO_TYPE_AUTHORITY_UNAVAILABLE` until ASA supplies a governed machine-readable source edition.

## 3. Sprint goals

### G1 — Real Claude Code provider adapter

Provide an executable, vendor-specific adapter behind the existing vendor-neutral Foundry process protocol.

The adapter shall:

- consume exactly one Foundry provider-protocol envelope on stdin;
- use Claude Code non-interactively;
- request schema-constrained structured output;
- allow research tools only, not arbitrary shell/file mutation;
- disable Claude session persistence for manufacturing calls;
- emit exactly one Foundry provider bundle on stdout;
- preserve the Foundry as the sole canonicalisation/publication boundary;
- capture adapter/model/version provenance as non-authoritative source-strategy notes;
- bind registry context when supplied;
- restore `registry://` evidence bytes from the verified immutable registry rather than trusting model-transcribed bytes.

### G2 — Stable semantic identity discipline

Document and enforce adapter-level guidance:

1. reuse an exact registered `resolutionKey` when reusing an existing Foundry identity;
2. otherwise prefer a durable external identifier when one exists;
3. otherwise manufacture a deterministic Foundry resolution key from semantic type + canonical label;
4. never use UUIDs, timestamps, model names, session IDs, conversational wording, confidence values or source ordering as semantic identity.

The Foundry, not the model, determines resulting canonical UAO IDs from the submitted candidate identity structure.

### G3 — Operator workflow

Add operator scripts for:

- environment/preflight checks;
- one-command registry-aware Claude manufacture;
- optional registry admission after successful package verification.

No API keys or OAuth tokens are stored in the repository.

### G4 — Independent regression gates

Add CI proving:

- adapter protocol parsing;
- structured-output extraction;
- exact registry-source byte restoration;
- failure on malformed Claude output;
- failure on command error;
- no accidental stdout contamination;
- full Java Foundry manufacture through a fake Claude executable;
- package verification;
- registry-aware semantic delta;
- existing Maven/provider/registry/reuse regressions remain green.

### G5 — Demonstration readiness

Add a non-authoritative TAFE demonstration guide showing how an operator can manufacture a course/topic identity and then rerun a related identity against the same registry to surface semantic reuse.

No TAFE-specific logic may enter production Java or generic adapter logic.

### G6 — Documentation and audit handoff

At sprint close, produce:

- exact changed-file inventory;
- authority/dependency record;
- test and CI evidence;
- known limitations;
- restore/backup locations;
- explicit claims that are and are not supported;
- an independent-audit prompt/checklist for Claude.

## 4. Non-goals

This sprint does not:

- create or ratify ASA Relationship Type definitions;
- close ASA#29 by local Foundry design;
- rename UAO to USI;
- claim external research content is true merely because a package verifies;
- create an ALA learner model;
- make a production-hosting/security certification claim;
- weaken existing fail-closed publication or package-verification gates.

## 5. Acceptance gates

The sprint may merge only when:

1. every existing Foundry workflow passes on the exact PR head;
2. the new Claude-adapter workflow passes on the exact PR head;
3. Maven `clean verify` passes;
4. the same compiled Java core remains domain-independent;
5. fake-Claude end-to-end manufacture reaches a structurally valid package;
6. registry reuse is demonstrated without model-controlled canonical reuse claims;
7. malformed/provider-failure/tamper controls fail closed;
8. documentation matches actual command surfaces;
9. the URO authority dependency remains explicit and unresolved unless ASA#29 is independently closed by upstream authority;
10. an audit packet exists before merge or is added immediately in the same tested sprint branch.

## 6. Post-merge controls

After merge:

- create a second post-sprint backup artifact and copy it to Google Drive;
- record the final main SHA in the sprint execution report;
- hand the audit packet to an independent Claude review without supplying a desired verdict.
