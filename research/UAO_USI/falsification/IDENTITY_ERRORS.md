# Identity Errors and Defects Found

**Status:** RESEARCH / NON-AUTHORITATIVE
**Phase:** 0–10

Every defect this programme found, including the ones it caused.

## Defects introduced by this programme, and fixed

### PIMA-D1 — alias provenance broke cross-source reuse *(Phase 3, fixed Phase 8)*

`alias_provenance` was added inside `foundry_identity`, and `SemanticVariants.digest` strips only
`source_refs`. Candidate and source refs were therefore inside the meaning-bearing projection, so
**the same identity acquired from a differently-named source produced a different semantic-variant
digest** → `MULTIPLE_UNRECONCILED_VARIANTS` → automatic reuse refused.

This defeats the central claim of persistent identity: that one identity may be evidenced
repeatedly from different places.

*Why the tests missed it:* every reuse test re-manufactured from the **same** fixture, with
identical candidate and source ids. Only building a realistic two-run demonstration exposed it.
Lesson recorded: a test suite that only ever replays identical inputs cannot detect a digest that
is over-sensitive to inputs.

Fixed by excluding `alias_provenance` from the variant digest for the same stated reason as
`source_refs`. Regression test `oneIdentityEvidencedFromDifferentSourcesIsStillOneIdentity`,
mutation-verified.

### PIMA-D2 — every merge read as a cycle *(Phase 4, fixed in Phase 4)*

A `MERGE` names every participant as a subject, survivor included. The lifecycle derivation treated
the survivor as a link in a supersession chain pointing at itself and rejected every merge as a
cycle. Caught by the adversarial tests before it left the phase.

### PIMA-D3 — lifecycle reason codes missing from the decision schema *(Phase 4, fixed in Phase 4)*

Manufacture against a registry holding a retired identity failed schema validation, because
`IDENTITY_RETIRED` and its siblings were added to the resolver but not to the closed enum in
`identity-decision.schema.json`. Caught by the same adversarial tests.

## Defects found in accepted main `2bc2871d`

### P0-1 — `externalIdentifiers` accepted and silently discarded

Declared in `schemas/candidate-identity.schema.json`, supplied by every fixture and by the Claude
adapter, **read by no Java code**. A provider correctly supplying a durable external identifier —
exactly what `STABLE-SEMANTIC-IDENTITY.md §2.2` asks for — had that evidence dropped without
warning. Fail-open behaviour in an otherwise rigorously fail-closed codebase.

Latent, because the field was always empty in practice. Closed in Phase 1.

### P5-1 — a relationship could name an identity that does not exist

`relationship-bearing-cow.json` declares participant `cid-species`, which is **not among its
candidate identities**. The Foundry accepted this silently for as long as the fixture has existed,
because the unresolved finding discarded participants entirely — while an unmapped *claim* subject
has always thrown.

Test fixture only, so no manufactured knowledge is affected. Now visible as `binding: UNRESOLVED` /
`PARTIALLY_BOUND`. Not "fixed" beyond being made visible: §16 requires the binding to remain
unresolved, not the candidate to be discarded.

### P9-1 — package-id collision blocks repeated observation **(open, not fixed)**

`reuse-report.json` is written **inside** the content-addressed package but is **excluded** from
`PackageContentDigest.CORE_FILES`, which determines `packageId`. The report embeds
`registryIndexHash`, `registryContextHash` and `priorOccurrences`, all of which move as the
registry grows.

Consequence: manufacturing the same material a **third** time against a registry that has moved on
produces the *same* `packageId` with *different bytes*. Both guards fire:

```
Package output collision: existing path has different content: …/UAO-CHANGELOG…
Registry package-id collision with different immutable content: pkg-b2e6ba9164927b66
```

Reproduced minimally: manufacture `CHANGELOG.md` unchanged three times, moving the registry on
between each. First is new (`pkg-af6f…`), second and third are reused and share `pkg-b2e6…`; the
third is refused. The only differing files are `reuse-report.json` and `checksums.sha256`.

**Measured impact:** in the cumulative experiment, 114 attempted manufactures across six
observations of one codebase produced **69 refusals attributable to P9-1** — against 8 refusals
from the intended fail-closed behaviour.

**This caps H1 independently of ASA#29.** A registry that cannot accumulate observations past the
third cannot demonstrate reduced repeated reconstruction.

`PackageContentDigest.java` is byte-unchanged from `2bc2871d`, so this is **pre-existing in
accepted main**, surfaced rather than caused by this programme.

**Not fixed here, deliberately.** Every available fix is a design decision on an audited surface:

| Option | Cost |
|---|---|
| include `reuse-report.json` in the content digest | identical manufactured knowledge would get different ids depending on *when* it was made, undermining content addressing of knowledge |
| move the reuse report outside the package | changes the documented package contract (`README.md` lists it as package output) |
| compare only the meaning-bearing projection on collision | requires deciding what happens to the second, differing reuse report — a semantic choice |

Recommended for the repository owner, with option 2 preferred: the reuse report is *relative to a
registry state*, not intrinsic to the manufactured knowledge, and does not belong inside an
artefact whose identity is its content.

## Non-defect worth recording

### Content changing under a stable address

Addressing a file by path while carrying its content hash as durable external identity means an
edit triggers `EXTERNAL_IDENTIFIER_CONTRADICTION` and manufacture stops. 8 of 114 cumulative
manufactures refused this way.

**This is the fail-closed guard working as designed**, not a defect. But it makes the modelling
choice explicit: address-by-path gives stable identity across edits and unstable evidence;
address-by-content-hash gives stable evidence and a new identity per edit. Neither is right for
code, and the tension is the central unsolved problem of persistent identity in a codebase.
Recorded for whoever designs the next iteration.
