# ADR-0003 — Adopt USI as the Canonical Target Identity Terminology

**Status:** Accepted for successor product/application development  
**Date:** 2026-08-21  
**Supersedes:** ADR-0001 for forward-facing terminology  
**Authority created here:** terminology only; no new ASA semantic authority

## Context

ADR-0001 intentionally retained **UAO — Universal ASA Object** during the prototype period while leaving **USI — Universal Semantic Identity** as a future candidate. The Persistent Identity Manufacturing Alpha has since clarified the actual invariant being manufactured and reused: a durable semantic identity that persists across descriptions, aliases, models, sessions, observations and state changes.

The identity substrate is not itself an adaptive runtime object. Adaptation, significance, allocation and reasoning remain outside canonical identity state.

The programme owner has now resolved the deferred naming question in favour of **USI — Universal Semantic Identity**.

## Decision

1. **USI — Universal Semantic Identity** is the canonical target term for the persistent identity substrate and for new user-facing product/application development.
2. **UAO** becomes legacy/transitional terminology for the existing audited prototype implementation and historical governance material.
3. Existing `uao-*` identifiers, Java package names, repository history, schemas and immutable packages MUST NOT be destructively rewritten merely to achieve cosmetic terminology consistency.
4. New application/UI surfaces SHOULD use `USI`, `USI Foundry`, `USI Registry`, `USI Identity` and equivalent terminology where they refer to the persistent semantic identity abstraction.
5. Backward compatibility MUST preserve existing UAO packages and references during migration.
6. A later governed migration may introduce a new identifier namespace such as `usi-*`; this ADR does **not** authorize that identifier-format change by itself.
7. USI identifies the durable semantic referent. It MUST NOT canonically store significance, importance, priority, allocation state, reasoning tier or other transient ASA runtime projections.
8. URO/relationship authority remains governed separately. ASA#29 is unchanged.

## Rationale

The change is justified by both compression and architectural truthfulness:

- **USI** is a shorter and cleaner acronym.
- **Semantic Identity** describes the actual invariant more precisely than **ASA Object**.
- The word **Object** risks conflating identity with state, representation, behaviour and runtime adaptation.
- The identity layer increasingly serves as the stable addressing substrate beneath relationships, context and significance.

The intended conceptual stack is:

```text
EXISTENCE
   ↓
USI — persistent semantic identity
   ↓
RELATIONSHIPS / URO
   ↓
CONTEXTUAL PROJECTION
   ↓
SIGNIFICANCE
   ↓
PLAN
   ↓
SCHEDULE
```

## Migration rule

This is a controlled migration, not a blind search-and-replace.

Forward-facing terminology changes first. Core identifiers and immutable package formats change only under explicit compatibility and migration specifications.

## Consequence

The existing `uao-foundry` repository may continue to host the migration implementation during development, but the successor application is developed and presented as a **USI manufacturing application**. Historical UAO terminology remains inspectable as provenance rather than being rewritten out of history.
