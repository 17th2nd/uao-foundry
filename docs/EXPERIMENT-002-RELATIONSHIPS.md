# Experiment 002 — Typed Relationships Under an RTR-Format Edition

**Status:** experimental (Founder-authorised continuation, 2026-09-05) · **Authority created here:** none
**Branch:** `experiment/002-relationships-visual-bridge` · **Baseline:** Six Cyberneticians registry (6 packages, 19 identities)

## What changed upstream, and what did not

ASA now holds a governed **Relationship Type Registry facet** (`specification/registry/relationship_types.json`,
edition 2026.2, ASA-SPEC-0006, adopted by D-040 on 2026-08-31, digest
`sha256:a0c6a69c…7059c`). Its admitted types are the five `asa.core` meta-types (supports, challenges,
contradicts, supersedes, stance) and six `asa.cc0` Orchard-Zero claim types. **None of the domain predicates
this experiment needs (author-of, co-created, created, developed, architect-of, presented, …) is admitted**,
`17th2nd/ASA#29` is still open on GitHub, and the ASA commits carrying the facet are on a local/baseline branch,
not `origin/main`. `config/upstream-authority-lock.json` records this review.

So the Foundry's posture is: **consume RTR-format editions and fail closed on `RTR-EDITION-MISMATCH` /
`RTR-DIGEST-MISMATCH`** exactly as ASA-SPEC-0006 §9 requires — but bind this experiment's predicates only
through a **Foundry-local proposed edition**, never by pretending ASA admitted them.

## The edition

`config/relationship-types/foundry-exp002.json` — registry_version `2026.902`, `provenance_anchor: PROPOSED`,
22 records: the five `asa.core` meta-types **verbatim** (RTR conformance requires the core set) plus 17
`foundry.exp002` domain types, every one `admission.state: proposed` with `decision_ref: null`. Built by
`scripts/exp002/build_facet.py` using the ASA kernel's JCS implementation for `definition_hash` and the
registry digest; the Java loader recomputes both independently, and `RelationshipEditionTest` proves the ASA
2026.2 facet recomputes byte-for-byte in Java (cross-implementation agreement).

## The primitive

`--relationship-edition <facet>` (console, `RegistryManufactureApplication`, and the Claude adapter via
`UAO_FOUNDRY_RELATIONSHIP_EDITION`) is **never inferred**. Without it, stage 11 is unchanged: every candidate
stays `URO_TYPE_AUTHORITY_UNAVAILABLE`, the package is `EVIDENCE_INCOMPLETE`, and nothing accumulates.

With it, for each candidate relationship:

| Step | Result on failure |
|---|---|
| type resolves in the edition (RTR §9 step 4) | unresolved `RTR-TYPE-UNKNOWN` |
| every participant binds to a persistent uid | unresolved `PARTICIPANT_UNBOUND` (never invented) |
| RTR §10.1 V1–V6, V8 instance validation | unresolved `URO-INSTANCE-INVALID` + §10.2 diagnostics |
| otherwise | an **experimental typed relationship record** |

Any unresolved candidate keeps the package inadmissible. A record (`schemas/experimental-relationship.schema.json`):

- `relationshipId` `urx-<12 hex>` — pure function of type id + identity-bearing role bindings + identity
  literals, so the same relationship restated in another package resolves to the same id and accumulates as
  a second occurrence;
- `typeEdition` {registryVersion, digest, namespace, admissionState, admissionOverride};
- participants (role, candidate ref, uid), `sourceRefs`, `basis` EXPLICIT | INFERRED | UNSTATED, a rendered
  `statement`, `stateVersion`;
- `diagnostics` that are **undetermined and never collapsed**: `ARCHITECTURAL-UNCERTAINTY AU-1` (CSS 2026.2
  `uri_versioned` rejects a non-`asa.core` type id — ASA-SPEC-0006 §10.3) and `RTR-TYPE-NOT-ADMITTED`
  (proposed type bound under `FOUNDRY_EXPERIMENT_002_OPERATOR_AUTHORISED`);
- `status: EXPERIMENTAL_TYPED_RELATIONSHIP`, `certifying: false`.

**It is not a CSS URO.** `canonical-relationships.json` / `manufactured-package.uros` stay empty and the
`URO_FAIL_CLOSED_TYPE_AUTHORITY` check still passes. Records live in `experimental-relationships.json` beside a
full copy of the edition (`relationship-type-edition.json`); both are meaning-bearing for the package content
digest. The verifier reconstructs every record and every unresolved finding from the package's own candidates
and embedded edition and demands byte-equality, then re-derives each `stateVersion`.

## Registry surface

The index gains `relationships` (only when some package carries them, so older registries verify unchanged),
`relationshipNeighbourhood(uid)` and `graph()`; the console gains `relationships <ref>` and `graph [--json]`.
Occurrences of one relationship id must agree on type and participants or the index build fails closed.

## Reuse discipline (what Phase 3 showed)

Reuse in this engine means **re-observation with identical meaning**: the semantic-variant digest covers
`assertions`, so re-stating an identity with any re-worded or additional claim is a divergent variant that
automatic reuse refuses. Enrichment of a registered identity therefore happens **through relationships**, whose
records live outside the identity's digest. `scripts/exp002/relate.py` builds reuse-only bundles (verbatim
identities/claims/evidence over `registry://` sources + operator-proposed typed relationships); on the six-person
registry: 13 edges, 19 identities reused, 0 new, 36 registry sources reused, 0 provider calls.
