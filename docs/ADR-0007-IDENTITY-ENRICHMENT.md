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

## Operator path

1. A live manufacture about the identity produces a package whose provider snapshot carries new, sourced claims.
   Registered against the registry it is refused (`SEMANTIC_VARIANT_DIVERGENCE`); that refusal is expected.
2. `scripts/enrich/build_enrichment_bundle.py --uid <uid> --package <that package> --run` builds a fixture bundle
   that restates the identity's current assertions verbatim from `registry://` bytes and appends the new ones
   (other registered identities restated verbatim too; new identities and relationship candidates kept), then
   manufactures it with `--fixture … --enrich <uid>`. The reuse analyzer accepts a differing variant for a
   named uid only as a strict superset (`ENRICHMENT_NOT_SUPERSET` / `ENRICHMENT_TARGET_ABSENT` otherwise) and
   reports it under `enrichedUaos`, never `reusedUaos`. `--enrich` and `--register` are mutually exclusive.
3. `RegistryApplication enrich <package> --subject <uid> …` re-derives the law from bytes, admits the package
   and records `ENRICH` as one fail-closed step.

Dry run 2026-09-06 on a scratch copy of the cyberneticians registry: Wiener 7 → 8 assertions, `SINGLE_VARIANT`,
verification PASS, one journal record, zero provider calls.

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

## Ratification record

- **Codex pass A (2026-09-06, read-only, on 0d14cd4): REFUSED** — F-2 HIGH `enrich()` built the operation record after
  `register()`, outside the rollback boundary, so invalid metadata could leave an admitted package without its ENRICH;
  F-7 HIGH `significanceInputs()` took the first occurrence regardless of `currentVariant`, so A_x could export
  superseded assertions; F-6 MEDIUM the fork test never reached the journal fork guard. F-1/F-3/F-4/F-5 INFO: byte
  verifiability, individual refusals, legacy index byte-compatibility and legacy operation ids all pass.
  Report: `temp/codex-uaofoundry-adr0007-ratification-001.md`.
- **Remediation (second commit):** operation metadata validated and the record built before any write; significance
  export selects the current variant's occurrence; tests replaced by a genuine journal-fork test (refused fork
  removed, accepted succession intact, unlinked sibling reported unreconciled) and a refusal test covering both
  before-admission (blank justification) and after-admission (seeded journal collision → package rolled back).
  Suite 180/180. Codex pass B requested on the combined change.
