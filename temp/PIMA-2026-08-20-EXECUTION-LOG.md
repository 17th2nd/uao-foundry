# PIMA Execution Log

**Run label:** `PIMA-2026-08-20`
**Programme:** UAO Foundry — Persistent Identity Manufacturing Alpha
**Base SHA:** `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e`
**Branch:** `programme/persistent-identity-manufacturing-alpha`
**Operator:** Claude (lead manufacturing operator)

Reports in `temp/` are operator working records. They carry no authority.

---

## Phase 0 — Repository truth

**Status:** COMPLETE

Governance preconditions verified against the repository, not recollection:

- `origin/main` = `2bc2871d2a7c36c9b4d67881d40827ff2e948d2e` — **confirmed after fetch**
- working tree clean — confirmed
- `mvn -B -ntp clean verify` → BUILD SUCCESS, 35/35 tests — confirmed
- UAO terminology authoritative (ADR-0001 Accepted) — confirmed
- `17th2nd/ASA#29` OPEN — confirmed via `gh`
- `17th2nd/uao-foundry#3` OPEN — confirmed via `gh`
- `relationshipTypeRoleAuthority: NOT_FOUND_IN_CURRENT_CSS_AUTHORITY_SURFACE` — confirmed

**No material authority change. Programme proceeds.**

### Deviations recorded

1. Local clone `main` was stale at `cb20687d`; `HEAD` was on `sprint/2026-08-10-audit-remediation-r1`.
   Fetched; programme branch cut from the canonical SHA directly.
2. Workstation has no JDK (JRE only) and no Maven. Provisioned Temurin JDK 21 + Maven 3.9.9
   **into the session scratchpad only**. No system install, no repository change.
3. `pytest` absent — Python adapter tests are CI-only evidence on this workstation.

### Principal Phase 0 finding

`uid = "uao-" + sha256(resolutionKey)[0..12]`. Identity is *derived*, never *resolved*. There is
no identity decision, no evidence for sameness, and no mapping layer — so MERGE is structurally
impossible without one. The canonical UAO schema is closed (`additionalProperties: false`), so
all Foundry-owned identity material must live under `internal_state`. The registry index is fully
derived from immutable packages and verified by rebuild-and-compare, so non-derivable identity
material cannot live in `index.json`.

### Finding P0-1 (real defect, latent)

`externalIdentifiers` is declared in `schemas/candidate-identity.schema.json`, supplied by all
fixtures and by the Claude adapter, and **read by no Java code**. A provider that correctly
supplies a durable external identifier has that evidence silently discarded. Fail-open behaviour
in an otherwise fail-closed codebase. Scheduled as the first Phase 1 increment.

**Deliverables:** `research/UAO_USI/CURRENT_STATE.md`, `README.md`, `terminology/UAO_VS_USI.md`.
