# UAO Foundry

**Release target:** `v0.1.0`  
**Status:** functional deterministic manufacturing core; fixture-mode cross-domain proof complete; live provider not configured; non-empty URO publication fails closed pending current ASA Relationship Type role authority.

UAO Foundry is the domain-independent Java 21 manufacturing system for **Universal ASA Objects (UAOs)**. It transforms a minimally specified identity expression into a governed JSON package through explicit interpretation, scope, planning, evidence, candidate, resolution, canonicalisation, completeness, verification and publication stages.

The Foundry does **not** contain knowledge about demonstration identities. Identity-specific material lives in provider data, fixtures and manufactured outputs.

## Governing rule

> Build the manufacturing machine first. An input word is a seed, not automatically a canonical identity.

The implementation follows this authority order:

```text
ASA authority / UAO-URO doctrine
        ↓
Foundry JSON contracts
        ↓
Java 21 implementation
        ↓
Manufactured JSON records and package
```

The checked-in ASA schemas are **validation projections**, not independent authority. Their upstream evidence lock is in [`config/upstream-authority-lock.json`](config/upstream-authority-lock.json).

## Sixteen-stage v0.1 pipeline

```text
 1  Job initialisation
 2  Seed normalisation
 3  Identity interpretation
 4  Scope resolution
 5  Manufacturing planning
 6  Source strategy
 7  Source acquisition
 8  Knowledge extraction
 9  Candidate validation / quarantine
10  Identity resolution
11  Relationship construction
12  Canonical build
13  Completeness analysis
14  Verification
15  Publication decision
16  Package manufacture + checksums
```

Every stage writes an inspectable checkpoint artifact. Resume validates checkpoint hashes and reuses completed stages rather than silently restarting the job.

## Build

Requirements:

- Java 21
- Maven 3.9+

```bash
mvn -B -ntp clean verify
mvn -B -ntp package
```

The executable is:

```text
target/uao-foundry-0.1.0.jar
```

## Manufacture

Fixture mode is deterministic and requires no network service:

```bash
java -jar target/uao-foundry-0.1.0.jar manufacture cow \
  --fixture src/test/resources/fixtures/biological-cow.json
```

A full request document is also accepted:

```bash
java -jar target/uao-foundry-0.1.0.jar manufacture \
  --request request.json \
  --fixture fixture.json
```

Only `identitySeed` is required by the request contract. Missing provider capability fails closed: v0.1 does not silently hallucinate an interpretation or sources when no provider is configured.

## Lifecycle CLI

```text
uao-foundry manufacture <identity-seed> --fixture <bundle.json>
uao-foundry validate-request <request.json>
uao-foundry interpret <identity-seed> [--fixture <bundle.json>]
uao-foundry status <job-id>
uao-foundry resume <job-id>
uao-foundry verify <package-path>
uao-foundry inspect <package-path>
```

## Cross-domain proof

The repository carries three identity-specific **test fixtures only**:

- biological;
- physical/material;
- manufactured/cultural.

The CI gate requires the same compiled JAR to manufacture all three through the same contracts and stages, with meaningfully different generated plans and canonical identity structures. Production Java is separately scanned to ensure demonstration identity terms have not leaked into the core.

The correct claim is not that one example proves universality. The proof is that structurally different identities complete the same compiled manufacturing system with no source-code change.

## ASA alignment

Current Foundry validation is aligned to the reviewed ASA authority surface at commit:

```text
908c5255fb3144c2a2e3f48c993d031e347d1695
```

Key consequences:

- UAO IDs conform to the current `uao-<12 lowercase hex>` CSS shape.
- UAOs hold **relationship references**, not embedded relationship meaning.
- UROs preserve role-bound, potentially n-ary participation.
- `score`, `significance_value`, `belief`, and `stance` are rejected from canonical structures.
- `epistemic_class` remains explicitly `DEFERRED_ON_RECORD`; the Foundry does not mint the deferred vocabulary.
- publication is fail-closed.

See [`docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md`](docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md).

## Bounded upstream dependency: relationship type roles

Current ASA authority defines the URO structural boundary but the Foundry review did not locate a current CSS-era authoritative registry of arbitrary domain **Relationship Type role declarations** suitable for validating generated type versions, role cardinality and participant kinds.

Therefore v0.1 does not invent one. If candidate relationships are present without that authority, they are recorded as unresolved and excluded from canonical URO publication. The package becomes `EVIDENCE_INCOMPLETE` rather than silently fabricating a relationship contract.

This does **not** block UAO manufacture when no unsupported relationship candidate is required. It is tracked in [`docs/UPSTREAM-DEPENDENCIES.md`](docs/UPSTREAM-DEPENDENCIES.md).

## Package output

A successful experimental package includes, among other records:

```text
manifest.json
manufacturing-job.json
manufacturing-request.json
identity-seed.json
interpretation-candidates.json
scope-resolution.json
manufacturing-plan.json
source-strategy.json
source-registry.json
source-corpus/
candidate-*.json
identity-resolution.json
canonical-identities.json
canonical-relationships.json
provenance-ledger.json
coverage-report.json
verification-report.json
unresolved-items.json
publication-decision.json
manufactured-package.json
checksums.sha256
```

Package names are derived from the **resolved working identity**, not blindly from the submitted seed.

## Publication semantics

`EXPERIMENTAL` means the v0.1 structural, evidence, completeness and packaging gates passed under the selected provider/profile. It does **not** mean that all knowledge about the identity has been captured, that the content is independently true, or that upstream ASA governance has ratified the manufactured knowledge.

## Terminology

The prototype continues to use **UAO — Universal ASA Object**. The existing terminology ADR preserves the later review of **USI — Universal Semantic Identity** for a clean university/research/partnership migration. No cosmetic rename is performed here.
