# Aliases

**Status:** IMPLEMENTED — FOUNDRY-OWNED
**Phase:** 1 (names) / 3 (provenance)

## 1. An alias routes toward identity; it is never identity

The single most important rule. Two things are routinely called the same word, and the same thing
is routinely called several. `IdentityResolver` therefore returns `UNRESOLVED /
ALIAS_MATCH_INSUFFICIENT` for **every** alias reference, however many identities match — including
when exactly one does.

Tested by `aSharedAliasDoesNotResolveToTheSameIdentity` (a bovine and a cattle crush both aliased
"cow") and `anAliasReturnsCandidatesRatherThanAnIdentity` (a sole match still does not resolve).

The registry's exact-lookup surface `identityRecord` delegates to the same resolver, so the lookup
API cannot become a back door to identity-by-name.

## 2. Alias kinds

The programme lists human label, file path, URI, external database ID, Git identifier, historical
name and model-generated reference. The Foundry currently distinguishes two tiers, by capability
rather than by taxonomy:

| Tier | Members | Can establish identity? |
|---|---|---|
| Durable third-party identifier | `external_identifiers`, `ext:` keys | yes, when unambiguous |
| Name | canonical label, aliases | **never** |

Paths, URIs and Git identifiers are not yet modelled as distinct kinds. When they are, the test to
apply is whether the issuing system guarantees stability — a path does not, a content hash does.
Until then they enter as names and correctly fail to establish identity.

## 3. Provenance

Every name is recorded with the candidate that used it and the sources behind that candidate:

```json
"alias_provenance": [
  {"alias": "cattle",          "candidateRefs": ["cid-bovine-context"], "sourceRefs": ["src-cow-bio"]},
  {"alias": "domestic cattle", "candidateRefs": ["cid-bovine-context"], "sourceRefs": ["src-cow-bio"]}
]
```

The canonical label is included. §9 treats a human label as one alias kind among many, and a label
carries no more inherent authority than any other name — recording it separately would imply
otherwise.

A name without provenance cannot later be weighed against a competing name, which is what any
future alias-driven disambiguation will need.

### Outside `identity_digest`

Alias provenance is excluded from the identity digest, exactly as `source_refs` is. It records
*how* the identity was evidenced, not *what* it is: acquiring a second source for a name already
known must not read as a change of identity. The names themselves *are* inside the digest, so a
genuine rename is visible — see `renameUnderOneAddressPreservesIdentity`.

Fully derivable from candidate material, so the package verifier reconstructs it independently and
a forged provenance record fails verification.

## 4. Not yet done

**Time-awareness.** §9 asks for aliases to be time-aware "where needed". They are not. A name is
currently recorded as observed, without validity intervals, so a historical name and a current name
are indistinguishable within one package.

Partial mitigation exists by accretion: each package occurrence is a point-in-time observation, so
the sequence of occurrences already carries a coarse chronology via the registry's occurrence
history. Genuine validity intervals would need a governed time model, and inventing one here would
be exactly the kind of unearned machinery §5 of the programme warns against. Recorded as open
rather than quietly skipped.
