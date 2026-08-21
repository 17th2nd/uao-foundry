# Identity Model — Persistent Identity Kernel

**Status:** IMPLEMENTED — FOUNDRY-OWNED
**Creates ASA authority:** No
**Phase:** 1
**Implementation:** `src/main/java/org/seventeenthsecond/uaofoundry/identity/`
**Contract:** `schemas/foundry-identity.schema.json`

## 1. The problem this solves

Before Phase 1 the Foundry *derived* identity but never *resolved* it:

```
uid = "uao-" + sha256(resolutionKey)[0..12]
```

Two references were the same object if and only if a provider supplied the byte-identical
canonical key. Nothing consulted aliases or external identifiers, nothing recorded why a
determination was made, and nothing could answer the question the programme requires:

> Why do we think these references are the same object?

The key was an *assertion of identity*, treated as though it were *evidence of identity*.

## 2. What was added

A resolution layer strictly **above** the existing derivation. The derivation is unchanged —
`uid` is still `sha256(resolution_key)` and every previously manufactured uid is stable. Verified
by test: `stateChangeDoesNotCreateANewIdentity` and the untouched baseline suite.

## 3. The kernel

Carried in `internal_state.foundry_identity`, because the ASA UAO validation projection is a
closed schema (`additionalProperties: false`) and the Foundry may not add top-level UAO members.

```json
{
  "canonical_label":      "domestic cattle",
  "aliases":              ["cattle"],
  "resolution_key":       "fixture:biology:domestic-cattle",
  "semantic_type":        "biology",
  "external_identifiers": {"wikidata": "Q830"},
  "identity_digest":      "<sha256>",
  "state_version":        "<sha256>",
  "source_refs":          ["src-cow-bio"]
}
```

`uid` and `lifecycle_status` are deliberately **not** restated here. Both already exist as
ASA-governed top-level UAO members; duplicating them would create two places to disagree.

### Mapping to the programme's target kernel (§6)

| Programme field | Where it lives | Status |
|---|---|---|
| `uid` | UAO top level (ASA) | pre-existing |
| `resolutionKey` | `foundry_identity.resolution_key` | pre-existing |
| `semanticType` | `foundry_identity.semantic_type` | **added** |
| `canonicalLabel` | `foundry_identity.canonical_label` | pre-existing |
| `aliases` | `foundry_identity.aliases` | pre-existing |
| `externalIdentifiers` | `foundry_identity.external_identifiers` | **added** (closes P0-1) |
| `stateVersion` | `foundry_identity.state_version` | **added** |
| `provenance` | `uao.provenance` (ASA) + `source_refs` | pre-existing |
| `lifecycleState` | `uao.lifecycle_status` (ASA) | pre-existing, unused beyond `Registered` |

Fields were added only where they earned inclusion. Nothing was added because the brief listed it.

## 4. Identity versus state

The programme models one identity carrying a sequence of states. The Foundry already guaranteed
the strong half — no state change can alter a uid — but that was invisible. Two derived digests
make it observable:

| Digest | Covers | Moves when |
|---|---|---|
| `identity_digest` | uid, resolution_key, semantic_type, canonical_label, aliases, external_identifiers | what the identity **is** changes |
| `state_version` | lifecycle_status, successor_identity_ref, assertions, relationship_references | what the identity **asserts** changes |

`source_refs` is in neither: it is provenance about how the identity was evidenced, not part of
what it is.

Both are **derived, never authored**. A provider cannot supply them and the package verifier
re-derives both from independently reconstructed candidate material. Forging either fails
verification — proven by `aForgedIdentityDigestFailsVerification` and
`aForgedStateVersionFailsVerification`, both of which were mutation-tested by disabling the
verifier check and confirming they fail.

Neither digest is a significance, confidence, ranking or priority value. They are content
addresses over disjoint projections and carry no ordering meaning. See
`../significance/SIGNIFICANCE_INTERFACE.md`.

## 5. `semantic_type` and honest absence

The semantic type is extracted from the key grammar rather than invented:

| Namespace | Semantic type |
|---|---|
| `foundry:v0.1:<type>:<label>` | `<type>` |
| `fixture:<type>:<label>` | `<type>` |
| `ext:<scheme>:<identifier>` | **`null`** |

An external registry identifier says *which* object is meant without saying *what kind* of object
it is. A type inferred from the scheme would be indistinguishable from a declared one, so the
field is explicitly null and the schema requires it to be present. Absence is recorded, not
papered over.

## 6. External identifiers are evidence, not identity

`ExternalIdentifiers` applies the same rigour `ResolutionKeys` applies to keys: lower-case scheme
matching `^[a-z][a-z0-9._-]*$`, NFKC-normalised, whitespace-free, non-blank values, rejected
rather than silently repaired. An identifier that is not canonical cannot compare equal to itself
across runs, which would silently split one identity in two.

Two fail-closed consistency guards exist:

1. **Self-contradiction.** A candidate keyed `ext:wikidata:Q830` that declares
   `wikidata = Q99999` names two objects with one address and is refused at manufacture.
2. **Group contradiction.** Candidates grouped under one resolution key that declare conflicting
   identifiers for a shared scheme are refused. There is no winner to pick.

The same guard runs again at registry index build, so two immutable packages cannot combine into
a contradictory registered identity.

## 7. Why there is no confidence score

The decision space is `SAME` / `DIFFERENT` / `UNRESOLVED` with reason codes, and deliberately
carries no numeric confidence. A confidence value invites ordering, then thresholding, then
"reuse above 0.9" — which is exactly the silent automatic merge that the semantic variant policy
exists to prevent. See `IDENTITY_STATES.md`.

## 8. What Phase 1 did not do

- No merge, split, supersession or retirement. Phase 4.
- No identity decision *records* persisted into packages. Phase 2 — the resolver returns decisions
  but nothing yet writes an append-preserving decision history.
- No manufacture-time consultation of the resolver. `resolveCandidate` exists and is tested, but
  the pipeline does not yet call it. Wiring it changes manufacture behaviour and belongs with the
  Phase 2 provenance record so that any reuse it causes is evidenced.
