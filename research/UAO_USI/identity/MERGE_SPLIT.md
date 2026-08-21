# Merge, Split, Supersession and Retirement

**Status:** IMPLEMENTED — FOUNDRY-OWNED
**Phase:** 4
**Contract:** `schemas/identity-operation.schema.json`
**Store:** `<registry>/identity-operations/<operationId>.json`

## 1. Why these cannot be UAO fields

`uid = sha256(resolution_key)[0..12]`. Two uids therefore **can never become one** by rewriting
anything — and packages are immutable and content-addressed, so nothing can be rewritten anyway.

Merge and split can only exist as a **mapping layer above** the derivation. That is what an
identity operation is.

`SUPERSEDE` and `RETIRE` differ in kind: ASA already governs both, through `lifecycle_status`
(`Registered | Superseded | Retired`) and `successor_identity_ref`. The journal records the
governed decision; it does not amend ASA and does not rewrite any package manufactured earlier.
A future manufacture may carry the ASA-canonical form; that is not yet implemented.

## 2. Where the journal lives, and why not elsewhere

| Candidate location | Why rejected |
|---|---|
| inside a UAO | closed ASA schema; and uid derivation makes merge unrepresentable |
| inside a package | a package is manufactured from provider evidence; an operation is a governed decision about already-registered identities |
| in `index.json` | the index is fully derived and rebuilt on every read; authored content there would break rebuild-and-compare |
| **a second immutable content-addressed store** | ✔ keeps the derived-index invariant intact |

`operationId = idop-<16 hex>` over the meaning-bearing projection, and the file is named by it. An
edited record no longer matches its own address and registry verification fails — proven by
`anEditedOperationRecordBreaksItsContentAddress`.

## 3. Operation shapes

| Operation | Subjects | Targets | Resulting subject state |
|---|---|---|---|
| `SUPERSEDE` | exactly 1 | exactly 1 | `SUPERSEDED` |
| `RETIRE` | exactly 1 | **0** | `RETIRED` |
| `MERGE` | ≥ 2 (all participants) | exactly 1 (the survivor) | `MERGED`, except the survivor |
| `SPLIT` | exactly 1 | ≥ 2 | `SPLIT` |

Degenerate shapes are refused at construction: merging one identity, splitting into one, retiring
with a successor (that is a supersession under the wrong name), and superseding something by
itself.

A `MERGE` names **every** participant as a subject, survivor included, because the record is about
all of them. The survivor keeps `ACTIVE`; it was not merged away. Getting this wrong initially made
the lifecycle derivation read every merge as a cycle — the adversarial tests caught it.

`justification` and at least one `reasonCode` are **mandatory**. An identity operation without a
stated reason is indistinguishable from a mistake once its author has moved on. `recordedAt` is
explicit rather than defaulted to the wall clock, so operations stay reproducible.

## 4. Resolution is not redirected

**This is the central decision.** After `SUPERSEDE A → B`, asking for `A` does **not** yield `B`.
It yields:

```
UNRESOLVED / IDENTITY_SUPERSEDED, candidateUids: [A, B]
```

Silent redirection would change what a later manufacture produces without anyone requesting the
change — the destructive rewrite §10 forbids, arriving by the back door. The caller is told what
happened and which identities resulted, and decides for itself.

| After | Resolving the subject yields | Offers |
|---|---|---|
| `SUPERSEDE A → B` | `IDENTITY_SUPERSEDED` | `[A, B]` |
| `RETIRE A` | `IDENTITY_RETIRED` | `[A]` — nothing in its place, by definition |
| `MERGE A,B → B` | `IDENTITY_MERGED` (for A) | `[A, B]` |
| `SPLIT A → B,C` | `IDENTITY_SPLIT` | `[A, B, C]` — and picks none |

This mirrors the existing sticky `MULTIPLE_UNRECONCILED_VARIANTS` behaviour exactly: an identity
whose status is in question stops being automatically reusable, while its neighbours carry on.

## 5. Reuse cannot undo a lifecycle decision

`ReuseAnalyzer` refuses automatic reuse of any identity whose `lifecycleState` is not `ACTIVE`
(`IDENTITY_LIFECYCLE_NOT_ACTIVE`), and manufacture-time decisions record the refusal. Without this,
a retirement would be quietly undone by whichever manufacture next happened to propose the same
resolution key. Tested by `aRetiredIdentityIsNotSilentlyReusedByTheNextManufacture`.

## 6. Non-destructiveness, demonstrated

| Requirement (§10) | Test |
|---|---|
| no silent destructive rewrite | `aMergeLeavesEveryPackageByteIdenticalAndEveryHistoryReadable` |
| previous determinations remain inspectable | same test — `decisionHistory` and `occurrences` unchanged |
| provenance records why | `anOperationWithoutAStatedReasonCannotBeConstructed` |
| previous references remain resolvable | `aMergedReferenceStillResolvesFarEnoughToLearnItsFate` |
| results surfaced, never chosen | `aSplitNamesEveryResultAndChoosesNoneOfThem` |
| unrelated identities unaffected | `aLifecycleOperationDoesNotDisturbUnrelatedIdentities` |
| packages still verify afterwards | `manufacturedPackagesStillVerifyAfterTheirIdentityIsRetired` |

## 7. Contradiction and cycles fail closed

An identity may be the subject of **at most one** terminal operation. A second would leave the
registry holding two contradictory accounts of one identity's fate with no rule for choosing, so
the index build fails rather than picking one. Chains remain expressible — `A → B` then `B → C`
names `B` as a subject only once — and are tested.

Cycles are refused: a cycle is a history that cannot have happened.

Both refusals are transactional. The operation file is removed and the registry is left
byte-identical, verified by tree hash in
`twoContradictoryOperationsOnOneIdentityAreRefusedAndLeaveNothingBehind` and `aCycleOfSupersessionsIsRefused`.

## 8. Not done

- **No automatic semantic reconciliation.** Recording a merge does not union, rank, choose or
  discard assertions, and does not clear `MULTIPLE_UNRECONCILED_VARIANTS`. §10 forbids it without
  governance, and no reconciliation authority exists.
- **No ASA-canonical emission.** A superseded identity's next manufacture does not yet emit
  `lifecycle_status: Superseded` with `successor_identity_ref`. The ASA representation exists and
  is unused; wiring it is a manufacture-side change, not a journal change.
- **Relationship effects** are not yet surfaced, because canonical UROs are always empty under
  ASA#29 and relationship candidates are not yet bound to persistent uids. That binding is Phase 5,
  after which affected candidates can be reported by an operation.
- **State migration** is explicit only in the sense that nothing migrates: no state is copied from
  a merged identity to its survivor. Copying would be manufacture, not bookkeeping.
