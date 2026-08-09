# UAO Foundry Semantic Delta v0.1

**Status:** Experimental Foundry execution capability  
**Authority:** Foundry-owned reuse accounting over verified registry packages; ASA CSS remains semantic authority

## Purpose

Semantic-delta manufacture makes prior verified UAO work visible to the next manufacturing transaction before new acquisition begins.

```text
request -> verify registry -> deterministic registry context -> provider
        -> normal 16-stage Foundry -> stable UAO comparison
        -> reuse-report.json -> checksummed package -> optional register
```

The registry is verified **before** the external provider is invoked. If a registered package has been altered, manufacture stops before acquisition.

## Provider context

`RegistryManufactureApplication` supplies the ordinary provider request plus `registryContext` containing deterministic matches and a bounded identity catalog. It also sets:

```text
UAO_FOUNDRY_REGISTRY_ROOT=/absolute/path/to/verified/registry
```

and the constraint:

```text
REUSE_VERIFIED_REGISTRY_IDENTITIES_BEFORE_NEW_ACQUISITION
```

The provider remains an intermediate proposer. It cannot declare canonical reuse by itself.

The canonical SHA-256 of the registry context is injected into the validated provider snapshot. Therefore otherwise identical provider output executed against different registry states produces a different deterministic job identity.

## Foundry-computed reuse

After canonical build, the Foundry compares each resulting stable UAO UID against the **pre-manufacture** registry index.

- `reusedUaos`: the same stable UID existed before the transaction; prior immutable package occurrences are preserved.
- `newUaos`: the stable UID was absent before the transaction.

No confidence, significance or model score decides this classification.

## Evidence reuse

A provider can identify prior evidence with:

```text
registry://<package-id>/<relative-path>
```

The prefix alone is not trusted. The Foundry resolves the locator inside the already-verified immutable package, rejects path escape/unknown packages, hashes the prior file and requires that SHA-256 to equal the new source record's content hash. Only then is the source counted as registry evidence.

The normal source acquisition stage still snapshots the content into the new package, so reused evidence remains provenance-bearing.

## `reuse-report.json`

The schema-validated report records:

- registry context hash;
- pre-transaction registry index hash;
- reused UAOs and prior occurrences;
- new UAOs;
- verified registry evidence sources;
- newly acquired sources;
- deterministic counts.

The report is inserted into `manifest.json`, covered by `checksums.sha256`, and the complete package is re-verified before success.

## CLI

```bash
mvn -B -ntp clean package

java -cp target/uao-foundry-0.1.0-SNAPSHOT.jar \
  org.seventeenthsecond.uaofoundry.reuse.RegistryManufactureApplication \
  "requested identity" \
  --provider-command /absolute/path/to/provider-adapter \
  --registry .uao-registry \
  --register
```

Controls include `--provider-timeout-seconds`, `--catalog-limit`, `--work-dir`, `--dist-dir`, `--language`, `--profile`, and `--repository-commit`.

`--register` is explicit. When used, the reuse-augmented package must still pass ordinary registry admission before becoming available to later manufacture.

## What v0.1 proves

1. prior verified UAOs are discoverable before acquisition;
2. registry state participates in transaction identity;
3. stable UAO IDs determine reused versus new identities after canonicalisation;
4. a claimed registry source is cryptographically checked against prior immutable evidence;
5. the reuse report is package evidence, not a console-only claim;
6. successful output can re-enter the registry for cumulative manufacture.

The pipeline intentionally still validates provider candidates even when a stable identity exists. Reuse does not bypass validation or provenance. Future performance optimisation may avoid unnecessary candidate reconstruction only if these guarantees remain intact.

## TAFE / ALA significance

This is the auditable mechanism behind the demonstration that the Foundry need not conceptually start from zero. Once verified electrotechnology identities exist, a later EV-maintenance provider can inspect the registry, reuse relevant stable identities/evidence where justified, and add only the genuinely new semantic material. The resulting package states exactly what was reused and what was new.

The Foundry never forces overlap merely because two topics sound related.

## URO boundary

Semantic-delta support does not change the fail-closed URO policy. Missing current Relationship Type role authority remains the bounded upstream dependency tracked in Issue #3.
