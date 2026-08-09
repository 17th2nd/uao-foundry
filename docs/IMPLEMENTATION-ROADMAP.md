# UAO Foundry Implementation Roadmap

This roadmap converts the frozen Manufacturing Architecture v0.1 into independently verifiable implementation increments.

## M0 — Foundation (this branch)

- Java 21 Maven project
- executable CLI
- canonical JSON Schema seed contracts
- lifecycle command routing
- arbitrary identity-seed intake
- fail-closed `NOT_PUBLISHED` response
- cross-domain tests and CI
- architecture and terminology decisions recorded

**Exit condition:** a fresh clone builds and the same executable accepts unrelated identity seeds with no domain-specific production logic.

## M1 — Contract enforcement

- validate request JSON against canonical schema
- stable request identifiers
- deterministic canonical serialization
- schema compatibility tests
- contract fixtures for valid and invalid requests

## M2 — Interpretation and scope

- interpretation candidate contract
- ambiguity preservation
- explicit scope selection
- jurisdiction/language/time controls
- no silent canonicalisation

## M3 — Manufacturing plan

- generic plan representation
- required knowledge/evidence dimensions derived from scope
- checkpointable plan state
- no domain-specific planner branches

## M4 — Reuse and registry discovery

- discover existing governed UAOs
- compare requested scope with reusable coverage
- construct semantic-delta plan
- preserve source authority and version references

## M5 — Evidence and provenance

- source adapters behind generic interfaces
- source records and evidence bindings
- acquisition policy
- provenance completeness checks

## M6 — Candidate knowledge and assembly

- candidate knowledge model
- n-ary relationship bindings
- canonical identity assembly
- unresolved-item preservation

## M7 — Verification

- structural verification
- evidence/provenance verification
- policy/profile verification
- deterministic checksums
- fail-closed publication gate

## M8 — Resume and inspect

- durable checkpoint format
- `status`, `resume`, and `inspect` implementation
- reproducibility across interrupted manufacturing runs

## M9 — Cross-domain manufacturing proof

Use at least three unrelated domains and prove that one executable manufactures each through the same pipeline. The proof must distinguish shared machinery from domain-specific source/configuration data and must not embed example knowledge in production code.

## M10 — ALA / external consumer integration

Expose governed package/registry interfaces so ALA and other ASA consumers can discover and reuse manufactured knowledge without reconstructing it.

## Future terminology review

UAO remains the prototype term throughout these milestones. During a later clean repository migration for university/research/partnership use, reassess whether the mature architecture genuinely supports `USI — Universal Semantic Identity` and associated Record/Foundry/Domain/Registry terminology.
