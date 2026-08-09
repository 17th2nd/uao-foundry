# UAO Foundry Provider Protocol v0.1

**Status:** Experimental Foundry interface  
**Authority:** Foundry-owned execution interface; it does not define ASA semantic authority  
**Protocol version:** `0.1.0`

## 1. Purpose

The provider protocol allows UAO Foundry to obtain identity interpretation, scope, manufacturing-plan, source, evidence and candidate material from an external research adapter without giving that adapter authority to manufacture canonical UAOs or UROs.

A provider may be backed by a deterministic program, a local model, Claude, Codex, Grok, another research service, or a future acquisition system. The Java Foundry core remains vendor-neutral.

The boundary is deliberate:

```text
external provider / research adapter
        |
        | intermediate provider bundle only
        v
UAO Foundry validation boundary
        |
        v
16-stage deterministic pipeline
        |
        v
canonical build -> verification -> publication decision
```

The external provider can propose. The Foundry validates, resolves, canonicalises, verifies and decides publication.

## 2. Invocation

The CLI accepts an explicit executable path:

```bash
java -jar target/uao-foundry-0.1.0-SNAPSHOT.jar \
  manufacture "arbitrary identity" \
  --provider-command /absolute/path/to/provider-adapter \
  --provider-timeout-seconds 300
```

The Foundry invokes the exact path with Java `ProcessBuilder`. It does **not** invoke a shell and does not interpolate command text.

Only one provider may be selected for a transaction:

- `--fixture <bundle.json>` -> deterministic fixture mode;
- `--provider-command <executable>` -> live provider mode.

The request `executionMode` and provider execution mode must agree. A request-file transaction is not silently rewritten to match a provider.

## 3. Request envelope

The provider receives exactly one canonical JSON object on standard input:

```json
{
  "constraints": {
    "canonicalWriteAllowed": false,
    "responseRole": "INTERMEDIATE_PROVIDER_BUNDLE_ONLY",
    "responseSchema": "fixture-bundle.schema.json"
  },
  "protocolVersion": "0.1.0",
  "request": {
    "executionMode": "live",
    "identitySeed": "arbitrary identity",
    "inputLanguage": "en",
    "manufacturingProfile": "experimental",
    "requestId": "req-...",
    "requestedVersion": "0.1.0",
    "schemaVersion": "0.1.0"
  }
}
```

Optional request controls are included when present.

The provider environment also receives:

```text
UAO_FOUNDRY_PROVIDER_PROTOCOL=0.1.0
```

No API credential is supplied by the Foundry protocol. Provider-specific credentials belong to the adapter's own process environment or secret-management mechanism and must not be written into its JSON response.

## 4. Response contract

The provider must write exactly one JSON provider bundle to standard output and exit `0`.

For v0.1, the live-provider response intentionally reuses `schemas/fixture-bundle.schema.json`. This keeps fixture and live acquisition behind the same intermediate contract while the provider ecosystem is still experimental.

The bundle supplies:

- the exact `identitySeed` being handled;
- fixed assertion/acquisition clock and knowledge horizon;
- interpretation candidates;
- explicit scope resolution;
- provider-generated manufacturing plan;
- source strategy;
- acquired source records and source content;
- candidate identities, claims, relationships and evidence;
- optional candidate states, events and language mappings;
- completion-question coverage answers.

The response is **not** a canonical UAO package. A provider response that fails the schema is rejected before the manufacturing pipeline begins.

## 5. Canonical-authority boundary

A provider is not permitted to bypass Foundry stages by returning canonical output. The protocol request explicitly declares:

```json
"canonicalWriteAllowed": false
```

Canonical responsibilities remain inside the Foundry:

1. request/schema validation;
2. deterministic transaction identifiers;
3. candidate validation and quarantine;
4. identity resolution;
5. URO authority checks;
6. current-ASA-compatible canonical UAO projection;
7. completeness analysis;
8. verification;
9. publication decision;
10. package manifest and checksums.

Current ASA CSS remains the semantic authority for ASA primitives. Provider output cannot override it.

## 6. Resource and failure policy

Command-provider execution is bounded:

- timeout: minimum 1 second, maximum 3600 seconds;
- default timeout: 300 seconds;
- maximum accepted stdout: 16 MiB;
- diagnostic stderr read: capped at 256 KiB;
- non-zero provider exit: transaction fails closed;
- empty output: transaction fails closed;
- invalid JSON: transaction fails closed;
- schema-invalid response: transaction fails closed.

No provider failure is converted into guessed knowledge.

## 7. Provider snapshot custody

A successful provider response is canonicalised as JSON and captured once as:

```text
work/<job-id>/provider-snapshot.json
```

Its canonical SHA-256 participates in the deterministic job identity through the provider hash stored in the checkpoint.

The snapshot is also copied into the manufactured package as:

```text
provider-snapshot.json
```

and is therefore covered by:

- `manifest.json` inventory;
- `checksums.sha256`;
- package verification;
- provider-bundle schema validation.

This preserves the actual interpretation/planning/acquisition input that preceded canonicalisation.

## 8. Resume semantics

`resume` never invokes the original external provider.

Instead it:

1. reads the original checkpoint;
2. resolves `provider-snapshot.json` inside the job directory;
3. validates the snapshot schema;
4. recomputes its canonical hash;
5. compares that hash with the checkpoint's original provider hash;
6. rejects any mismatch;
7. replays the validated snapshot through `SnapshotProvider`;
8. resumes hash-valid completed stages.

This prevents a resumed job from silently receiving different AI/research output midway through manufacture.

Deleting or disconnecting the original provider after a successful acquisition does not prevent deterministic resume.

## 9. Adapter responsibilities

A live adapter may perform domain-dependent work outside the core, including:

- disambiguation research;
- source discovery and retrieval;
- source licensing/locator capture;
- evidence extraction;
- candidate identity/claim generation;
- manufacturing-plan generation;
- coverage assessment.

The adapter must preserve source text/evidence sufficient for the Foundry package to retain provenance. It must not place secrets in returned source content or metadata.

Provider-specific policy belongs outside the generic Java core. Adding a Claude, Codex or Grok adapter must not add identity-specific branches to `src/main/java`.

## 10. Relationship authority remains fail-closed

The provider may propose relationship candidates, but v0.1 does not grant those candidates canonical URO status merely because an external model supplied them.

Until the current authoritative ASA Relationship Type role surface is linked/resolved, non-empty arbitrary-domain relationship candidates remain subject to `URO_TYPE_AUTHORITY_UNAVAILABLE`, are excluded from canonical URO publication, and force an incomplete publication disposition.

See `UPSTREAM-DEPENDENCIES.md` and repository Issue #3.

## 11. Security posture

The protocol is intentionally a narrow process boundary, not a plugin runtime with broad Foundry privileges.

The Foundry does not:

- execute provider output as code;
- invoke provider text through a shell;
- disclose secrets through the protocol;
- allow a provider to write canonical package files directly;
- re-run a provider during resume;
- accept schema-invalid provider data;
- silently repair failed provider output.

A provider executable still runs with the operating-system permissions of the user launching the Foundry. Production deployments should therefore sandbox provider adapters according to their own threat model.

## 12. Next interface evolution

The v0.1 provider bundle is intentionally broad so the complete pipeline can be exercised now. Future versions may split acquisition into narrower stage-specific provider contracts, add explicit source-content storage references, or support separately governed adapter capabilities.

Any such change must preserve the core rule: **providers propose evidence-bearing intermediate material; the Foundry owns deterministic canonical manufacture.**
