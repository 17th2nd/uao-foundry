# UAO Manufacturing Architecture v0.1

**Status:** Frozen prototype architecture baseline  
**Implementation status:** Foundation only  
**Canonical prototype term:** UAO — Universal ASA Object

## 1. Purpose

UAO Foundry exists to manufacture reusable, governed semantic knowledge assets from arbitrary identity seeds. It must not depend on the identity belonging to a particular discipline, ontology, qualification system, physical object class, or data source.

The architecture is designed so that the same executable can eventually manufacture structurally different UAOs while preserving a common contract for identity, relationships, evidence, provenance, coverage, uncertainty, verification, and publication state.

## 2. Manufacturing pipeline

```text
ManufacturingRequest
        |
        v
IdentitySeed
        |
        v
InterpretationCandidates
        |
        v
SelectedSemanticScope
        |
        v
ManufacturingPlan
        |
        +-------------------+
        |                   |
        v                   v
Reusable governed       New evidence
knowledge discovery     acquisition
        |                   |
        +---------+---------+
                  |
                  v
          CandidateKnowledge
                  |
                  v
            CanonicalUAO
                  |
                  v
             Verification
                  |
          pass ----+---- fail
           |              |
           v              v
    publish package    fail closed
```

## 3. Invariants

### INV-001 — Domain independence
Production code must not branch on example identities or contain identity-specific knowledge.

### INV-002 — Contract authority
JSON/JSON Schema is authoritative for portable UAO representation. Java is an implementation layer subordinate to the contracts.

### INV-003 — Explicit ambiguity
Ambiguity must be represented as data and resolved through an explicit interpretation/scope stage. The Foundry must not silently convert an ambiguous seed into a canonical identity.

### INV-004 — Provenance-bearing knowledge
Manufactured knowledge must remain traceable to evidence and provenance records appropriate to the manufacturing profile.

### INV-005 — Fail-closed publication
Acceptance, interpretation, assembly, and publication are distinct states. A UAO that has not satisfied verification policy must not be represented as verified or canonical-public.

### INV-006 — Reuse before reinvention
Before acquiring or constructing new knowledge, the Foundry should discover governed reusable knowledge. Manufacturing should preferentially produce the semantic delta required by the new identity/scope.

### INV-007 — Relationship expressiveness
Relationships must support role-bound n-ary participation. The canonical contract must not constrain the semantic model to property-bearing binary edges.

### INV-008 — Deterministic representation
Given equivalent canonical inputs and policy, serialization and package identity should be reproducible. Incidental runtime data must not silently alter semantic identity.

### INV-009 — Uncertainty remains visible
Coverage gaps, unresolved interpretations, verification findings, and unavailable evidence remain first-class outputs rather than being erased to create an appearance of completeness.

## 4. Canonical package surfaces

The initial contract surface is divided into:

- `manufacturing-request.schema.json` — arbitrary identity seed and generic controls;
- `canonical-uao.schema.json` — identity envelope, n-ary relationships, provenance, coverage and unresolved items;
- `manufactured-package.schema.json` — request + UAO + verification + checksums + publication state.

These schemas are intentionally minimal. They establish portable boundaries without prematurely freezing a domain ontology.

## 5. Publication states

Prototype packages begin as `EXPERIMENTAL`. The package contract distinguishes this from `VERIFIED`, `REJECTED`, and `SUPERSEDED` states. Verification policy is not yet implemented in v0.1; therefore the executable currently reports `NOT_PUBLISHED` for accepted manufacturing requests.

## 6. Reuse model

A later manufacturing stage must be capable of asking:

1. Which governed semantic identities already exist?
2. Which relationships/evidence are reusable under the requested scope and policy?
3. What knowledge is genuinely new?
4. Can the output refer to existing canonical identities instead of duplicating them?

This is the basis of cumulative knowledge manufacturing. A future request that overlaps prior work should manufacture only what is missing while preserving provenance and authority boundaries.

## 7. Cross-domain proof criterion

The same executable must accept and route unrelated identity seeds without identity-specific production code. Cross-domain examples may be used in tests to demonstrate this invariant, but passing the intake test is not evidence that a canonical UAO has been manufactured.

## 8. Current boundary

v0.1 proves:

- Java 21 build foundation;
- generic CLI surface;
- arbitrary identity-seed intake;
- deterministic foundation response;
- fail-closed publication response;
- canonical JSON Schema seed contracts;
- cross-domain CI guard.

v0.1 does **not** yet implement evidence acquisition, semantic interpretation, registry reuse, canonical assembly, verification policy, checkpoint/resume state, or publication.
