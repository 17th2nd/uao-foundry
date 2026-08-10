# UAO Foundry — Independent Audit Remediation R1 Execution Report

**Base:** `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb`
**Branch:** `sprint/2026-08-10-audit-remediation-r1`
**Status:** candidate implementation; merge prohibited until final-head CI and independent Claude differential re-audit.

## Remediated findings

- **F-1 semantic forgery:** meaning-bearing core projections produce `manifest.contentDigest`; `packageId` is derived from that digest. The verifier reconstructs assertions/provenance/publication state rather than trusting duplicated views. A consistent canonical/manufactured assertion rewrite with refreshed checksums is rejected. Package verification is explicitly scoped to structural/content-addressed integrity, not external factual truth/authorship.
- **F-2 registry custody:** `registry://` resolution moved into core source acquisition. Plain manufacture without a verified registry context fails closed; registry-aware manufacture replaces provider content with bytes from the verified immutable package. Adapter and `ReuseAnalyzer` checks remain defence in depth.
- **F-3 resolution keys:** Java Foundry requires canonical NFKC/no-whitespace key syntax. Registry admission refuses same-key occurrences with no lexical label/alias continuity. Failed admission is transactional and leaves the registry unchanged.
- **F-4 checkpoint trust:** resume deterministically re-derives cached stages from the captured provider snapshot before counting them as reused. A stage plus attacker-updated checkpoint hash is invalidated and rebuilt.
- **F-5 index trust:** registry `index/search/discovery` rebuild and compare the index to verified package contents before use.
- **F-6 package path collision (audit numbering F-7):** package output directories include the content-addressed package ID and differing jobs cannot silently overwrite the same label/version/status path.
- **Silent invalidation (audit F-8):** `PipelineResult.invalidatedStages` surfaces rejected cache projections.
- **Relationship regression:** checked-in relationship-bearing fixture proves all relationship candidates remain `URO_TYPE_AUTHORITY_UNAVAILABLE`, canonical URO list remains empty, and publication is `EVIDENCE_INCOMPLETE` while ASA#29 is unresolved.

## Claude containment gate

Implemented against the independent `CLAUDE-CONTAINMENT-REMEDIATION-GATE-2026-08-10.md`:

- minimum Claude Code v2.1.205;
- exact WebSearch/WebFetch built-in restriction;
- explicit Bash/Read/Write/Edit/Glob/Grep/Agent/Skill and `mcp__*` denial;
- `--strict-mcp-config`;
- `dontAsk`, no session persistence, no Chrome;
- schema/output/turn/budget assertions;
- bounded environment allowlist with unrelated secret-canary tests;
- custom endpoint provenance without token disclosure;
- top-level structured-output salvage removed; JSON `result` fallback is explicitly marked;
- hostile argv mutation tests make the fake Claude fail before bundle emission.

## Local adversarial replay performed before publication

Using a JDK-only build from the independently verified post-sprint Git bundle:

- clean granite manufacture + strengthened standalone verify: PASS;
- fully re-checksummed canonical/manufactured assertion forgery: REJECTED;
- fabricated plain-path `registry://` evidence: REJECTED;
- registry-aware fabricated transcription replaced byte-for-byte from immutable registry: PASS;
- uppercase, trailing-space and NBSP resolution-key variants: REJECTED;
- granite key reused for asbestos identity: REJECTED; registry hash unchanged after failed admission;
- stored registry index label injection followed by search: REJECTED;
- stage-12 content + checkpoint-hash forgery: RE-DERIVED, surfaced in `invalidatedStages`, forged text absent from final package;
- relationship-bearing fixture: `EVIDENCE_INCOMPLETE`, zero canonical UROs, all candidate relationships unresolved;
- relationship-incomplete package relabelled `EXPERIMENTAL`, content digest/package ID refreshed and checksums rewritten: REJECTED by independent publication reconstruction;
- same visible label with distinct semantic package content: distinct package-ID suffixed directories coexist.
- Claude adapter containment tests: all 12 test methods passed across two bounded invocations, including hostile argv mutation, version, environment, budget, fallback and registry-byte custody tests.
- Java production sources compile cleanly with JDK 21-compatible `javac`; all JUnit test sources type-check against minimal API stubs. Maven is not installed in the local execution runtime, so actual JUnit execution remains an exact-head GitHub Actions gate.
- fresh cow, granite and pie manufactures: `EXPERIMENTAL` + verification pass; relationship-bearing fixture: exit 4 / `EVIDENCE_INCOMPLETE` / zero canonical UROs.

## Standing limits

- This remediation does not establish correctness of external knowledge.
- A self-contained package has no third-party authenticity guarantee absent an external signature/trust anchor.
- Conservative resolution-key continuity does not solve universal semantic equivalence.
- ASA#29 remains the blocker for arbitrary-domain URO publication and realistic qualification structure.
- Real authenticated Claude workstation operation remains field-unverified until after independent candidate re-audit.
