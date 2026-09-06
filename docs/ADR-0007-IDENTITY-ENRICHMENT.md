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
- **Codex pass B (2026-09-06, read-only, on e24cb1f): REFUSED** — F-B5 HIGH the bundle builder's novelty test was exact
  string inequality, so a paraphrase of a prior assertion passed as enrichment; F-B6 HIGH a live source sharing an id
  with a registered source silently substituted live bytes behind historical claims; F-B2 MEDIUM
  `scripts/exp002/reconcile_reuse.py` still took `occurrences[0]`, which is package-id order, not succession; F-B3
  MEDIUM the "post-admission" rollback test never crossed admission (the seeded journal collision was rejected by the
  verified `index()` read before `register()`); F-B7 MEDIUM the operator summary had no enrichment field. F-B1/F-B4
  INFO: F-2 closed by implementation, operator gate aligned. Report:
  `temp/codex-uaofoundry-adr0007-ratification-pass-b-001.md`.
- **Remediation (third commit):** shared `scripts/enrich/bundle_lib.py` — `current_occurrence` selects by
  `currentVariant` and fails closed on unreconciled variants (both bundle builders use it; `occurrences` order is
  never succession); `split_paraphrases` classifies provider claims by content-token overlap with the restated
  assertions (default threshold 0.5, `--paraphrase-threshold`), the bundle builder refuses paraphrases and only an
  explicit `--accept-paraphrase <candidateId>` keeps one, recorded in `authorityNotes`; `SourcePool` merges source ids
  by origin — registry sources first under their package, a colliding live source is renamed `<id>--live-<pkg>` with
  its claim/evidence references rewritten, registry packages sharing an id are shared only when their sha256 agrees.
  The rollback test now makes the journal directory unwritable so `register()` succeeds and only the ENRICH record
  write fails (package removed, index byte-identical, no journal entry, and the same enrichment succeeds afterwards).
  The console report carries `existingIdentitiesEnriched` and `enrichedIdentities`, emitted only when non-empty so
  non-enrichment reports keep their bytes. 13 Python unit tests for the helper.
- **Codex pass C (2026-09-07, read-only, on 9488e26): REFUSED** — F-C3 HIGH token-overlap novelty is evadable (a semantic
  restatement scored 0.27 and passed) and the threshold was unbounded; F-C4 HIGH `SourcePool` rewrote only claim/evidence
  references, leaving identity and relationship `sourceRefs` on the wrong source, and re-installing an already-renamed
  origin threw; F-C1 MEDIUM `current_occurrence` ignored `semanticVariantStatus`; F-C2 MEDIUM the rollback test's
  `Assumptions` guard could skip silently. F-C5/F-C6 INFO: F-B7 closed, suites verified. Report:
  `temp/codex-uaofoundry-adr0007-ratification-pass-c-001.md`.
- **Remediation (fourth commit):** novelty is an operator attestation, not a computation — without `--accept` the bundle
  builder lists every provider claim about the target beside its nearest registered assertion (overlap score,
  `PARAPHRASE?` flag as a triage aid) and exits 2; only claims named with `--accept <candidateId>` enter the bundle, each
  recorded in `authorityNotes` with its score; exact restatements can never be accepted; the threshold must be a finite
  number in [0, 1]. `SourcePool.install` rewrites `sourceRefs`/`sourceRef` in every record handed to it (identities,
  claims, evidence, relationships) and is idempotent for an already-renamed origin; `disambiguate_ids` renames restated
  claim/evidence ids that collide with live ones (found on Macleay reconcile runs 16/23). `current_occurrence` refuses any
  identity whose `semanticVariantStatus` is not `SINGLE_VARIANT`. The rollback test squats the journal path with a plain
  file, so `register()` succeeds and only the ENRICH record write fails, with no permission dependence and no skip
  path. Python 30/30 at that commit (helper + builder-level tests on a synthetic registry).
- **Codex pass D (2026-09-07, read-only, on 60df8cc): REFUSED** — F-D1 HIGH `SourcePool` reused an id for "same origin"
  without comparing bytes, so a live package legitimately carrying both `src-x` and `src-x--live-<suffix>` could have the
  second collapsed onto the first; F-D2 MEDIUM the builder kept one scalar target candidate id, so a target represented
  by two live candidates (same resolution key, an input topology the pipeline supports) had only one candidate's claims
  reviewed; F-D3 LOW the output directory was created before the refusal; F-D5 INFO the ADR's Python count was wrong
  (30, not 29) and one trailing space. F-D4 INFO: attestation, threshold, reference rewrite, id disambiguation and
  variant-status guard close as designed; F-C2 closes. Report: `temp/codex-uaofoundry-adr0007-ratification-pass-d-001.md`.
