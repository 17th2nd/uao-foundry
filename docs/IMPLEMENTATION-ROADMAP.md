# UAO Foundry v0.1 Implementation Status

The original M0–M10 roadmap has been reconciled to the recovered execution blueprint. The v0.1 manufacturing core is implemented as one coherent sixteen-stage vertical slice, with live-provider, immutable-registry and semantic-delta layers now built around it.

## Complete in accepted v0.1 main before this sprint

- repository/build/CLI foundation;
- universal JSON contracts and fail-closed validator;
- deterministic request/job identifiers;
- deterministic offline fixture provider;
- vendor-neutral live command-provider boundary;
- provider snapshot custody and snapshot-only resume;
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
- three-domain fixture proof through the same compiled executable;
- immutable verified-package registry;
- deterministic registry search/discovery context;
- registry verification/rebuild/tamper detection;
- registry-aware live provider context;
- Foundry-computed semantic-delta/reuse report;
- cryptographic validation of `registry://` evidence against immutable prior package bytes.

## Sprint 2026-08-10 additions

This sprint adds release/readiness surfaces without changing the UAO→USI terminology decision or inventing upstream relationship authority:

- bounded Claude Code live research/evidence adapter;
- stable live semantic `resolutionKey` discipline;
- exact registry-evidence byte restoration after model output;
- clone-portable executable-provider launcher;
- live preflight and one-command Claude manufacture wrapper;
- independent adapter unit and end-to-end fake-Claude CI;
- TAFE cumulative-manufacturing demonstration guide isolated from production logic;
- stale provider/semantic-delta CI JAR-name correction;
- sprint execution and independent-audit handoff documentation.

The sprint remains incomplete until all workflows pass on the exact final PR head and the execution/audit packet is committed.

## Bounded upstream dependency

Arbitrary non-empty domain URO publication remains fail-closed until ASA supplies a current machine-readable Relationship Type role authority.

Tracking:

- `17th2nd/uao-foundry#3`;
- `17th2nd/ASA#29`.

The authority sweep confirmed this is a genuine source/governance gap rather than a misplaced Foundry file. See `UPSTREAM-DEPENDENCIES.md`.

## Remaining path after this sprint

1. **Real workstation Claude smoke:** run one authenticated Claude Code manufacture outside CI, verify the package and preserve its provider snapshot; CI intentionally uses a fake Claude executable and therefore cannot prove real account/model/network operation.
2. **TAFE demonstration manufacture:** manufacture the selected qualification/topic pair against a persistent demo registry and show the actual `reuse-report.json`, not a predicted reuse claim.
3. **ASA#29:** independently establish and adopt the governed Relationship Type source edition required for arbitrary URO role validation; only then integrate URO publication in a separately reviewed Foundry sprint.
4. **Consumer APIs:** expose verified package/registry consumption to ALA and other downstream systems without folding learner/application logic into the Foundry.
5. **Release freeze:** after independent audit and real-provider smoke, decide whether `v0.1.0` is ready for an immutable release/tag and formal demonstration baseline.
6. **Later clean migration:** when the programme migrates to a clean university/research/partnership repository, execute the already-recorded UAO → USI terminology review rather than renaming this prototype in place.
