# ADR-0001 — Retain UAO During Prototype; Review USI at Clean Migration

**Status:** Superseded by ADR-0003 for forward-facing terminology  
**Original status:** Accepted  
**Date:** 2026-08-09

## Context

UAO originally means **Universal ASA Object**. As the architecture evolved toward portable, reusable semantic identity, **USI — Universal Semantic Identity** emerged as a potentially more accurate mature name.

Renaming during active prototype, demonstration, Foundry and TAFE work would create unnecessary terminology churn before the architecture has proven that semantic identity is the correct stable abstraction.

## Original Decision

1. Retain **UAO** as the canonical prototype term.
2. Do not perform a cosmetic UAO-to-USI rename in the current repository.
3. Continue designing boundaries so that a later migration is technically possible without encoding `UAO` assumptions into domain logic.
4. At a future clean repository migration intended for university, research and external partnerships, formally reassess whether the proven architecture warrants:
   - USI Record
   - USI Foundry
   - USI Domain
   - USI Registry
5. Adopt USI only if it describes the demonstrated architecture more accurately than UAO.

## Supersession

The deferred naming question was resolved on 2026-08-21 by **ADR-0003 — Adopt USI as the Canonical Target Identity Terminology**.

UAO remains historical/transitional terminology for the audited prototype and existing immutable artefacts. New user-facing development targets USI terminology through a controlled compatibility-preserving migration.
