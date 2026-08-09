# Claude Code Provider Adapter

**Adapter version:** `0.1.0`  
**Foundry protocol:** `0.1.0`  
**Role:** non-authoritative live research/evidence provider

This adapter connects the vendor-neutral UAO Foundry command-provider protocol to an installed Claude Code CLI.

It is intentionally outside the Java canonicalisation core.

```text
Foundry request + verified registry context
                ↓
       claude_provider.py
                ↓
     Claude Code research only
       WebSearch / WebFetch
                ↓
 schema-constrained provider bundle
                ↓
   adapter custody / normalization
                ↓
        Java UAO Foundry
 validation → resolution → canonical build
 verification → publication → registry
```

Claude does not write canonical UAOs or UROs.

## Requirements

- Python 3.11+
- Claude Code installed and authenticated
- Java 21 and Maven for the Foundry

The current Claude Code CLI supports non-interactive `-p/--print`, structured `--output-format json`, `--json-schema`, tool availability/permission controls, `--no-session-persistence`, model selection and turn limits. The adapter uses those surfaces rather than interactive automation.

## Security boundary

For manufacturing calls the adapter starts Claude Code with:

- `--bare`;
- `--no-session-persistence`;
- `--no-chrome`;
- `--tools WebSearch,WebFetch` to restrict built-in tool availability;
- `--permission-mode dontAsk`;
- allowed tools: `WebSearch`, `WebFetch`;
- explicitly disallowed defense-in-depth entries: `Bash`, `Read`, `Write`, `Edit`, `Glob`, `Grep`, `Agent`, `Skill`.

The model therefore receives the manufacturing request, bounded registry discovery context and bounded immutable registry evidence through stdin. It is not given general repository filesystem or shell capability by this adapter.

This is a provider-level safety boundary, not a general Claude Code security certification.

## Configuration

Optional environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `UAO_FOUNDRY_CLAUDE_BIN` | discovered `claude` | exact Claude Code executable |
| `UAO_FOUNDRY_CLAUDE_MODEL` | `sonnet` | Claude Code model/alias |
| `UAO_FOUNDRY_CLAUDE_MAX_TURNS` | `8` | bounded non-interactive turns |
| `UAO_FOUNDRY_CLAUDE_TIMEOUT_SECONDS` | `240` | adapter-side Claude process timeout |
| `UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD` | unset | optional Claude Code print-mode budget ceiling |
| `UAO_FOUNDRY_FIXED_CLOCK` | current UTC | deterministic test/replay clock override |
| `UAO_FOUNDRY_REGISTRY_EVIDENCE_BYTES` | `1000000` | maximum registry evidence bytes placed in the Claude prompt |
| `UAO_FOUNDRY_REGISTRY_EVIDENCE_FILES` | `8` | maximum registry evidence files placed in the Claude prompt |

Credentials remain owned by Claude Code. Do not commit OAuth tokens, API keys or credential files into this repository.

## Stable semantic identity policy

The adapter applies a narrow live-provider policy before allowing a candidate bundle to leave the adapter:

1. **Existing identity:** reuse the exact `resolutionKey` from the verified Foundry registry.
2. **Durable external identifier:** use `ext:<scheme>:<identifier>`.
3. **Foundry-local new identity:** use `foundry:v0.1:<semantic-type-slug>:<canonical-label-slug>`.

New live keys outside `ext:*` or `foundry:v0.1:*` fail closed.

Keys that appear to contain UUID/session/model/timestamp material also fail closed. Model/session/turn identity is not semantic identity.

This is an adapter discipline for repeatable manufacturing; it does not claim to solve all future cross-domain identity equivalence questions. See [`../../docs/STABLE-SEMANTIC-IDENTITY.md`](../../docs/STABLE-SEMANTIC-IDENTITY.md).

## Relationship authority

Current ASA authority does not provide the general machine-readable Relationship Type role registry required for arbitrary URO construction. See:

- `17th2nd/uao-foundry#3`;
- `17th2nd/ASA#29`.

Accordingly this adapter rejects a non-empty `candidates.relationships` array and emits `relationships: []` only. It does not invent Relationship Type versions or roles and does not encode relationship semantics into fake UAO fields.

Once ASA#29 supplies a governed source edition, this restriction can be replaced by source-driven validation in a separately reviewed Foundry change.

## Registry evidence custody

A registry-aware Foundry invocation sets `UAO_FOUNDRY_REGISTRY_ROOT` and supplies bounded discovery context.

For each matched immutable registry occurrence the adapter may provide Claude with a bounded evidence record:

```json
{
  "locator": "registry://<package-id>/canonical-identities.json",
  "sha256": "...",
  "content": "... exact UTF-8 bytes ..."
}
```

If Claude cites a `registry://` source, the model-supplied content is discarded. The adapter restores the exact verified registry bytes before returning the provider bundle. The Foundry later hashes those bytes again and `ReuseAnalyzer` verifies the locator against the immutable package.

A model cannot make changed text become registered evidence merely by retaining a `registry://` prefix.

## Executable-provider boundary

The Java Foundry accepts only an exact executable provider path. Git hosting may not preserve an executable bit for files created through repository APIs, so `scripts/manufacture-claude.sh` manufactures a temporary `0700` launcher whose only action is `exec python3 <checked-in adapter>`. This preserves the Foundry's executable-provider invariant without requiring the Python source file itself to be executable after every clone.

## Direct protocol test

The adapter itself expects one protocol envelope on stdin. Most operators should not call it manually; use the Foundry scripts instead.

```bash
cat provider-envelope.json | python3 adapters/claude-code/claude_provider.py
```

Stdout is reserved for exactly one JSON provider bundle. Diagnostics are written to stderr.

## Tests

Adapter-only tests:

```bash
python3 -m unittest discover -s adapters/claude-code/tests -p 'test_*.py' -v
```

The sprint CI also runs the adapter through the Java registry-aware manufacturing entry point with a fake Claude executable so that no live Claude credentials are required in GitHub Actions.