- **Remediation (fifth commit):** an id is reused only for the same bytes (equal registry sha256, or an identical source
  record); same origin with different bytes gets a distinct, deterministic id like any other collision. The builder
  treats every live candidate resolving to the target as the target for review and attestation, and restates the
  registry's assertions once per identity, not once per candidate. Nothing is written, not even the output directory,
  unless a bundle is built. Python 33/33.
- **Codex pass E (2026-09-07, read-only, on af79005): REFUSED** — F-E1 HIGH the collision rewrite was applied per source
  to the same record list, so with `src-x` declared before an explicit `src-x--live-<suffix>` a reference was moved
  twice and ended on the wrong bytes; F-E2 MEDIUM the builder restated only the FIRST registry candidate for a key,
  dropping assertions held by a second candidate of the same identity; F-E3 MEDIUM `reconcile_reuse.py` restated an
  identity once per live candidate, duplicating its assertions; F-E4 MEDIUM the live adapter's "add no new claim to a
  reused identity" rule contradicted this ADR's live acquisition path and the protocol carried no enrichment intent;
  F-E5 LOW the whitespace fix in af79005 was a no-op and the ADR's fifth-remediation prose overstated closure. F-E6:
  F-D3 closed. Report: `temp/codex-uaofoundry-adr0007-ratification-pass-e-001.md`. The pass-D remediation entry above
  stands as written at the time; pass E shows F-D1 and F-D2 were only partly closed by it.
- **Remediation (sixth commit):** `SourcePool.install_origin` is two-phase — every source of one origin is planned
  first (an id is reused only for the same bytes; a colliding id gets a distinct deterministic name that also avoids
  the origin's own declared ids), and only then is each reference in the origin's records rewritten exactly once
  through that plan, so no rewrite can chain whatever the declaration order. One shared `restate_identity` now serves
  both builders: identity fields from the first registry candidate for the key, claims and evidence from ALL of them,
  copied once per identity. The provider protocol carries `constraints.enrichmentTargets` (the `--enrich` uids, sorted,
  absent when empty) from `RegistryAwareCommandProvider`, and the adapter demands verbatim restatement PLUS new sourced
  claims for exactly those uids while keeping the no-new-claim rule for every other reused identity. Trailing
  whitespace actually removed. Python 36/36 (helper, builder-level and reconcile-level tests on the synthetic
  registry) + adapter 14/14; Java 182/182, 0 skipped. ⚠ The jar was not rebuilt in this commit (a live batch was using
  it); the console `--enrich` live acquisition path needs the rebuilt jar before its first use.
- **Codex pass F (2026-09-07, read-only, on 971123d): REFUSED** — F-F1 HIGH `restate_identity` took names and
  identifiers from the first registry candidate in file order, while canonicalisation sorts candidates by id and unions
  aliases and external identifiers, so a restatement could rename an identity or forget its names — and the enrichment
  law, checking only assertion containment, would have admitted that regression; F-F2 MEDIUM evidence attached directly
  to identity candidates was not restated; F-F3 HIGH `--enrich` accepted several uids while `RegistryApplication enrich`
  admits one subject, leaving a second target an unreconciled variant; F-F4 MEDIUM the reused-identity no-new-claim
  rule was emitted only in relationship-edition mode, so the adapter and the reuse gate disagreed without an edition.
  F-F5/F-F6 INFO: F-E1 and the assertion halves of F-E2/F-E3 closed; counts and whitespace verified. Report:
  `temp/codex-uaofoundry-adr0007-ratification-pass-f-001.md`. The sixth-remediation entry's "claims and evidence from
  ALL candidates" and "every other reused identity" claims were overstated, as pass F shows.
- **Remediation (seventh commit):** the enrichment law now has two halves, both re-derived from package bytes at
  admission, in the analyzer and on every index rebuild: **identity continuity** (`identityContinuityDefect`: the
  canonical label stays; aliases and external identifiers may only grow — analyzer code
  `ENRICHMENT_IDENTITY_REGRESSION`) and then the strict assertion superset. `restate_identity` takes the identity's
  label, aliases and external identifiers from the registry's canonical UAO (the fields the variant digest covers),
  unions `sourceRefs` over the registry candidates, and restates evidence attached to identity candidates as well as to
  claims. `--enrich` accepts exactly one uid per manufacture. The reused-identity rule is emitted by the adapter with
  or without a relationship edition, and the enrichment-target rule tells the provider to keep names and identifiers.
  Python 36/36 + adapter 14/14; Java 183/183, 0 skipped. ⚠ Jar still not rebuilt (live batch in flight).
