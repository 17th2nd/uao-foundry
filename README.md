# UAO Foundry

**Status:** Experimental foundation (`v0.1`)

UAO Foundry is the domain-independent manufacturing pipeline for **Universal ASA Objects (UAOs)**. It accepts an arbitrary identity seed, preserves ambiguity and scope explicitly, and is being built to manufacture governed, provenance-bearing, reusable semantic knowledge packages.

The current repository state is intentionally a **foundation**, not a completed knowledge manufacturer. It proves the generic executable boundary, canonical JSON contract layer, lifecycle command surface, fail-closed publication posture, and cross-domain invariants before domain knowledge is introduced.

## Core rule

> Build the manufacturing machine first; never build an identity-specific generator and generalise it later.

No production code may contain knowledge or branching specific to `cow`, `hydrogen`, `granite`, a qualification, or any other example identity. Cross-domain terms may appear in tests solely to prove that the same executable accepts unrelated identity seeds.

## Architecture

```text
arbitrary identity seed
        |
        v
interpretation / ambiguity
        |
        v
semantic scope
        |
        v
manufacturing plan
        |
        v
evidence acquisition
        |
        v
candidate knowledge
        |
        v
canonical UAO (JSON)
        |
        v
verification
        |
        v
published package / registry
```

The full manufacturing stages are architectural targets. `v0.1` currently implements request intake and lifecycle routing only; it does **not** claim to manufacture or publish canonical knowledge yet.

## Authority model

1. JSON and JSON Schema are the canonical representation and contract surface.
2. Java 21 implements those contracts; Java classes are not semantic authority.
3. Publication fails closed until verification requirements are satisfied.
4. Evidence, provenance, unresolved items, and coverage remain first-class package data.
5. Relationships must remain compatible with n-ary role bindings rather than being reduced to binary edges.
6. Reuse comes before reinvention: later manufacturing should discover reusable governed knowledge and manufacture only the semantic delta.

## Build

Requirements:

- Java 21
- Maven 3.9+

```bash
mvn -B -ntp clean verify
mvn -B -ntp package
java -jar target/uao-foundry-0.1.0-SNAPSHOT.jar manufacture --identity "cow"
```

Expected foundation output declares `REQUEST_ACCEPTED` and `NOT_PUBLISHED`. That is deliberate: acceptance of an identity seed is not equivalent to manufacturing a verified UAO.

## CLI surface

```text
uao-foundry manufacture --identity <seed> [--language <tag>] [--profile <name>]
uao-foundry interpret
uao-foundry status
uao-foundry resume
uao-foundry verify
uao-foundry inspect
```

The lifecycle commands other than `manufacture` are routed but explicitly report `FOUNDATION_ONLY` until their stages are implemented.

## Repository layout

```text
schemas/        canonical JSON contract surface
config/         implementation-neutral runtime defaults
docs/           architecture, decisions, roadmap
src/main/java/  Java 21 execution layer
src/test/java/  cross-domain and fail-closed tests
.github/         continuous verification
```

## Terminology

During prototype and demonstration development, the canonical term remains **UAO — Universal ASA Object**. A future clean migration for university/research/partnership use will formally reassess whether the mature architecture warrants the name **USI — Universal Semantic Identity**. USI is not adopted merely as a cosmetic rename.

See [`docs/ADR-0001-UAO-TERMINOLOGY.md`](docs/ADR-0001-UAO-TERMINOLOGY.md).
