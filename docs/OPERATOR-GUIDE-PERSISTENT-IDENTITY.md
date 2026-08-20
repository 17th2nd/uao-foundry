# Operator Guide — Persistent Identity Manufacturing Alpha

**Status:** Programme deliverable on `programme/persistent-identity-manufacturing-alpha`. Not merged.
**Base:** `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e`

## 1. Clean-room setup

The repository **cannot be built on a stock workstation as shipped**. It needs a full JDK 21 and
Maven 3.9+; a JRE is not enough (no `javac`), and neither is installed by default on the operator
machine this programme ran on.

```bash
# 1. Verify a JDK, not merely a JRE
javac -version          # must print 21.x. If "command not found", you have a JRE only.
mvn -v                  # must print 3.9+

# 2. If either is missing, install without touching system packages:
curl -fsSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
curl -fsSL -o mvn.tar.gz "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz"
tar xzf jdk.tar.gz && tar xzf mvn.tar.gz
export JAVA_HOME="$PWD/jdk-21.0.12.1+1"
export PATH="$JAVA_HOME/bin:$PWD/apache-maven-3.9.9/bin:$PATH"

# 3. Build and test
mvn -B -ntp clean verify        # expect: Tests run: 120, Failures: 0, Errors: 0
mvn -B -ntp package -DskipTests

# 4. Python adapter tests (unittest, not pytest)
python3 -m unittest discover -s adapters/claude-code/tests -p 'test_*.py'   # expect 12 OK
```

## 2. The console

```bash
FOUNDRY="java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.console.OperatorConsole"
$FOUNDRY help
```

| Command | Purpose |
|---|---|
| `manufacture <identity expression>` | consult registry → acquire evidence → resolve identity → verify → optionally admit |
| `search <query>` | ranked discovery across uid, key, external identifier, label, alias, token |
| `identity <uid\|key\|scheme:id\|alias>` | exact addressing; returns the full identity record |
| `status` | registry verification, counts, unreconciled and non-active identities |

Options: `--registry` `--fixture` `--provider` `--register` `--context` `--json`
`--work-dir` `--dist-dir` `--schema-dir`.

Exit codes: `0` success · `2` usage or error · `4` not resolved / not publishable · `5` registry
verification failed. A considered "not resolved" is deliberately distinct from both success and
error.

**`--registry` is never inferred.** A manufacture that silently found a registry would reuse
identities you did not know existed; one that silently missed a registry would report every
identity as new. Both are wrong in ways that look like success.

## 3. Demonstration script

```bash
REGISTRY=$(mktemp -d)/registry     # disposable

$FOUNDRY manufacture "electric motor"    --registry "$REGISTRY" \
         --fixture examples/demonstration/electric-motor.json --register
$FOUNDRY search      "electric motor"    --registry "$REGISTRY"
$FOUNDRY manufacture "EV traction motor" --registry "$REGISTRY" \
         --fixture examples/demonstration/ev-traction-motor.json --register
$FOUNDRY identity    "wikidata:Q53068"   --registry "$REGISTRY"
$FOUNDRY manufacture "tidal barrage"     --registry "$REGISTRY" \
         --fixture examples/demonstration/tidal-barrage.json --register
$FOUNDRY status                          --registry "$REGISTRY"
```

Expected: run 2 reports **3 reused, 2 new**; the unrelated domain reports **0 reused, 2 new**;
`wikidata:Q53068` resolves `SAME` via `EXTERNAL_IDENTIFIER_CONTINUITY`; status shows 3 packages,
7 identities, 0 unreconciled.

See `examples/demonstration/README.md` for what the demonstration does and does not show.

## 4. Identity lifecycle operations

```bash
REG="java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.registry.RegistryApplication"

$REG supersede --subject uao-… --target uao-… --reason SUPERSEDED_BY_REVISION \
     --justification "why" --recorded-at 2026-08-20T00:00:00Z --registry "$REGISTRY"
$REG retire    --subject uao-… --reason WITHDRAWN --justification "why" --recorded-at … --registry …
$REG merge     --subject uao-A --subject uao-B --target uao-B --reason … --justification … --recorded-at …
$REG split     --subject uao-A --target uao-B --target uao-C --reason … --justification … --recorded-at …
$REG operations --registry "$REGISTRY"
```

