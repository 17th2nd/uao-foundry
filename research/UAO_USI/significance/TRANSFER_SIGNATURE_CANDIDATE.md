# Transfer Signature — Candidate

**Status:** RESEARCH / NON-AUTHORITATIVE
**Implemented:** No. Deliberately.
**Shape proposal:** `TRANSFER_SIGNATURE_CANDIDATE.schema.json` (in this directory, deliberately **not** in `schemas/`)
**Blocked by:** `17th2nd/ASA#29`

## 1. What this is for

Because the significance architecture depends on relationships, a future governed Relationship Type
version may need to declare how significance moves across it. §13 lists the candidate modes:

```
emits   absorbs   attests   challenges   gates   hastens   inhibits   inert
```

This document and its schema record the **seam**, so it is designed rather than improvised the day
ASA#29 closes. Nothing here is implemented: no manufacturing stage reads it, nothing validates
against it, and no Foundry behaviour depends on it.

### Why it is not in `schemas/`

`schemas/` is tree-hashed into every manufacturing job's `configurationHash`. A research artefact
placed there would alter the manufacturing configuration of every job, making an unratified
proposal part of the deterministic identity of real work. Canonical contracts live in `schemas/`;
candidates live here.

## 2. The one firm requirement

> **Providers must not invent transfer modes.**

The vocabulary is a closed `enum` in the candidate schema for exactly this reason. A model that
could define new transfer modes would be defining how significance propagates through the graph —
a governance decision wearing the costume of a data field. The same discipline already applies to
resolution keys (three namespaces, no others) and identity decision reason codes (closed enum).

## 3. Transfer is declared per role, not per relationship

The one substantive design position taken here.

A relationship does not transfer significance uniformly. `asa.core/contains@1` binds a `container`
and a `member`; whatever a container contributes toward its members is not what a member
contributes toward its container. A relationship-level mode would force the two to be equal, and
the resulting model could not express containment — arguably the most common relationship there is.

So the candidate declares `roleTransfer[]`, one entry per named role. This also aligns with
ADR-002, under which Relationship Type versions own role schemas, and it degrades correctly for
n-ary relationships, where "direction" is not even well defined.

## 4. Open questions, unanswered on purpose

- Do modes compose along a path, and if so under what algebra? Answering this would be designing
  the significance engine, which is not the Foundry's to design.
- Is `gates` a hard precondition or a multiplier? The word does not settle it.
- Does `challenges` reduce significance, or raise it by making the object contested? Both are
  defensible; the choice belongs to whoever owns `F_v`.
- What happens when two roles of one relationship declare conflicting modes?
- Does transfer depend on `C_q`? If so it is not a property of the type at all, and this entire
  seam is in the wrong place.

That last question is the one worth resolving first, because a "yes" invalidates the approach
rather than refining it.

## 5. Relationship to the supply interface

Even fully specified, transfer signatures would remain **engine-owned** semantics attached to
**URO-owned** type declarations. The Foundry's role would still be to supply the declaration as
part of `R_x` — never to apply it. See `SIGNIFICANCE_INTERFACE.md §2`.
