# ADR-0005 — USI Identifier Strategy

**Status:** Accepted for the USI application programme
**Date:** 2026-08-21
**Decides:** whether `uao-<12 hex>` becomes `usi-<12 hex>`, and when
**Authority created here:** none. This ADR records a constraint it cannot lift.

## The decisive constraint

**The identifier prefix is pinned by ASA, not by the Foundry.**

`schemas/asa/uao.schema.json` declares:

```json
"uid": {"type": "string", "pattern": "^uao-[a-f0-9]{12}$"}
"$comment": "Non-authoritative validation projection of ASA CSS 2026.1
             primitive_definitions.UAO at ASA main 908c5255…. CSS remains sole semantic authority."
```

and ADR-0002 §2 requires the Foundry to *"Preserve the CSS `uao-<12 hex>` and `uro-<12 hex>`
identifier shapes"*, under the governing rule that *"Foundry-owned schemas must not widen, narrow or
reinterpret"* the CSS.

Emitting `usi-<12 hex>` in the canonical `uid` field would therefore be **the Foundry
reinterpreting an ASA primitive** — exactly what ADR-0002 forbids and what the programme's own
authority boundary forbids. It is not a matter of preference or effort.

**Option C is blocked upstream. Only ASA can unblock it.**

## Options assessed against the required criteria

| Criterion | A — legacy uid retained | B — dual `usiId` + `legacyUaoId` | C — clean `usi-*` |
|---|---|---|---|
| Backward compatibility | **intact** | degraded — two textual forms circulate | broken without migration |
| Deterministic identity continuity | intact | intact (pure re-prefix) | intact |
| Registry compatibility | **intact** | worse — index and search must normalise both | requires re-index |
| Package verification | **intact** | worse — verifier must accept or reject a second form | new schema version |
| External references | **intact** | **worse** — a system storing `usi-X` and one storing `uao-X` textually disagree about the same object | invalidated |
| Migration reversibility | **intact** | intact | one-way without a mapping |
| ASA authority | **compliant** | compliant (if `usiId` is never canonical) | **violates ADR-0002** |

Option B loses on the criteria it was meant to satisfy. Its `usiId` would differ from `legacyUaoId`
only by prefix over identical hex — **a second name for one thing carrying no additional
information**, while doubling the forms in circulation. Every "external references" and "registry
compatibility" consideration is made worse, not better, by minting it.

## Decision

### 1. Option A now

The canonical, wire and storage identifier remains `uao-<12 hex>`, unchanged, everywhere.

### 2. Product language without a second identifier

The application API uses the product field name `usiId`, and its **value is the canonical
identifier unchanged**, accompanied by an explicit scheme:

```json
{
  "usiId": "uao-e7582726a3c8",
  "identifierScheme": "legacy-uao",
  "canonicalLabel": "electric motor"
}
```

The field name states the *role*; the value states the *scheme*. No second identifier is minted, so
there is nothing for two systems to disagree about. `identifierScheme` is the seam: when ASA moves,
it becomes `usi` and the value changes with it.

### 3. Presentation rule

An operator sees the USI framing and the real identifier together, with the scheme named:

```
USI ID   uao-e7582726a3c8   (legacy wire identifier)
```

Never a fabricated `usi-…` string. An identifier an operator can copy must be one the system will
accept back.

### 4. The Option C mapping is defined and tested, but not emitted

`UsiIdentifiers` implements the future mapping as a pure, total, reversible function:

```
usi-<hex>  ⟷  uao-<hex>
```

Unit-tested for round-trip fidelity. Two invariants are enforced by test, because the two
directions differ in risk:

| Function | Direction | Permitted where |
|---|---|---|
| `toUsi` | **mints** a `usi-` string | **nowhere in production.** Calling it would start leaking `usi-` identifiers into artefacts without the governed migration this ADR requires. |
| `toLegacy` | translates an inbound reference | the application facade only, so a future-form identifier someone pastes is not silently unresolvable |
| `schemeOf` | labels which scheme a value is in | the application facade only, to populate `identifierScheme` |

The audited core (`org.seventeenthsecond.uaofoundry.*`) must not reference the class at all: the
migration seam belongs at the application boundary, not inside manufacture, registry or
verification. A further test walks every file of a manufactured package and asserts no `usi-`
string appears in any artefact.

Option C therefore remains a switch to throw rather than a design to invent, on the day ASA
changes the CSS shape.

### 5. Preconditions for Option C

All must hold:

1. ASA CSS changes the primitive identifier shape, and the authority lock is refreshed;
2. a registry migration with backup, SHA-256 manifest and verified restore exists (ADR-0006 §backup);
3. `identifierScheme` is versioned in the package schema;
4. the mapping in §4 is the migration's only translation point.

Until (1), this ADR is not revisited.

## Consequences

- Zero risk to existing packages, registries and external references.
- The product reads as USI immediately.
- Some operators will see a `uao-` string under a "USI ID" label. That is honest — it is the
  identifier — and is preferable to showing one the registry would reject.
- Closing ASA#29 does **not** unblock this; it is a separate CSS question and should be raised as
  its own upstream item if the identifier shape genuinely matters to the programme.
