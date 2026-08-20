# Identity Provenance

**Status:** IMPLEMENTED — FOUNDRY-OWNED
**Phase:** 2
**Contract:** `schemas/identity-decision.schema.json`
**Carried in:** `identity-resolution.json` → `identityDecisions[]`, inside every immutable package

## 1. The question that must stay answerable

> **Why do we think these references are the same object?**

Before Phase 2 nothing could answer it. The `resolutionKey` was an *assertion* of identity that the
pipeline treated as *evidence* of identity, and once manufacture finished no artefact recorded
whether the Foundry had looked for a prior identity, what it found, or why it concluded what it did.

## 2. What a decision record contains

One record per resolved identity:

```json
{
  "uaoId":         "uao-7fde0894bfbc",
  "reference":     {"kind": "RESOLUTION_KEY", "value": "fixture:biology:adult-female-cattle"},
  "decision":      "SAME",
  "reasonCodes":   ["EXACT_RESOLUTION_KEY_MATCH"],
  "uid":           "uao-7fde0894bfbc",
  "resolutionKey": "fixture:biology:adult-female-cattle",
  "candidateUids": ["uao-7fde0894bfbc"],
  "candidateRefs": ["cid-root"],
  "sourceRefs":    ["src-cow-bio"]
}
```

Evidence, not only verdict: the reference resolved, the registered identities the evidence pointed
at (including when it pointed at several), the provider candidates the decision was made over, and
the acquired sources that supported them.

## 3. Append-preserving by construction, not by discipline

Decision records live **inside immutable packages**. A later manufacture produces a new package
carrying its own decisions; it cannot revise an earlier one, because packages are content-addressed
and any edit breaks the content digest, the checksum inventory and registry custody.

This is why decisions were not put in a mutable journal beside the registry index: a journal would
need a *rule* against rewriting, whereas package immutability makes rewriting structurally
impossible. History accumulates by accretion.

Tested by `earlierDeterminationsAreNeverRewrittenByLaterOnes`: the first package records
"I did not consult a registry"; the second, better-evidenced, records `SAME`; the first is
byte-unchanged and still verifies.

## 4. Not having looked ≠ having looked and found nothing

With no registry supplied, every decision is `UNRESOLVED / REGISTRY_NOT_CONSULTED`. With a registry
supplied but no match, it is `UNRESOLVED / NO_REGISTERED_MATCH`. Collapsing these two would make
the absence of a match unreadable — you could not tell an unsearched registry from an exhausted
one. This distinction is what makes negative-space reasoning possible later (Phase 7), where
`MISSING ≠ FALSE` and `UNKNOWN ≠ ABSENT` depend on knowing the observation scope.

## 5. New fail-closed guard

Manufacture now **stops** when a candidate's declared external identity contradicts what is already
registered under the same address (`EXTERNAL_IDENTIFIER_CONTRADICTION`). There is no winner to pick
between two durable third-party identifiers, and choosing one would fabricate identity certainty.

The complementary case — the *same* durable evidence under a *different* address — is recorded as
`EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH` and explicitly **not acted on**. It is a merge candidate, and
merging is a governed append-preserving operation (Phase 4), never a manufacture-time side effect.
The record names the identity a future merge would have to consider.

## 6. What the verifier can and cannot prove

Honest boundary, stated because it would be easy to overclaim.

**Verified from package bytes alone:**

- exactly one decision per resolved identity, none missing, none duplicated;
- each decision's reference, resolution key, candidate refs and source refs reconstruct from the
  independently reconstructed candidate material;
- reason codes are drawn from the closed vocabulary;
- a `SAME` decision binds the uid that its own key derives — so a package cannot claim to have
  reused some *other* registered identity;
- an `UNRESOLVED` or `DIFFERENT` decision binds no uid at all.

**Not verified, and not claimed:** whether the registry genuinely held a match at manufacture time.
That depends on registry state deliberately not copied into the package. An auditor holding the
registry can re-derive the decision from the recorded key and external identifiers.

This is why identity decisions are excluded from the *strict* reconstruction comparison in
`verifySemanticProjections` and checked for internal consistency instead. The exclusion is narrow
and explicit; everything else in the resolution stage must still reconstruct exactly.

## 7. Tests

| Property | Test |
|---|---|
| absence of registry is recorded as such | `withoutARegistryTheFoundryRecordsThatItDidNotLook…` |
| evidenced reuse | `consultingARegistryThatHoldsTheIdentityRecordsAnEvidencedReuseDecision` |
| evidenced absence | `consultingARegistryWithoutTheIdentityRecordsAnEvidencedAbsence` |
| merge candidate surfaced, not acted on | `aMergeCandidateIsSurfacedInThePackageAndNeverActedOn` |
| contradiction fails closed | `contradictingRegisteredExternalIdentityStopsManufacture` |
| history never rewritten | `earlierDeterminationsAreNeverRewrittenByLaterOnes` |
| full coverage of resolved identities | `everyResolvedIdentityCarriesExactlyOneDecision` |
| forged reuse claim rejected | `aDecisionClaimingReuseOfSomeOtherIdentityFailsVerification` |
| decision detached from evidence rejected | `aDecisionDetachedFromItsEvidenceFailsVerification` |
| provenance cannot be dropped | `removingTheDecisionRecordFailsVerification` |
