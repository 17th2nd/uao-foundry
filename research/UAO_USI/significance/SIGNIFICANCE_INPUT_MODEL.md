# Significance Input Model — Sourcing Contract Candidate

**Status:** CANDIDATE INTERFACE
**Phase:** 6
**Companion:** `SIGNIFICANCE_INTERFACE.md`

An implementation-facing classification of every input to
`(A_x, R_x, C_q, e) ⟶ 𝓡_v`, with where each comes from and whether the Foundry supplies it today.

## UAO/USI-owned — `A_x`

| Input | Supplied | Where from |
|---|---|---|
| identity | ✔ | `foundry_identity` + uid |
| durable attributes | ✔ | canonical `assertions` (`DEFERRED_ON_RECORD`) |
| state / version | ✔ | `lifecycleState`, `stateVersions` |
| validity declarations | ✖ | **not modelled** — see below |
| source / provenance references | ✔ | `occurrences`, `identityDecisionHistory` |

**Validity is the gap.** ASA lifecycle gives `Registered | Superseded | Retired`, which is a
*status*, not a validity interval. Nothing records "this identity was valid from T1 to T2". The
same absence appears in `../identity/ALIASES.md §4` for alias time-awareness — one missing time
model, surfacing twice. Inventing one here would be unearned machinery; it is recorded as open.

## URO-owned — `R_x`

| Input | Supplied | Status |
|---|---|---|
| relationship type | ✖ | recorded as `typeVersion` on unpublished candidates only |
| participant roles | ✖ | recorded verbatim, validated against nothing |
| relationship state | ✖ | blocked — no canonical URO exists |
| relationship provenance | ✖ | `sourceRefs` on unpublished candidates only |
| relationship validity | ✖ | blocked |

**All of `R_x` is blocked on ASA#29.** Not partially — entirely.

## Runtime-owned — not supplied, by design

`q` (objective) · `C_q` (context, observer, perspective, environment, resource conditions) ·
`e` (epoch / current time)

The Foundry has no access to any of these and must not acquire it. An objective is a property of
whoever is asking, not of the object.

## Significance-engine-owned — never computed here

`𝓡_v` reason closure · `Project_v` · `⟨G, C↑, C↓, U, E⁺, E⁻, X, V⟩` · `Plan` · `Schedule`

## Boundary collapses to avoid

The failure mode is not one big mistake; it is four small plausible ones:

1. **Storing a result.** Adding `attention_weight` to a UAO because a runtime computed one. Blocked
   by `SignificanceBoundary` at manufacture and at verification.
2. **Smuggling an objective in.** Letting a manufacture request carry `q` "for context", making
   identity state objective-dependent. Not implemented; there is no path for it.
3. **Reading a digest as a ranking.** `identity_digest` and `state_version` are content addresses
   over disjoint projections. Asserted non-significance by test.
4. **Reading `[]` as "no relationships exist".** The most dangerous, because it is silent. `R_x`
   carries `complete: false`, `authorityStatus`, `blockedBy` and a plain-language `consequence`
   precisely so an empty set cannot be mistaken for an observed absence. This is the same
   `MISSING ≠ FALSE` distinction that `../relationships/NEGATIVE_SPACE_BINDING.md` formalises.
