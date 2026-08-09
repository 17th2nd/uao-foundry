# UAO Foundry

**Release target:** `v0.1.0`  
**Status:** functional deterministic manufacturing core with live provider protocol, immutable verified-package registry, semantic-delta reuse, and a bounded Claude Code research adapter. Arbitrary-domain URO publication remains fail-closed pending current ASA Relationship Type role authority.

UAO Foundry is the domain-independent Java 21 manufacturing system for **Universal ASA Objects (UAOs)**. It transforms a minimally specified identity expression into a governed JSON package through explicit interpretation, scope, planning, evidence, candidate, resolution, canonicalisation, completeness, verification and publication stages.

The Foundry does **not** contain knowledge about demonstration identities. Identity-specific material belongs in provider evidence, test fixtures, registries and manufactured outputs.

## Governing rule

> Build the manufacturing machine first. An input expression is a seed, not automatically a canonical identity.

The authority boundary is:

```text
ASA authority / UAO-URO doctrine
        ↓
Foundry portable JSON contracts
        ↓
provider evidence / proposals
        ↓
Java 21 Foundry implementation
        ↓
validated canonical UAO package
        ↓
verified immutable registry
```

Provider output is intermediate. Providers do not acquire canonical-write or publication authority.

The checked-in `schemas/asa/*` files are **validation projections**, not independent ASA authority. Their upstream evidence lock is in [`config/upstream-authority-lock.json`](config/upstream-authority-lock.json).

## Sixteen-stage manufacturing pipeline

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

## Implemented v0.1 surfaces

### Deterministic core

- Java 21 / Maven executable;
- fail-closed JSON Schema contract validation;
- deterministic request, job, UAO and package identifiers;
- explicit ambiguity/scope records;
- evidence snapshots and provenance hashes;
- candidate quarantine without silent repair;
- canonical UAO construction aligned to the reviewed ASA surface;
- verification, publication decision, manifest and SHA-256 inventory;
- checkpoint/status/resume/verify/inspect lifecycle;
- byte-determinism and tamper negative controls.

### Provider protocol

A vendor-neutral process protocol allows external research/acquisition systems to produce schema-constrained **intermediate provider bundles**. Provider responses are captured as `provider-snapshot.json`, checksum-covered, and replayed from the snapshot on resume rather than re-calling the provider.

See [`docs/PROVIDER-PROTOCOL.md`](docs/PROVIDER-PROTOCOL.md).

### Verified registry

Publication-eligible packages can be copied into a Foundry-owned immutable registry. The registry re-verifies packages, preserves every package occurrence and maintains a deterministic rebuildable index over stable UAO UID, resolution key, labels and aliases.

The registry is a discovery/reuse surface, **not** an alternative ASA semantic authority.

See [`docs/REGISTRY.md`](docs/REGISTRY.md).

### Semantic delta

Registry-aware live manufacture verifies the registry **before** provider acquisition, supplies bounded discovery context, binds registry state into the provider transaction, then computes reuse inside the Foundry from stable UAO identities.

A checksum-covered `reuse-report.json` distinguishes:

- previously registered UAO identities reused by the new package;
- genuinely new UAO identities;
- exact `registry://` source evidence resolved from prior immutable packages;
- newly acquired sources.

The model/provider cannot simply declare canonical reuse. Registry evidence bytes are SHA-256 checked against the prior immutable package.

See [`docs/SEMANTIC-DELTA.md`](docs/SEMANTIC-DELTA.md).

### Claude Code adapter

[`adapters/claude-code/`](adapters/claude-code/) supplies a bounded live adapter for an installed Claude Code CLI. Claude is used as a research/evidence provider; Java Foundry still owns validation, identity resolution, canonicalisation, verification and publication.

The adapter:

- uses non-interactive structured output;
- restricts the model-facing tool surface to web research;
- disables manufacturing-session persistence;
- applies stable semantic resolution-key discipline;
- restores exact immutable registry evidence bytes after the model response;
- rejects non-empty relationship candidates while the upstream Relationship Type role authority is unavailable.

See [`adapters/claude-code/README.md`](adapters/claude-code/README.md).

## Build

Requirements for the deterministic core:

- Java 21;
- Maven 3.9+.

```bash
mvn -B -ntp clean verify
mvn -B -ntp package
```

Executable JAR:

```text
target/uao-foundry-0.1.0.jar
```

## Deterministic fixture manufacture

Fixture mode requires no network service:

