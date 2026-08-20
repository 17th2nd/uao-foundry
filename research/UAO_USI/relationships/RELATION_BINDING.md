# Relationship Binding to Persistent Identity

**Status:** IMPLEMENTED — FOUNDRY-OWNED (identity binding)
**Also:** BLOCKED ON UPSTREAM AUTHORITY (`17th2nd/ASA#29`) for canonical URO publication
**Phase:** 5
**Contract:** `schemas/unresolved-relationship.schema.json`

## 1. Two separable problems, one of which is not blocked

A relationship candidate poses two questions:

| Question | Needs | Status |
|---|---|---|
| *Which objects does this relate?* | identity resolution | **the Foundry can answer this** |
| *Is `container` a legal role of `asa.core/contains@1`, at what cardinality, over which participant kinds?* | governed Relationship Type role authority | **blocked on ASA#29** |

Before Phase 5 both were treated as blocked together, and the retained finding recorded only:

```json
{"candidateId": "rel-…", "code": "URO_TYPE_AUTHORITY_UNAVAILABLE", "description": "…"}
```

The participants were **dropped entirely**. A relationship candidate was retained "as evidence"
while discarding the part of it that said what it was about.

## 2. What binding changes, and what it must not

Participants are now bound to persistent uids where the Foundry can resolve them:

```json
{
  "candidateId": "rel-qualification-structure",
  "code": "URO_TYPE_AUTHORITY_UNAVAILABLE",
  "typeVersion": "asa.core/contains@1",
  "participants": [
    {"role": "container", "candidateIdentityRef": "cid-root",    "binding": "RESOLVED", "uaoId": "uao-7fde0894bfbc"},
    {"role": "member",    "candidateIdentityRef": "cid-species", "binding": "UNRESOLVED"}
  ],
  "identityBindingStatus": "PARTIALLY_BOUND",
  "identityLiterals": {}, "contextualBindings": [], "sourceRefs": ["src-cow-bio"]
}
```

**The fail-closed boundary is exactly where it was.** `canonicalUros = 0`,
`URO_TYPE_AUTHORITY_UNAVAILABLE`, `EVIDENCE_INCOMPLETE`, `eligible: false`, and every UAO's
`relationship_references` still empty.

`ALL_PARTICIPANTS_BOUND` **does not mean publishable**. It means the identity half is solved and
only the ASA#29 half remains. `aFullyBoundRelationshipIsStillNotPublishable` exists specifically to
stop that misreading taking hold.

## 3. Identity certainty is never fabricated

§16: *"If identity is unresolved, relationship binding must remain unresolved. Never fabricate
identity certainty merely to complete a relation."*

An unresolvable participant gets `binding: UNRESOLVED` and **no** `uaoId`. The schema permits
`uaoId` only alongside `RESOLVED`. A forged binding fails verification, because the verifier
re-derives the whole projection from candidate material.

Note the Foundry **records** rather than **rejects** an unresolvable participant. Rejecting would
discard evidence, and §16 asks for the binding to remain unresolved, not for the candidate to be
thrown away.

## 4. Finding P5-1 — a dangling participant reference was invisible

`src/test/resources/fixtures/relationship-bearing-cow.json` declares a participant
`cid-species`, which is **not among its candidate identities**. Its identity candidates are
`cid-root` and `cid-bovine-context` only.

The Foundry accepted this silently for as long as the fixture has existed, because the unresolved
finding discarded participants. Contrast with candidate *claims*, where an unmapped
`subjectIdentityRef` throws in `canonicalBuild`.

It now shows as `binding: UNRESOLVED` / `PARTIALLY_BOUND`. This is a test fixture, so no
manufactured knowledge is affected, but it demonstrates precisely the class of defect the binding
closes: a relation could name something that does not exist and nothing noticed.

## 5. Traceability

The registry aggregates `relationshipBindings` per identity from each package's retained
candidates — package, candidate id, `typeVersion`, role, binding status, and explicitly
`canonicalUroPublished: false` with `blockedBy: URO_TYPE_AUTHORITY_UNAVAILABLE`.

A relationship stated in one package is now findable from the identity it mentions. Before binding
it pointed only at bundle-local `cid-` handles, which `STABLE-SEMANTIC-IDENTITY.md §4` explicitly
declares non-semantic, and was unfindable outside its own package.

Caveat, stated plainly: a package containing a relationship candidate is `EVIDENCE_INCOMPLETE` and
therefore **not registry-admissible** today. Cross-package tracing is consequently latent — the
binding is durable and correct inside the package, and becomes traceable through the registry the
moment ASA#29 permits such packages to be admitted. Tested honestly in
`aBoundRelationshipIsTraceableFromTheIdentityItMentions`, which asserts the inadmissibility rather
than pretending otherwise.

## 6. ASA#29 extension seams

Recorded verbatim, validated against nothing:

| Seam | Where it is today |
|---|---|
| `relationshipTypeId` / `relationshipTypeVersion` | `typeVersion`, `^asa\.core/[a-z_]+@[0-9]+$` |
| named roles | `participants[].role`, free string |
| identity-bearing fields | `identityLiterals` |
| contextual fields | `contextualBindings` |
| role cardinality | **absent** — needs the registry |
| allowed participant kinds | **absent** — needs the registry |
| symmetry / ordering | **absent** — needs the registry |
| transfer signature | see `../significance/TRANSFER_SIGNATURE_CANDIDATE.md` |

When ASA#29 closes, the work is to validate these against the governed registry and only then lift
the publication gate. Nothing here anticipates the answer.
