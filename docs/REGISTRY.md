# UAO Foundry Registry v0.1

**Status:** Experimental Foundry infrastructure  
**Authority:** Derived index over verified manufactured packages; not ASA semantic authority

## Purpose

The registry exists to make knowledge manufacture cumulative.

A manufactured UAO package that has passed verification and has a reuse-eligible publication decision can be admitted as an immutable registry package. The registry then exposes stable UAO identifiers, resolution keys, labels, aliases and package occurrences for future discovery.

The registry does **not** decide that one occurrence is the final truth, does not assign significance, and does not replace ASA CSS authority.

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

If a package ID already exists with byte-identical content, registration is idempotent. If the same package ID names different content, admission is rejected.

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

Multiple verified package occurrences of the same stable UAO may coexist. Registry v0.1 preserves them rather than silently choosing a winner.

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

`RegistryApplication verify` rebuilds the expected index from all registered packages and re-runs package verification. It fails if:

- a package has been altered;
- a package checksum/source snapshot/provider snapshot fails;
- a package is no longer reuse-eligible;
- the stored index differs from the rebuildable deterministic index.

`index.json` is therefore a cache/derived view, not the authority surface. The immutable verified packages are its evidence base.

## Relationship-authority boundary

Registry admission does not cure unresolved URO type authority. Packages with publication decisions that are not reuse-eligible are rejected, and the current Foundry relationship fail-closed policy remains unchanged.

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
reuse-report.json
   |-- reused existing UAOs
   |-- new UAOs
   `-- evidence/source delta
```

The registry remains Foundry-owned infrastructure throughout; ASA CSS remains the authority for ASA semantics.
