# UAO Foundry Registry v0.1

**Status:** Experimental Foundry infrastructure  
**Authority:** Derived index over verified manufactured packages; not ASA semantic authority

## Purpose

The registry exists to make knowledge manufacture cumulative.

A manufactured UAO package that has passed verification and has a reuse-eligible publication decision can be admitted as an immutable registry package. The registry then exposes stable UAO identifiers, resolution keys, labels, aliases and package occurrences for future discovery.

The registry does **not** decide that one occurrence is the final truth, does not assign significance, and does not replace ASA CSS authority. It detects exact semantic-variant divergence but does not attempt to decide whether differing assertion sets contradict one another.

## Storage model

```text
.uao-registry/
├── index.json
└── packages/
    ├── pkg-.../
    │   ├── manifest.json
    │   ├── checksums.sha256
    │   ├── canonical-identities.json
    │   ├── provider-snapshot.json
    │   └── ... complete manufactured package
    └── pkg-.../
```

Packages are copied as immutable evidence-bearing artifacts. The index is completely rebuildable from those packages.

## Admission gate

`register` fails closed unless:

1. the package verifier passes;
2. manifest/checksum/source/provider-snapshot integrity passes;
3. the publication decision has `eligible: true`.

If a package ID already exists with byte-identical content, registration is idempotent. If the same package ID names different content, admission is rejected. Admission also checks stable-key lexical name continuity and is transactional: a rejected identity collision leaves neither package bytes nor a changed index behind.

## Index model

The deterministic index contains:

- package ID;
- root UAO ID;
- publication status;
- immutable package tree digest;
- relative registry package path;
- each stable UAO UID;
- Foundry resolution key;
- all observed canonical labels;
- all observed aliases;
- every immutable package occurrence containing that UAO.
- a deterministic semantic-variant digest on every occurrence;
- `SINGLE_VARIANT` or `MULTIPLE_UNRECONCILED_VARIANTS` status for the stable identity.

The digest covers the stable UAO's meaning-bearing canonical projection: UID, lifecycle/successor state, Foundry identity metadata except source references, canonical assertions, relationship references and disclaimer. Assertion/reference/alias ordering is canonicalised. Package occurrence provenance, source references and source bytes are excluded so provenance-only repeats can share one variant.

Multiple verified package occurrences of the same stable UAO may coexist. Same-digest occurrences are repeat/provenance occurrences and remain eligible for automatic reuse. Different digests are all preserved and deterministically mark the identity `MULTIPLE_UNRECONCILED_VARIANTS`. The registry does not choose a newest or highest-confidence occurrence, union assertions, declare one true, or delete either package.

## CLI

Build the Foundry JAR first:

```bash
mvn -B -ntp clean package
```

Register a verified manufactured package:

```bash
java -cp target/uao-foundry-0.1.0-SNAPSHOT.jar \
  org.seventeenthsecond.uaofoundry.registry.RegistryApplication \
  register dist/UAO-EXAMPLE-v0.1.0-experimental \
  --registry .uao-registry
```

Search by UID, resolution key, exact label, alias or deterministic lexical token match:

```bash
java -cp target/uao-foundry-0.1.0-SNAPSHOT.jar \
  org.seventeenthsecond.uaofoundry.registry.RegistryApplication \
  search "example identity" \
  --registry .uao-registry
```

Other commands:

```text
RegistryApplication list
RegistryApplication verify
RegistryApplication rebuild
RegistryApplication context <query> [--catalog-limit 5000]
```

## Search semantics

The registry deliberately avoids significance or confidence ranking.

Match classes are ordered only by deterministic structural specificity:

1. `UID`
2. `RESOLUTION_KEY`
3. `LABEL`
4. `ALIAS`
5. `TOKEN`

No `score`, `belief`, `stance` or `significance_value` is produced.

Search and discovery expose each occurrence's package ID, canonical path and semantic-variant digest together with the identity-level variant status. This makes divergence inspectable without presenting it as reconciled knowledge.

## Provider discovery context

`context` exposes provider-safe discovery material:

```json
{
  "catalog": [],
  "catalogTruncated": false,
  "matches": [],
  "query": "requested identity",
  "registryVersion": "0.1.0",
  "totalIdentities": 0
}
```

The catalog contains identity/index metadata only. It does not expose API credentials or grant a provider direct write access to registered packages.

This is the basis for the next integration step: give the provider registry context before acquisition, allow it to refer to already-manufactured identities, and have the Foundry produce an explicit reuse/delta report.

## Verification

Every registry read used for search/discovery first rebuilds the expected index from verified immutable packages and compares it with stored `index.json`. `RegistryApplication verify` exposes the same check explicitly. It fails if:

- a package has been altered;
- a package checksum/source snapshot/provider snapshot fails;
- a package is no longer reuse-eligible;
- the stored index differs from the rebuildable deterministic index.

`index.json` is therefore a cache/derived view, not the authority surface, and a tampered cache is never used for discovery. The immutable verified packages are its evidence base.

Registry-aware automatic manufacture refuses a matched `MULTIPLE_UNRECONCILED_VARIANTS` identity before provider acquisition. If a newly manufactured occurrence differs from the registry's current single variant, `ReuseAnalyzer` refuses to count it as reused or register it automatically. An operator may separately admit the immutable package occurrence, after which the identity remains unresolved until a future governed reconciliation mechanism exists.

## Relationship-authority boundary

Registry admission does not cure unresolved URO type authority. Packages with publication decisions that are not reuse-eligible are rejected, and the current Foundry relationship fail-closed policy remains unchanged.

A non-canonical staging store for retained relationship candidates lives **beside** the registry root (`staged-relationships/`), never inside it. Nothing staged enters `index.json`, influences admission, or is consulted by verification; the registry index remains derived from packages and identity operations only. See `RELATIONSHIP-STAGING.md`.

See `UPSTREAM-DEPENDENCIES.md` and Issue #3.

## Next step: semantic delta

Registry v0.1 proves safe storage and discovery. Semantic-delta integration must preserve provenance rather than simply deleting sources for reused identities.

The intended next flow is:

```text
request seed
   |
registry discovery
   |
provider receives existing identity catalog
   |
provider proposes reuse + genuinely new evidence
   |
Foundry verifies prior package evidence
   |
canonical identity comparison
   |
reuse-report.json   # legacy packages only; see ADR-0006
   |-- reused existing UAOs
   |-- new UAOs
   `-- evidence/source delta
```

The registry remains Foundry-owned infrastructure throughout; ASA CSS remains the authority for ASA semantics.

## Enrichment (ADR-0007)

An identity's assertion set can grow without a new uid. `ENRICH` is a journal operation over two semantic
variants of the same uid: the newer must restate every assertion of the older verbatim and add at least one,
which the registry re-checks from package bytes on every build. The superseded variant becomes history
(`variantHistory`), the identity stays `SINGLE_VARIANT` with a `currentVariant`, and reuse compares against the
current variant. Forks and cycles fail closed. `RegistryApplication enrich <package> --subject <uid> …` admits
the enriching package and records the operation as one fail-closed step. See `ADR-0007-IDENTITY-ENRICHMENT.md`.
