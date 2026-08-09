# UAO Foundry Execution Blueprint Traceability

**Recovered programme source:** `Pasted markdown.md`, 2026-08-03 — “UAO FOUNDRY EXECUTION BLUEPRINT — Generic Identity-to-UAO Manufacturing System”.  
**Implementation target:** Foundry `v0.1.0`.

This document records implementation traceability; it does not replace the recovered source blueprint.

| Blueprint stage | v0.1 implementation | Principal artifact |
|---|---|---|
| 1 Job Initialisation | deterministic job identity, provider/config hash, repository provenance | `manufacturing-job.json` |
| 2 Seed Normalisation | original retained; whitespace/case normalised without semantic resolution | `identity-seed.json` |
| 3 Identity Interpretation | provider-generated candidates; no merge | `interpretation-candidates.json` |
| 4 Scope Resolution | explicit selected interpretation and exclusions | `scope-resolution.json` |
| 5 Manufacturing Planning | provider-generated dynamic dimensions/questions | `manufacturing-plan.json` |
| 6 Source Strategy | contextual source classes and authority notes | `source-strategy.json` |
| 7 Source Acquisition | exact offline snapshots, IDs, licences, timestamps, SHA-256 | `source-registry.json`, `source-corpus/` |
| 8 Knowledge Extraction | provider candidate sets remain non-canonical | `candidate-*.json` |
| 9 Candidate Validation | schema validation; invalid records quarantined, never silently repaired | `candidate-quarantine.json` |
| 10 Identity Resolution | deterministic resolution-key grouping and stable UAO IDs | `identity-resolution.json` |
| 11 Relationship Construction | ASA n-ary boundary; fail-closed if current type-role authority absent | `canonical-relationships.json`, `unresolved-items.json` |
| 12 Canonical Build | deterministic ASA-shaped UAOs, deferred epistemic vocabulary, evidence ledger | `canonical-identities.json`, `provenance-ledger.json` |
| 13 Completeness Analysis | generated questions evaluated from provider answers | `coverage-report.json` |
| 14 Verification | schemas, IDs, provenance, hashes, scope, forbidden fields, URO authority boundary | `verification-report.json` |
| 15 Publication Decision | fail-closed status selection | `publication-decision.json` |
| 16 Package Manufacture | resolved-label package, manifest, complete inventory, checksums, post-package verify | `manifest.json`, `checksums.sha256` |

## Completion gates

### Universal execution

All sixteen stages are executable in deterministic fixture mode.

### Identity independence

Production Java contains no demonstration identity knowledge. CI scans the production tree for the cross-domain fixture terms as an additional negative control.

### Technical

- Java 21;
- JSON contracts govern portable records;
- provider interface decouples fixture/live adapters from orchestration;
- fixture mode requires no external service;
- repeated fixture runs are byte-identical for the same inputs, provider data and repository provenance;
- checkpoint/resume reuses validated completed stages;
- package and source hashes detect tampering;
- invalid requests/candidates fail closed or quarantine explicitly.

### Cross-domain evidence

CI executes biological, physical/material and manufactured/cultural fixtures with the same compiled JAR and requires all three to reach `EXPERIMENTAL` without Java changes.
