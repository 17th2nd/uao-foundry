# UAO Manufacturing Architecture v0.1

**Status:** implemented functional vertical slice  
**Canonical prototype term:** UAO — Universal ASA Object  
**Execution language:** Java 21  
**Portable contracts/output:** JSON

## 1. Purpose

UAO Foundry manufactures governed semantic packages beginning from an arbitrary identity expression. The expression is a seed. Interpretation, scope, evidence and canonical identity remain explicit records rather than hidden model state.

The universal core contains no identity-specific knowledge. A provider supplies identity-specific interpretation/planning/evidence/candidate material through a stable interface; deterministic pipeline stages govern what may become canonical output.

## 2. Layers

```text
Provider boundary
  fixture / future live AI-research adapters
                  │
                  ▼
Universal Foundry pipeline
  contracts → stages → validation → resolution → verification
                  │
                  ▼
Manufactured package
  identity-specific records, evidence, provenance, UAOs, findings
```

## 3. Pipeline

```text
RAW REQUEST
    ↓
JOB INITIALISATION
    ↓
IDENTITY SEED NORMALISATION
    ↓
INTERPRETATION CANDIDATES
    ↓
EXPLICIT SEMANTIC SCOPE
    ↓
DYNAMIC MANUFACTURING PLAN
    ↓
CONTEXTUAL SOURCE STRATEGY
    ↓
SOURCE SNAPSHOTS + HASHES
    ↓
NON-CANONICAL CANDIDATES
    ↓
VALIDATION / QUARANTINE
    ↓
IDENTITY RESOLUTION
    ↓
RELATIONSHIP CONSTRUCTION GATE
    ↓
CANONICAL UAO BUILD
    ↓
DYNAMIC COMPLETENESS
    ↓
VERIFICATION
    ↓
PUBLICATION DECISION
    ↓
VERSIONED PACKAGE + CHECKSUMS
```

## 4. Invariants

### INV-001 — Domain independence
Production Java may not branch on or contain knowledge about demonstration identities.

### INV-002 — Contract authority
JSON contracts govern portable Foundry records. Java executes them rather than redefining them.

### INV-003 — ASA subordination
For ASA primitives, current ASA authority outranks Foundry-owned schemas. `schemas/asa/*` are validation projections only.

### INV-004 — Explicit ambiguity
A seed is not a canonical identity. Competing interpretations and the selected scope remain inspectable.

### INV-005 — Evidence preservation
Source snapshots, hashes, retrieval metadata, candidate evidence and provenance links travel with the package.

### INV-006 — Quarantine before repair
Malformed candidate records are quarantined with validation findings. The Foundry does not silently mutate extracted evidence into conformance.

### INV-007 — Relationship expressiveness
URO semantics remain role-bound and potentially n-ary. UAOs hold relationship references only.

### INV-008 — Fail-closed authority
If required upstream semantic authority is missing, affected structures are not canonicalised.

### INV-009 — Determinism
Equivalent request/provider data plus the same repository/configuration provenance produces byte-identical fixture packages.

### INV-010 — Uncertainty visible
Unresolved scope, coverage, relationship authority and quarantine findings are records, not conditions the packager hides.

### INV-011 — Reuse-ready separation
Resolution keys and canonical IDs are stable inputs to later registry/semantic-delta discovery. Reuse logic must be added without making identity-specific code part of the core.

## 5. Canonical ASA boundary

The reviewed ASA CSS defines the UAO/URO validation boundary used here.

A canonical Foundry UAO uses the current fields:

```text
uid
lifecycle_status
successor_identity_ref (when required)
internal_state
assertions
relationship_references
provenance
disclaimer
```

Relationship meaning is not embedded into UAO records.

A URO uses:

```text
uid
identity.type_version
participants[]       # named roles, not fixed source/target
identity_literals{}
contextual_bindings[]
```

The Foundry does not currently publish arbitrary non-empty UROs until current authoritative Relationship Type role declarations are linked.

## 6. Epistemic boundary

The current CSS records `epistemic_class` while its vocabulary remains deferred. Foundry canonical assertion metadata therefore uses the explicit marker `DEFERRED_ON_RECORD`; it does not manufacture a doctrine that ASA has not supplied.

The following fields are recursively forbidden from canonical Foundry output in alignment with the current CSS:

```text
score
significance_value
belief
stance
```

## 7. Provider model

`FoundryProvider` is the universal adapter boundary. The built-in `FixtureProvider` supplies deterministic offline evidence for conformance and demonstration tests. Future live adapters may retrieve research or invoke AI systems, but must return the same governed intermediate record shapes.

The pipeline never permits a provider to write canonical UAOs directly.

## 8. Persistence and resume

Each stage writes a deterministic work artifact and the checkpoint records its SHA-256. `resume` reuses only stage artifacts whose checkpoint hash still matches. Source acquisition is replayed to restore and verify snapshot side effects before downstream reuse.

## 9. Publication

A package is always explicit about its status. Successful v0.1 fixture manufacture targets `EXPERIMENTAL` only.

`EXPERIMENTAL` is a structural/process claim, not a claim of exhaustive knowledge or independent truth.

## 10. Generality proof

The same compiled executable must manufacture at least three structurally different identities with:

- the same stage sequence;
- the same universal contracts;
- no production-code changes;
- different provider-generated plans;
- different canonical identity outputs;
- valid packages and checksums.

The test fixtures are not Foundry dependencies and may be replaced without changing production Java.
