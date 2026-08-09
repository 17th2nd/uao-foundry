# ADR-0002 — Align Foundry Canonical Output to Current ASA Authority

**Status:** Accepted for Foundry v0.1 implementation  
**Date:** 2026-08-09  
**Authority created here:** No

## Context

The initial Foundry M0 repository established generic JSON envelopes before the implementation pass had reconciled them against the current ASA Canonical Specification Source (CSS).

The reviewed ASA `main` authority surface was commit:

`908c5255fb3144c2a2e3f48c993d031e347d1695`

Relevant upstream sources:

- `specification/core/canonical_source.json` — CSS schema version `2026.1`;
- `00_Governance/Specifications/ASA-SPEC-0004_Compiler_Specification_v0.1.md`;
- `00_Governance/ADR/ADR-002_Relationship_Ontology_and_Ownership.md`.

The CSS is the semantic authority. Foundry-owned schemas must not widen, narrow or reinterpret it while describing canonical ASA primitives.

## Decision

1. Replace the M0 invented canonical UAO envelope with a validation projection of the current CSS UAO shape.
2. Preserve the CSS `uao-<12 hex>` and `uro-<12 hex>` identifier shapes.
3. Keep relationships outside UAOs; UAOs contain relationship references only.
4. Preserve URO n-ary named-role participation; do not reduce relationships to source/predicate/target binary edges.
5. Reject ASA-forbidden canonical fields recursively: `score`, `significance_value`, `belief`, `stance`.
6. Preserve `epistemic_class` as deferred. Foundry canonical assertion metadata emits `DEFERRED_ON_RECORD` rather than inventing a normative epistemic vocabulary.
7. Treat checked-in `schemas/asa/*` as **non-authoritative validation projections** pinned to the reviewed upstream commit.
8. Fail closed if the authority needed to validate a generated semantic structure is absent.

## Blueprint reconciliation

The recovered 2026-08-03 UAO Foundry Execution Blueprint predates the current implementation reconciliation. Its generic pipeline and identity-independence requirements remain controlling programme requirements.

Where its illustrative relationship section described `source / predicate / target`, ADR-002 governs instead: UROs bind one or more participants under type-declared named roles, and direction is only a possible two-role special case.

## Consequences

The Foundry may manufacture ASA-shaped UAOs without creating its own ontology. Arbitrary domain UROs require the upstream role/type declaration authority described in `UPSTREAM-DEPENDENCIES.md`; until supplied, those candidates are preserved as unresolved rather than published.
