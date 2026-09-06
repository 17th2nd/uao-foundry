# ADR-0007 — Identity enrichment as a state-succession operation

**Status:** Proposed 2026-09-06 (founder direction: "proceed with foundry update"); awaiting independent ratification
**Context:** UAO Library, Macleay Island pilot and cyberneticians depth programme

## Problem

A manufactured identity carries the assertion set of the one bounded provider call that made it — six to nine
sentences for each of the six cyberneticians. The registry's reuse law (ADR-0006, `docs/REGISTRY.md`) treats any
package that restates a registered identity with a different assertion set as `SEMANTIC_VARIANT_DIVERGENCE`, and
two such occurrences leave the identity `MULTIPLE_UNRECONCILED_VARIANTS`, refused for reuse, resolution and
significance inputs. Enrichment of a registered identity was therefore possible only through relationships to
other identities. The founder's requirement is that an identity "summarise an entire life": the person's own
node must be able to grow.

## Decision

Add a fifth identity operation, `ENRICH`, to the append-preserving journal.

- `ENRICH` names one uid as both subject and target (the identity persists) and carries an `enrichment` block:
  `fromVariant`, `toVariant` (semantic-variant digests of that uid) and `toPackageId` (the registered package
  whose occurrence carries `toVariant`).
- The registry, not the record, establishes the truth of the claim on every index build, from immutable package
  bytes: both variants must be occurrences of the subject, the named package must carry the newer one, and the
  newer assertion set must be a **strict superset** of the older one — every prior assertion restated verbatim
  (canonical JSON equality) plus at least one more. Anything else is refused at admission and fails
  verification thereafter.
- Variants superseded by an `ENRICH` are history, not unreconciled siblings. An identity is `SINGLE_VARIANT`
  when exactly one current variant remains; the index then exposes `currentVariant` and `variantHistory`.
  These fields appear only for enriched identities, so registries without `ENRICH` operations keep verifying
  byte-for-byte.
- Reuse follows the current state: `ReuseAnalyzer` compares a candidate against `currentVariant` when present.
  Restating the enriched form is a re-observation; restating the superseded form is now divergence.
- A fork (two enrichments leaving one variant) or a cycle fails the index build closed, as lifecycle
  contradictions already do.
- `FoundryRegistry.enrich(package, uid, …)` admits the package and records the operation as one fail-closed
  step: the superset law is checked against the candidate before anything is written, so a non-enriching
  package never enters the registry as a stray variant; a failure after admission rolls the admission back.
  CLI: `RegistryApplication enrich <package> --subject <uid> --reason … --justification … --recorded-at …`.

## What this does not do

- It does not change the uid, the resolution key, lifecycle state or any package. Every prior occurrence stays
  inspectable; nothing is deleted or rewritten.
- It does not let a provider re-word history. A live manufacture still produces its own assertion set; the
  operator-side enrichment bundle (Experiment 002's `reconcile_reuse.py` pattern) restates the registered
  assertions verbatim from `registry://` bytes and appends the new, sourced ones before `--fixture` manufacture.
- It does not decide truth. Contradictions between assertions remain the consumer's problem, as before.

## Consequences

Depth becomes a sequence of verifiable state versions instead of a graph-only property. Each enrichment is a
package (its own provenance, sources and verification) plus one journal record, and the current state of an
identity is derivable from bytes alone.
