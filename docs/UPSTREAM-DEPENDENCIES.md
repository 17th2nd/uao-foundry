# Upstream Dependencies

## URO-TYPE-1 — Current Relationship Type role authority

**Status:** OPEN / CONFIRMED UPSTREAM AUTHORITY GAP  
**Foundry issue:** `17th2nd/uao-foundry#3`  
**ASA escalation:** `17th2nd/ASA#29`  
**Blocks:** canonical publication of non-empty arbitrary-domain URO candidates  
**Does not block:** identity intake, interpretation, scope, planning, evidence acquisition, candidate extraction/validation, UAO identity resolution, canonical UAO construction, completeness, package verification, checkpoint/resume, verified registry admission, semantic-delta reuse, or cross-domain UAO proof.

### Authority sweep completed before Sprint 2026-08-10

The dependency was re-investigated before the Claude-integration/release-readiness sprint across:

- current `17th2nd/ASA` `main` at the Foundry authority-lock commit;
- current CSS `specification/core/canonical_source.json`;
- `toolchain/COMPILER_FANOUT_HANDOFF.md`;
- ADR-002 and amendments;
- current implementation/compiler specifications;
- frozen/runtime interchange and meta-type material;
- CC-0 bootstrap generator and conformance planning;
- preserved/review branches, including later SPEC-0018 candidate work;
- the project File Library;
- the parallel Google Drive authority/review corpus.

The result is not merely “file not found.” The project record consistently shows that the general machine-readable Relationship Type source representation has **not yet been established as current implementation authority**.

### What current ASA does provide

The current CSS provides:

- URO structural identity;
- type-version field shape;
- n-ary participant array;
- free-form participant role string;
- identity literals container;
- contextual bindings container.

ADR-002 establishes the architecture that Relationship Type versions own role schemas and semantic binding rules.

The compiler fan-out handoff requires generated validators to validate participant roles against the declared Relationship Type and explicitly directs implementers to report architectural uncertainty rather than fill it locally.

CC-0 bootstrap material contains concrete type definitions with role names, `min`/`max`, participant kinds, identity flags, literal definitions and symmetry. These are valuable conformance evidence, but they are embedded test/bootstrap definitions rather than a general current ASA source-of-truth registry for arbitrary domains.

### Evidence that the gap is deliberate/current

Earlier validator planning explicitly classified full role validation as blocked until a Relationship Type registry/source representation was approved.

Later compiler review concluded that the current CSS/compiler IR has no first-class Relationship Type registry facet, and that introducing one requires a named source/schema/IR/validator edition rather than silently placing ordinary relationship-type definitions inside UAO/URO primitive definitions.

A later consolidated SPEC-0018 candidate includes substantial bounded type/vocabulary design, but explicitly declares itself non-ratified, non-implementation-ready and subject to outstanding Council dependencies. It therefore cannot be promoted by the Foundry into current ASA authority.

### Required governed machine-readable contract

To close this dependency, ASA must provide an exact governed source or deterministic authoritative pointer that supplies, per Relationship Type version:

- canonical/versioned type identifier;
- namespace/owner and lifecycle/admission state;
- participant role names;
- contextual role names;
- role kind/category;
- minimum/maximum cardinality;
- permitted participant kinds;
- identity-bearing role flags;
- identity-literal names, datatypes and identity effect;
- symmetry and ordering semantics;
- provenance-role declarations where applicable;
- compatibility/supersession/migration metadata;
- deterministic validation and diagnostic contract.

`17th2nd/ASA#29` records this upstream requirement and its definition of closure.

### Fail-closed Foundry behaviour

When a provider emits no relationship candidates, the relationship stage completes with an empty canonical URO set.

When a generic provider emits a relationship candidate without current type-role authority, the Java Foundry:

1. validates the candidate's universal structural shape;
2. records `URO_TYPE_AUTHORITY_UNAVAILABLE`;
3. excludes the candidate from canonical URO publication;
4. retains it in candidate/provenance records;
5. marks the publication decision `EVIDENCE_INCOMPLETE`.

The Claude Code adapter in Sprint 2026-08-10 is stricter still: it rejects a non-empty relationship candidate array before returning the intermediate provider bundle. This keeps the live model from proposing arbitrary UROs while the authoritative type source is absent.

### Closure rule

Do not close this dependency because an old draft, CC-0 fixture or model-generated type definition exists.

Close it only when ASA#29 identifies an exact path/commit/authority state that a downstream compiler can read and deterministically validate unknown/missing roles, cardinality, participant kinds, identity effects, symmetry and version resolution.
