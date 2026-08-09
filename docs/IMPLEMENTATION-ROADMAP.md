# UAO Foundry v0.1 Implementation Status

The original M0–M10 roadmap has been reconciled to the recovered execution blueprint. The v0.1 manufacturing core is implemented as one coherent sixteen-stage vertical slice.

## Complete in v0.1

- repository/build/CLI foundation;
- universal JSON contracts and fail-closed validator;
- deterministic request/job identifiers;
- provider boundary and offline deterministic fixture adapter;
- job checkpoints, status and resume;
- seed normalisation;
- provider-driven interpretations and explicit scope;
- dynamic manufacturing plans/source strategies;
- source snapshot acquisition and hashing;
- candidate extraction boundary and quarantine;
- identity resolution and stable UAO IDs;
- ASA-aligned canonical UAO construction;
- dynamic coverage analysis;
- structural verification and forbidden-field checks;
- publication decisions;
- package manufacture, manifest and SHA-256 inventory;
- `verify` and `inspect` commands;
- byte-determinism and tamper negative controls;
- three-domain fixture proof through the same compiled executable.

## Bounded dependency

Arbitrary non-empty domain URO publication remains fail-closed until current ASA Relationship Type role authority is available. See `UPSTREAM-DEPENDENCIES.md`.

## Next after v0.1 acceptance

1. Link the current Relationship Type role authority and close `URO-TYPE-1`.
2. Implement one or more live provider adapters behind `FoundryProvider` (AI/research/source connectors) without changing the pipeline contract.
3. Add governed registry discovery and semantic-delta reuse against previously manufactured packages.
4. Integrate ALA/other consumers through package/registry APIs.
5. When the programme later migrates to a clean university/research/partnership repository, perform the already-recorded UAO → USI terminology review rather than renaming in place.
