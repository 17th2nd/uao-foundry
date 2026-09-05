# CLAUDE-USI-PEOPLE-LIVE-MANUFACTURE-001

**Date:** 2026-09-05 · **Branch:** `programme/usi-manufacturer-application-alpha` at `3a15bd2` (+ uncommitted adapter patch) · **Operator:** Claude (Fable 5.1) on Brock's instruction · **Provider:** Claude Code 2.1.261, model alias `sonnet`, WebSearch/WebFetch only
**Report page:** https://claude.ai/code/artifact/1458d27f-40c4-4066-8d0a-12fccba4a223

## Request
Manufacture USI packages for six people shown in a Safari tab strip: Norbert Wiener, Herbert A. Simon, Judea Pearl, Douglas Engelbart, Stafford Beer, Elinor Ostrom.

## Result
All six manufactured, verified and admitted to a fresh non-canonical registry at `work/usi-people/registry` (gitignored). `OperatorConsole status`: 6 packages, 19 identities, 0 unreconciled, 0 identity operations, verification PASS.

| Seed | Package | Root uid | Root key | Identities | Sources |
|---|---|---|---|---|---|
| Norbert Wiener | pkg-639286abda65eaaf | uao-11eb71766754 | ext:wikidata:Q178577 | 2 | 4 |
| Herbert A. Simon | pkg-d9488187d2a74f65 | uao-b160552cb26e | ext:wikidata:Q181529 | 3 | 8 |
| Judea Pearl | pkg-549240a93185b759 | uao-bda651d022bf | ext:wikidata:Q92824 | 3 | 6 |
| Douglas Engelbart | pkg-2d4ab4307cf5e410 | uao-830ce8cca652 | ext:wikidata:Q92614 | 5 | 7 |
| Stafford Beer | pkg-167fb37d1b66d4dc | uao-09f5f6b2f9fd | ext:wikidata:Q796226 | 4 | 7 |
| Elinor Ostrom | pkg-e5dd1d8570e4c45c | uao-1fced4a70aa1 | ext:wikidata:Q153761 | 2 | 4 |

All packages EXPERIMENTAL (experimental profile). 0 relationships (fail-closed, ASA#29). Run evidence in `work/usi-people/runs/`, logs in `work/usi-people/logs/`, packages in `work/usi-people/dist/`.

## This was the first live-provider manufacture on this machine
Three drifts/gaps had to be closed before a package would admit. All changes are in the working tree only, on the audited PR #16 branch — **not committed, awaiting approval**; adapter unit tests 12/12 after the patch.

1. **CLI drift (blocking):** Claude Code 2.1.261 rejects `"$schema": draft/2020-12` inside `--json-schema` (`no schema with key or ref ...`). Also `--bare` now skips keychain/credential-file reads, so the nested research process reported "Not logged in" under subscription auth. Fix: `_cli_schema()` strips `$schema` for the CLI copy; `--bare` is now an explicit opt-out via `UAO_FOUNDRY_CLAUDE_BARE=0`, recorded as `bare=false` in `sourceStrategy.authorityNotes`. Default (bare on) and the fake-binary containment contract are unchanged. `tests/test_claude_provider.py` now asserts byte-equality against the composed CLI schema rather than the raw fixture-bundle file.
2. **Provider contract gap:** `fixture-bundle.schema.json` types interpretations/scope/plan/strategy/candidates loosely; the Java pipeline validates them strictly at stages 3–9. Live bundle #1 failed at stage 3 (freelanced interpretation shape); bundle #2 at stage 9 (`externalIdentifiers` scheme `mathGenealogy` not lower-case). Fix: the CLI schema is composed from the stage schemas (`interpretation-candidates`, `scope-resolution`, `manufacturing-plan`, `source-strategy`, `candidate-identity/claim/evidence`), plus `propertyNames` lower-case constraint on external identifiers and `relationships.maxItems: 0`; the prompt now states the Java cross-field rules.
3. **Publication gate (engine correct, prompt wrong):** bundle #3 verified but was refused admission: `EVIDENCE_INCOMPLETE` because the model posed two open-ended completion questions and honestly answered `unresolved`. Prompt now says completion questions are the plan's definition of done and must be researched in-run; open matters go to `scopeResolution.unresolvedQuestions`. Bundle #4 admitted.

## Quality flags on the manufactured content (candid)
- **Keys:** Engelbart's patent (`foundry:v0.1:patent:us-3541541a`) and 1962 report (`foundry:v0.1:document:...`) have durable external identifiers the provider did not use. Beer's *Brain of the Firm* labelled "1972 book" but keyed `ext:isbn:9780471276876` (Wiley 2nd edition, 1981). Pearl's 1988 book keyed on an edition ISBN; the provider itself flagged edition ambiguity. These three should be re-manufactured before reuse; the registry's lexical-continuity guard will refuse a same-key/different-name collision, so a new key is the path.
- **Provenance:** Amazon listing used as bibliographic source for Pearl's 1988 book; 11/36 sources licence UNKNOWN; Wikidata/Wikipedia carry most weight.
- **Coverage:** Wiener `q-cybernetics-book-identity` partial; Pearl `q-birth-death-dates` partial (living person); Engelbart `q-education-career` partial. Everything else covered. Quarantine empty in all six.
- `semantic_type` is null on all 19 identities — by design (`IdentityKernel`: derived from key grammar, null for `ext:`).

## Timing
Per person 1m54s–7m22s wall (Engelbart longest, 7 sources). Four Wiener attempts total ≈ 10 min of provider time spent on the drift/gap fixes above.

## Not done / decisions for the founder
- Commit of the adapter + test patch (2 files, +65/−4) — needs approval; PR #16 is under independent audit, so this should probably be a separate remediation commit with a Codex read-only look.
- Whether `work/usi-people` should be promoted anywhere; it is deliberately outside version control.
- Re-manufacture of the three mis-keyed component identities.