`--reason`, `--justification` and `--recorded-at` are mandatory. An identity operation without a
stated reason is indistinguishable from a mistake once its author has moved on, and a wall-clock
default would make operations irreproducible.

**Resolution is not redirected.** After `supersede A → B`, asking for `A` returns `UNRESOLVED /
IDENTITY_SUPERSEDED` naming `B` — not `B` itself. You are told what happened and decide.

## 5. Significance inputs

```bash
$REG significance-inputs uao-… --registry "$REGISTRY"
```

Returns `A_x` and `R_x` plus a `notSupplied` block naming the runtime-owned and engine-owned halves.
**The Foundry supplies inputs to significance and never computes it.** `R_x` is currently and
structurally empty — read its `consequence` field before using the output for anything.

## 6. Architecture

```
  identity expression
        │
        ▼
  ┌───────────────────────────────────────────────────────────┐
  │  registry consulted first  ─────────────────────────────► │  IdentityResolver
  │  (bounded discovery context, no source bytes exposed)     │  SAME / DIFFERENT / UNRESOLVED
  └───────────────────────────────────────────────────────────┘
        │
        ▼
  provider (fixture | Claude adapter)  ── evidence only, never authority
        │
        ▼
  16-stage pipeline
        │  10  identity resolution ──► uid = sha256(resolution_key)
        │      + identity DECISION record (reference, verdict, reasons, evidence)
        │  11  relationship construction ──► participants bound to persistent uids
        │      canonical UROs = 0   ◄── fail-closed, ASA#29
        │  12  canonical build ──► internal_state.foundry_identity
        │           canonical_label · aliases · alias_provenance
        │           resolution_key · semantic_type · external_identifiers
        │           identity_digest ─── what it IS      (stable across state change)
        │           state_version   ─── what it ASSERTS (moves with state)
        ▼
  verification ── re-derives every derived field independently
        │
        ▼
  immutable content-addressed package
        │
        ▼
  ┌──────────────────────────────────────────────────────────────┐
  │  REGISTRY                                                     │
  │   packages/            immutable, re-verified on every read   │
  │   identity-operations/ append-only journal (merge/split/…)    │
  │   index.json           FULLY DERIVED, rebuild-and-compare     │
  └──────────────────────────────────────────────────────────────┘
        │                                    │
        ▼                                    ▼
  A_x / R_x significance inputs        negative-space evaluation
  (never significance itself)          (research level)
```

Two properties hold this together:

- **`uid` is a pure function of `resolution_key`.** Identity is derived, so no state change can
  alter it — and merge is impossible without the mapping layer the operations journal provides.
- **`index.json` is fully derived.** Nothing authored may live there, which is why identity
  decisions live inside packages and lifecycle operations live in a second immutable store.

## 7. Known limitations

| Limitation | Consequence |
|---|---|
| **ASA#29 open** | no canonical URO; relationship-bearing packages are inadmissible, so **no persistent relationship graph can be accumulated** |
| **P9-1 open** | the registry cannot accept a third observation of unchanged material (`packageId` collision) |
| Alias time-awareness | absent — a historical name is indistinguishable from a current one |
| Validity intervals | absent — ASA lifecycle gives a status, not an interval |
| ASA-canonical supersession emission | `lifecycle_status: Superseded` is recognised but never emitted |
| Live provider | untested here; deterministic fixtures only |
| Benchmark result | **persistent identity showed no task-success gain** — see `research/UAO_USI/falsification/` |

## 8. Recovery

- Registry corrupted → `$REG rebuild --registry <path>` re-derives `index.json` from the immutable
  packages, then `$REG verify`.
- A registry with packages but no index refuses to read rather than guessing; rebuild explicitly.
- A refused admission leaves the registry byte-identical; nothing needs undoing.
- Packages are content-addressed: any edit is detected by `verify` on the package or the registry.