```bash
java -jar target/uao-foundry-0.1.0.jar manufacture cow \
  --fixture src/test/resources/fixtures/biological-cow.json
```

The repository carries unrelated biological, physical/material and manufactured/cultural **test fixtures only**. CI requires the same compiled JAR to manufacture all of them without example-specific production logic.

## Live Claude manufacture

Requirements in addition to the deterministic core:

- Python 3.11+;
- Claude Code installed and authenticated.

Run preflight:

```bash
bash scripts/preflight-live.sh
```

Then manufacture through the verified registry/semantic-delta path:

```bash
bash scripts/manufacture-claude.sh \
  "an identity seed" \
  --registry .uao-registry \
  --register
```

`--register` is optional. Registry admission occurs only after package verification and an eligible publication decision.

No Claude credential or API token belongs in this repository.

## Lifecycle CLI

Core:

```text
java -jar target/uao-foundry-0.1.0.jar manufacture ...
java -jar target/uao-foundry-0.1.0.jar validate-request <request.json>
java -jar target/uao-foundry-0.1.0.jar interpret ...
java -jar target/uao-foundry-0.1.0.jar status <job-id>
java -jar target/uao-foundry-0.1.0.jar resume <job-id>
java -jar target/uao-foundry-0.1.0.jar verify <package-path>
java -jar target/uao-foundry-0.1.0.jar inspect <package-path>
```

Registry:

```text
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication register <package>
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication search <query>
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication verify
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication rebuild
```

Registry-aware live manufacture:

```text
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.reuse.RegistryManufactureApplication ...
```

## ASA alignment

The Foundry validation projection is currently locked to the reviewed ASA authority surface recorded in [`config/upstream-authority-lock.json`](config/upstream-authority-lock.json), including ASA `main` commit:

```text
908c5255fb3144c2a2e3f48c993d031e347d1695
```

Key consequences:

- UAO IDs conform to the current `uao-<12 lowercase hex>` CSS shape;
- UAOs hold **relationship references**, not embedded relationship meaning;
- UROs preserve role-bound, potentially n-ary participation;
- `score`, `significance_value`, `belief`, and `stance` are rejected from canonical structures;
- `epistemic_class` remains explicitly `DEFERRED_ON_RECORD`; the Foundry does not mint the deferred vocabulary;
- publication fails closed.

See [`docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md`](docs/ADR-0002-ASA-AUTHORITY-ALIGNMENT.md).

## Bounded upstream dependency: Relationship Type roles

Current ASA authority defines the URO structural boundary and requires participant roles to validate against governed Relationship Type versions, but the reviewed current machine-readable ASA source does not provide the general arbitrary-domain Relationship Type registry required to perform that validation.

The dependency is tracked in:

- `17th2nd/uao-foundry#3`;
- `17th2nd/ASA#29`.

Until upstream authority exists, the Foundry does **not** invent one. A non-empty unsupported relationship candidate is preserved as unresolved evidence, excluded from canonical URO publication, and forces an incomplete publication status. The Claude adapter rejects such candidates even earlier.

This limitation does not block UAO identity/evidence manufacture, package verification, registry admission or semantic-delta reuse when no unsupported relationship candidate is required.

See [`docs/UPSTREAM-DEPENDENCIES.md`](docs/UPSTREAM-DEPENDENCIES.md).

## Package output

A manufactured package includes, as applicable:

```text
manifest.json
manufacturing-job.json
manufacturing-request.json
provider-snapshot.json
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
reuse-report.json       # registry-aware manufacture
checksums.sha256
```

Package names are derived from the **resolved working identity**, not blindly from the submitted seed.

## Publication semantics

`EXPERIMENTAL` means the selected v0.1 structural, evidence, completeness and packaging gates passed under the selected provider/profile. It does **not** mean that:

- all knowledge about the identity has been captured;
- external source content is independently proven true;
- ASA governance has ratified the manufactured knowledge;
- a provider/model has semantic authority.

## Demonstration

A non-authoritative cumulative-manufacturing walkthrough for the TAFE discussion is under [`examples/tafe/`](examples/tafe/). Demonstration vocabulary must not enter generic production logic.

## Terminology

The prototype continues to use **UAO — Universal ASA Object**. [`docs/ADR-0001-UAO-TERMINOLOGY.md`](docs/ADR-0001-UAO-TERMINOLOGY.md) preserves the later review of **USI — Universal Semantic Identity** for a clean university/research/partnership migration. No cosmetic rename is performed in this repository.
