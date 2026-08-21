# Persistent Identity Manufacturing — Acceptance Demonstration

**Status:** Demonstration material. Non-authoritative. Synthetic evidence.

Three deterministic fixture bundles that exercise the programme's success criterion end to end
without a live provider:

> Manufacture a persistent identity once. Refer to the same identity later. Preserve its history
> and evidence. Reuse prior governed knowledge where justified. Manufacture only what is genuinely
> new.

| Bundle | Purpose |
|---|---|
| `electric-motor.json` | Run 1 — three new identities: the motor, a rotor, a stator |
| `ev-traction-motor.json` | Run 2 — reuses all three, adds two genuinely new ones |
| `tidal-barrage.json` | Run 3 — an unrelated domain, proving the machine is not domain-specific |

The evidence is synthetic and proves nothing about electric motors. What it proves is that the
*machine* reuses identity correctly.

## Running it

```bash
mvn -B -ntp package
FOUNDRY="java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.uaofoundry.console.OperatorConsole"
REGISTRY=$(mktemp -d)/registry          # disposable, per programme §31

$FOUNDRY manufacture "electric motor"   --registry "$REGISTRY" \
         --fixture examples/demonstration/electric-motor.json --register
$FOUNDRY search      "electric motor"   --registry "$REGISTRY"
$FOUNDRY manufacture "EV traction motor" --registry "$REGISTRY" \
         --fixture examples/demonstration/ev-traction-motor.json --register
$FOUNDRY identity    "wikidata:Q53068"  --registry "$REGISTRY"
$FOUNDRY manufacture "tidal barrage"    --registry "$REGISTRY" \
         --fixture examples/demonstration/tidal-barrage.json --register
$FOUNDRY status                         --registry "$REGISTRY"
```

## Observed result

| Run | Reused | New | Verification | Admission |
|---|---|---|---|---|
| electric motor | 0 | 3 | PASS | REGISTERED |
| EV traction motor | **3** | **2** | PASS | REGISTERED |
| tidal barrage | 0 | 2 | PASS | REGISTERED |

Rediscovery by durable external identifier `wikidata:Q53068` resolves `SAME` via
`EXTERNAL_IDENTIFIER_CONTINUITY` — the identity is findable by a stable third-party address, not
merely by the words used the first time.

The reused motor identity ends with **two occurrences, two identity decisions and one state
version**: one identity, in one state, evidenced twice. Not two states, and not two identities.

## What the demonstration does not show

- **Registry sources reused: 0.** These fixtures carry their own sources rather than `registry://`
  locators. Cryptographically-verified source reuse is exercised by `SemanticDeltaTest` and by the
  live Claude adapter path, not here.
- **No relationships.** All three bundles have empty relationship candidate sets, so nothing is
  blocked by ASA#29. A relationship-bearing bundle would correctly become `EVIDENCE_INCOMPLETE` and
  be refused registry admission — see `OperatorConsoleTest`.
- **Nothing about the world.** The evidence is invented. `EXPERIMENTAL` means the structural gates
  passed, not that the claims are true.

## Why the resolution keys look the way they do

Reuse requires **exact** continuity of the canonical `resolutionKey`, lexical name continuity, and
an identical semantic-variant digest. `ev-traction-motor.json` therefore reproduces the motor,
rotor and stator candidates verbatim. That is the discipline working, not a convenience: a provider
that paraphrased them would manufacture duplicates, and correctly so, because the Foundry cannot
tell a paraphrase from a different object.
