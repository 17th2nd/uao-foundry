# Identity Decision Space and Lifecycle

**Status:** PARTIALLY IMPLEMENTED — FOUNDRY-OWNED
**Phase:** 1 implemented the decision space; lifecycle beyond `Registered` is Phase 4.

## 1. Three-valued decisions

```
SAME        positive evidence that a reference denotes a registered identity
DIFFERENT   positive evidence of contradiction
UNRESOLVED  evidence is insufficient to decide
```

`UNRESOLVED` is a first-class outcome and never an error. Binary same/not-same forces a
determination the evidence often cannot support, and the forced answer then becomes
indistinguishable from an evidenced one.

**Absence never yields `DIFFERENT`.** Not having seen a reference before is not evidence that it
denotes a different object — `MISSING ≠ FALSE`. Tested by
`anUnknownReferenceIsUnresolvedRatherThanDifferent`.

## 2. Reason codes

Every decision carries at least one; construction rejects a decision without one.

| Code | Decision | Meaning |
|---|---|---|
| `EXACT_UID_MATCH` | SAME | direct address |
| `EXACT_RESOLUTION_KEY_MATCH` | SAME | direct address |
| `EXTERNAL_IDENTIFIER_CONTINUITY` | SAME | one registered identity carries this durable identifier |
| `EXTERNAL_IDENTIFIER_AMBIGUOUS` | UNRESOLVED | several identities carry it; no arbitrary pick |
| `EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH` | UNRESOLVED | same evidence, different address — a **merge candidate** |
| `EXTERNAL_IDENTIFIER_CONTRADICTION` | DIFFERENT | same address, contradicting durable evidence |
| `ALIAS_MATCH_INSUFFICIENT` | UNRESOLVED | a name match is a hint and never decides |
| `NO_REGISTERED_MATCH` | UNRESOLVED | nothing matched |
| `SEMANTIC_VARIANTS_UNRECONCILED` | UNRESOLVED | the target identity is unreconciled and stays sticky |

## 3. Evidence strength is structural, not numeric

| Reference kind | Can establish SAME? | Why |
|---|---|---|
| `UID` | yes | direct address |
| `RESOLUTION_KEY` | yes | direct address |
| `EXTERNAL_IDENTIFIER` | yes, if unambiguous | durable third-party evidence |
| `ALIAS` | **never** | things are routinely called the same word |

Tested by `aSharedAliasDoesNotResolveToTheSameIdentity`: two genuinely different objects both
carrying the alias "cow" resolve to `UNRESOLVED` with both candidates surfaced, not to either one.

## 4. Sticky unresolved variants

An identity carrying `MULTIPLE_UNRECONCILED_VARIANTS` resolves `UNRESOLVED` **however exact the
addressing evidence** — a direct uid match is still refused. This preserves the existing
fail-closed policy: the machine will not reuse an identity whose meaning is in dispute.

Unrelated identities are unaffected. Tested by
`unreconciledVariantsBlockResolutionWithoutAffectingUnrelatedIdentities`.

This state is **intentionally sticky**. Nothing in the Foundry clears it. It is cleared only by a
governed reconciliation authority that does not yet exist, and none of *newest wins*, *highest
confidence wins*, *model chooses*, *majority wins* or *union all assertions* is implemented or
may be.

## 5. Lifecycle states

ASA already governs three, in the closed UAO schema:

```
Registered | Superseded | Retired
```

with `Superseded` **requiring** `successor_identity_ref`. The Foundry currently emits only
`Registered`.

Richer states the programme raises — `CONFIRMED`, `LIKELY`, `AMBIGUOUS`, `SPLIT`, `MERGED` — are
**not** implemented and must not be added to `lifecycle_status`, which is ASA-governed and closed.
`LIKELY` in particular is a confidence score wearing a different hat and is rejected on the
grounds in `IDENTITY_MODEL.md §7`.

`AMBIGUOUS` is already expressed, better, as `semanticVariantStatus`. `MERGED`/`SPLIT` belong to
the Foundry-owned mapping layer described in `MERGE_SPLIT.md`, because uid is derived from the key
and two uids can never become one without a mapping.

## 6. Open

- Decisions are computed but not yet persisted as append-preserving history (Phase 2).
- `resolveCandidate` is not yet called during manufacture (Phase 2).
- Supersession and retirement are unused (Phase 4).
