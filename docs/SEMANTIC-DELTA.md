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

After canonical build, the Foundry compares each resulting stable UAO UID and deterministic semantic-variant digest against the **pre-manufacture** registry index.

- `reusedUaos`: the same stable UID/key and same single semantic variant existed before the transaction; prior immutable package occurrences are preserved.
- `newUaos`: the stable UID was absent before the transaction.

An existing identity already marked `MULTIPLE_UNRECONCILED_VARIANTS` is refused before a matched provider acquisition. A newly encountered digest that differs from the prior single variant is not counted as reused and fails closed with `SEMANTIC_VARIANT_DIVERGENCE`. No confidence, significance, recency or model score decides this classification.

## Evidence reuse

A provider can identify prior evidence with:

```text
registry://<package-id>/<relative-path>
```

The prefix alone is not trusted. Core source acquisition requires a verified registry context for every `registry://` locator, resolves the locator inside the immutable package, checks the package tree digest, rejects path escape/unknown packages, and snapshots the exact registered bytes. `ReuseAnalyzer` independently re-resolves/hash-checks that evidence after manufacture. Plain manufacture without verified registry context rejects `registry://` evidence outright.

The normal source acquisition stage still snapshots the content into the new package, so reused evidence remains provenance-bearing.

## `reuse-report.json`

The schema-validated report records:

- registry context hash;
- pre-transaction registry index hash;
- reused UAOs and prior occurrences;
- new UAOs;
- the current semantic-variant digest for every reused/new UAO;
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
3. exact canonical `resolutionKey` continuity derives stable UAO IDs, while exact semantic-variant continuity is separately required before an existing identity is counted as automatically reused;
4. a claimed registry source is cryptographically checked against prior immutable evidence;
5. the reuse report is package evidence, not a console-only claim;
6. successful output can re-enter the registry for cumulative manufacture.

These properties prove deterministic reuse accounting under the Foundry identity/variant discipline; they do **not** prove universal semantic equivalence, infer contradiction between arbitrary assertion sets, reconcile divergent variants, or establish factual correctness of provider knowledge.

The pipeline intentionally still validates provider candidates even when a stable identity exists. Reuse does not bypass validation or provenance. Future performance optimisation may avoid unnecessary candidate reconstruction only if these guarantees remain intact.

## TAFE / ALA significance

This is the auditable mechanism behind the demonstration that the Foundry need not conceptually start from zero. Once verified electrotechnology identities exist, a later EV-maintenance provider can inspect the registry, reuse relevant stable identities/evidence where justified, and add only the genuinely new semantic material. The resulting package states exactly what was reused and what was new.

The Foundry never forces overlap merely because two topics sound related.

## URO boundary

Semantic-delta support does not change the fail-closed URO policy. Missing current Relationship Type role authority remains the bounded upstream dependency tracked in Issue #3.
