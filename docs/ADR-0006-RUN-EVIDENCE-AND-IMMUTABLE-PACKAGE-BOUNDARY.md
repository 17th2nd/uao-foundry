# ADR-0006 — Run Evidence and the Immutable Package Boundary

**Status:** Accepted for the USI application programme
**Date:** 2026-08-21
**Resolves:** finding P9-1
**Authority created here:** Foundry package/run boundary only. No ASA authority.

## Context — finding P9-1

The Persistent Identity Alpha established that cumulative manufacture breaks on the **third**
observation of unchanged material.

`reuse-report.json` is written **inside** the content-addressed package but is **excluded** from
`PackageContentDigest.CORE_FILES`, which determines `packageId`. The report embeds
`registryContextHash`, `registryIndexHash` and `priorOccurrences` — all of which move as the
registry grows.

So two manufactures of semantically identical material, against a registry that has moved on,
produce **the same `packageId` with different bytes**. Both guards then fire, correctly:

```
Package output collision: existing path has different content: …
Registry package-id collision with different immutable content: pkg-b2e6ba9164927b66
```

Measured impact: **69 of 114** cumulative manufactures refused, against 8 refused by the intended
fail-closed behaviour. `PackageContentDigest.java` is byte-unchanged from `2bc2871d`, so this is
pre-existing in accepted main.

## The question the fix must answer

> Should a semantically identical identity receive a different package ID every run?

**No.** A package is a content-addressed statement of manufactured semantic material. If its
identity changed whenever the surrounding registry changed, `packageId` would address *an event*
rather than *a claim*, and byte-determinism — an audited property — would be lost.

Therefore the volatile material must move out, not into the digest.

## Decision

### 1. Two artefacts with different lifetimes

```
IMMUTABLE SEMANTIC PACKAGE           DERIVED RUN EVIDENCE
  identity                             runId
  canonical assertions                 identitySeed, provider
  evidence snapshot                    packageId (reference)
  provenance                           usiIds (references)
  verification                         registryBeforeHash / registryAfterHash
  source registry                      reuseReport
  content-addressed packageId          reused / new counts, status, timings
```

A package answers *"what was manufactured?"*. A run record answers *"what happened when we ran
it?"*. The first is intrinsic; the second is relative to a registry state at a moment.

### 2. `reuse-report.json` is no longer written into new packages

`ReuseAnalyzer.analyze` still computes it — unchanged, including every cryptographic check on
`registry://` evidence. It is stored in the run record instead of being attached to the package.

`ReuseAnalyzer.attachAndVerify` is removed from the manufacture path.

### 3. Collision protections are not weakened

Neither `PackageContentDigest.CORE_FILES` nor either collision guard is changed. The defect is
resolved by removing the volatile input, not by loosening the check that detected it. After the
change, repeated manufacture of identical material produces **byte-identical packages**, so
admission is idempotent — which is what the guard was always trying to express.

### 4. Legacy packages keep their embedded report

A package manufactured before this change carries `reuse-report.json` inside, listed in its
manifest and checksums, and remains **self-consistent and verifiable**. It is not converted.
Verification, registry admission and inspection continue to accept it. Enforced by test.

### 5. Run records are append-preserving

Content-addressed `run-<16 hex>`. Recording the same run twice is idempotent; recording a different
run under an existing id is refused. Completed runs are never edited — a correction appends a new
record referencing the original, exactly as identity operations do.

### 6. Storage location

Run records live in a store **beside** the registry, not inside `packages/`:

```
~/.usi-foundry/
├── registry/          packages/ · identity-operations/ · index.json
└── runs/              <runId>.json
```

The registry index remains derived **only** from `packages/` and `identity-operations/`. Run
records never influence it, and a test asserts this.

### 7. Timestamps are supplied, not taken from the wall clock

As with identity operations, the caller supplies `startedAt` / `completedAt`. Deterministic tests
are worth more than the convenience of an implicit clock.

## Consequences

- Cumulative manufacture works. Regression test proves **10+** repeated accumulations.
- Byte-determinism is strengthened: a package no longer varies with its surroundings.
- Reuse evidence remains inspectable, and gains a home that can hold run history the package never
  could.
- **Documentation and one CI assertion change.** `README.md`, `docs/REGISTRY.md` and
  `docs/SEMANTIC-DELTA.md` list `reuse-report.json` as package output; the `semantic-delta`
  workflow asserts `test -f "$package_path/reuse-report.json"`. That assertion is relocated to the
  run record. It is an artefact-location assertion, not an adversarial control, and its intent —
  that reuse evidence was produced and is inspectable — is preserved exactly.
