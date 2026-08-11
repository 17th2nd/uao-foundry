# Sprint 2026-08-10 — Independent Audit Remediation R1

**Status:** IMPLEMENTED / AWAITING FINAL-HEAD CI AND INDEPENDENT RE-AUDIT
**Base authority:** `main` at `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb`
**Independent audit basis:** Claude adversarial audit supplied 2026-08-10
**Authority boundary:** ASA#29 remains unresolved; this sprint does not create Relationship Type authority.

## Purpose

Remediate independently reproduced integrity and identity defects before any persistent real-knowledge or TAFE demonstration registry is populated.

## Acceptance-critical findings

- **F-1 HIGH — semantic package forgery:** meaning-bearing files could be edited, mirrored, and re-checksummed while `verify` still passed.
- **F-2 HIGH — registry evidence custody gap:** plain manufacture accepted `registry://` locators without verified registry custody.
- **F-3 HIGH — unconstrained `resolutionKey`:** provider-controlled string equality could silently merge distinct meanings or fragment the same meaning.
- **F-4 HIGH — checkpoint trust-root defect:** a stage output plus its checkpoint hash could be forged and reused on resume.
- **F-6b HIGH — Claude containment evidence:** containment argv was designed but not mechanically asserted.
- **F-9 HIGH — Claude version:** pre-v2.1.205 schema enforcement can silently degrade for schemas using `format`.

## Same-sprint hardening

- verify registry index before every read/discovery;
- make failed registry admission transactional;
- prevent same-label package-path overwrite;
- surface invalidated resume stages;
- explicit MCP denial and bounded Claude environment inheritance;
- record custom provider endpoint provenance without tokens;
- validate optional budget bound;
- add relationship-bearing end-to-end regression proving ASA#29 fail-closed behavior.

## Design constraints

1. Package verification proves content-addressed structural integrity/self-consistency, not factual truth or third-party authorship.
2. Meaning-bearing package content determines `contentDigest` and package ID.
3. `registry://` evidence is resolved by the Java core only with a verified registry context.
4. Resolution keys must be canonical; registry reuse fails closed on incompatible lexical name continuity.
5. Resume re-derives cached stage outputs from the captured provider snapshot before accepting reuse.
6. URO publication remains blocked until governed Relationship Type role authority exists upstream.
7. No persistent demonstration registry is populated until this candidate is independently re-audited.

## Merge gate

Do not merge until:

- all existing workflows pass on the exact final head;
- `UAO Foundry independent-audit remediation` passes on the exact final head;
- Claude containment C-1 through C-17 pass;
- relationship-bearing fixture reaches `EVIDENCE_INCOMPLETE`, zero canonical UROs, and complete unresolved relationship accounting;
- Claude independently replays the adversarial attacks against the exact final candidate SHA;
- documentation distinguishes integrity, authenticity, factual correctness, reuse identity semantics, and ASA authority standing.
