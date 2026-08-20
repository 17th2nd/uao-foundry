# Relationship Staging (Non-Canonical)

**Status:** implemented (directive §18) · **Authority created here:** none

## Purpose

The Persistent Identity Alpha established that `17th2nd/ASA#29` blocks more than publication: a
package carrying a relationship candidate is `EVIDENCE_INCOMPLETE` and therefore inadmissible to
the registry, so relationship bindings never accumulate and a later session has nothing to reuse.
That made benchmark hypotheses H1 (relationship reconstruction) and H2 (relationship precision)
*not testable* rather than disproved.

The staging store retains identity-bound relationship candidates across manufactures so persistent
relationship reconstruction can be **studied and measured** while the upstream authority gap
remains open. It is candidate relationship **memory**, not a certified relationship graph.

## What staging is not

Nothing in the staging layer:

- creates a URO or any ASA authority;
- changes a publication decision — a relationship-bearing package remains `EVIDENCE_INCOMPLETE`
  and inadmissible to the registry, exactly as before;
- enters the registry index, which stays derived from packages and identity operations only;
- is consulted by manufacture, identity resolution, verification or registry admission;
- feeds `R_x` — the significance relational input remains structurally empty with
  `blockedBy: 17th2nd/ASA#29` until governed relationship authority exists.

## Ownership boundary

```text
Identity Registry                owns persistent referent resolution, aliases,
                                 provenance, state/version history

Relationship Staging             owns candidate retention, evidence references,
                                 proposed participant bindings, unresolved authority state

ASA Relationship Type Authority  owns canonical role schema, cardinality, participant
                                 kinds, relationship semantics — NOT implemented here
```

The Foundry implements the first two. It does not invent the third.

## Storage

The store is a **sibling of the registry, never a child**:

```text
<home>/
├── registry/               index.json · packages/ · identity-operations/
├── runs/                   run evidence (ADR-0006)
└── staged-relationships/   stg-<16 hex>.json · non-canonical candidate memory
```

Keeping it outside the registry root makes "this is not registry content" structural rather than a
convention someone must remember. Records are content-addressed (`stg-` + 16 hex over the record
projection), append-preserving and idempotent: re-staging identical content changes nothing, and an
id collision with different content is refused, matching the discipline of identity operations and
run records.

## Record shape and non-canonical labelling

`schemas/staged-relationship.schema.json` pins three labels as **schema constants**, so a record
cannot be written without them and a consumer cannot read one as governed:

```text
status:           NON_CANONICAL_CANDIDATE_MEMORY
authorityStatus:  URO_TYPE_AUTHORITY_UNAVAILABLE
certifying:       false
```

Reading the store re-derives each record's content address and re-checks these labels. A record
that has lost its non-canonical labelling, or whose file name does not match its id, **fails
closed**: the read throws rather than returning a record that could be mistaken for authority.

Each record carries the candidate's `typeVersion`, participants (role, candidate identity
reference, binding state and — where resolved — the persistent uid), identity binding status,
identity literals, contextual bindings, source references, originating `packageId`, original
`candidateId` and `recordedAt`.

## Identity binding

Participants bind to persistent uids **where the relationship-construction stage already resolved
them**. Staging copies that evidence; it does not re-derive, improve or force it:

- a resolved participant carries `binding: RESOLVED` and its `uao-<12 hex>` uid;
- an unresolved participant carries `binding: UNRESOLVED` and stays that way. Identity resolution
  is never forced to complete a relationship.

## Lifecycle

The Alpha keeps the lifecycle minimal and factual, and mints no parallel state vocabulary. A
candidate is observably in these conditions, each derivable from artefacts rather than from a
mutable status field:

```text
retained   the candidate exists in an immutable package (unresolved-items.json)
staged     a content-addressed copy exists in the staging store
```

The store is append-only: a re-observation with identical content is idempotent, a re-observation
with different content becomes its own record beside the first (both preserved, distinguishable by
`recordedAt` and originating `packageId`), and nothing is ever edited or deleted. Explicit
`VALIDATED` / `REJECTED` / `SUPERSEDED` dispositions are deliberately absent — each would be a
relationship-authority judgement, which is exactly what the Foundry must not invent while ASA#29
is open.

`STAGED ≠ CANONICAL`, and `URO_TYPE_AUTHORITY_UNAVAILABLE ≠ INVALID`: authority unavailability
means canonicalisation cannot currently be justified, not that the observed relationship is wrong.

## Operator surface

The application exposes staging read-only:

- `GET /api/staged-relationships/{ref}` — the staged candidate neighbourhood of one **exactly
  resolved** identity: edges, neighbour uids, contributing packages, and the authority caveat. An
  unresolved reference is refused (`IDENTITY_AMBIGUITY`) rather than guessed.
- The identity inspector renders the neighbourhood under an explicit *non-canonical candidate
  memory* banner with per-participant resolved/unresolved state, evidence references and the
  originating package.
- Plant status reports the staged candidate count and the store path, and surfaces a fail-closed
  store error if the labelling check ever trips.

The UI must never visually imply a staged candidate is a canonical URO; the banner and the
`certifying: false` badge are part of the contract, and the packaged-UI tests assert the wording.

## Negative-space compatibility

Staging records **observed** candidate assertions only. It deliberately leaves room for a later
research layer to compare *expected* relationships against *observed* ones under a certified
observation scope, without confusing the two: absence of a staged record is not evidence of
absence, and nothing in the store encodes `EXPECTED`. (`EXPECTED ≠ OBSERVED`, `MISSING ≠ FALSE`,
`UNKNOWN ≠ ABSENT`.)

## Relation to ASA#29

This store is the implementation of the "staged closure" observation recorded in the PIMA handoff
§14: the Foundry can already resolve relationship participants to persistent identities without any
type authority — only role validation is blocked. Staging unblocks *accumulation for study*. It
does not close, work around or prejudge the issue: canonical URO publication remains fail-closed
until governed Relationship Type authority exists upstream, and whatever that authority decides,
staged records remain what they always were — retained evidence.
