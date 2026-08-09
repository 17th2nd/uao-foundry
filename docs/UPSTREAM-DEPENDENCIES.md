# Upstream Dependencies

## URO-TYPE-1 — Current Relationship Type role authority

**Status:** OPEN / BOUNDED  
**Blocks:** canonical publication of non-empty arbitrary domain URO candidates  
**Does not block:** identity intake, interpretation, scope, planning, evidence acquisition, candidate extraction/validation, UAO identity resolution, canonical UAO construction, completeness, package verification, checkpoint/resume, or cross-domain UAO proof.

### Evidence searched

The Foundry implementation review checked the current ASA CSS, ADR-002 relationship architecture, active compiler specification, implementation specification, older runtime interchange specification and built-in meta-type definitions.

The current CSS supplies the URO structural record and requires role-bound participation. ADR-002 requires Relationship Type versions to own role schemas, cardinalities, permitted participant kinds and identity-bearing declarations. The active compiler specification requires validators to check participant roles against the declared Relationship Type.

The review did not locate a current CSS-era authoritative registry/contract that the Foundry can use to mint or validate **arbitrary domain** Relationship Type versions.

### Fail-closed behaviour

When a fixture/provider emits no relationship candidates, the relationship stage completes with an empty canonical URO set.

When a provider emits a relationship candidate without current type-role authority, the Foundry:

1. validates the candidate's universal structural shape;
2. records `URO_TYPE_AUTHORITY_UNAVAILABLE`;
3. excludes the candidate from canonical URO publication;
4. retains it in candidate/provenance records;
5. marks the publication decision `EVIDENCE_INCOMPLETE`.

### Exact upstream evidence requested

To close this dependency, provide the current authoritative source defining or generating:

- Relationship Type version identifiers accepted by the current CSS;
- named participant/contextual roles;
- role cardinality;
- permitted participant kinds;
- identity-bearing roles and literals;
- symmetry rules;
- version/compatibility rules;
- validation procedure used by the current ASA compiler/runtime.

An older or draft source may be useful as evidence, but the Foundry will not silently promote it to current authority.
