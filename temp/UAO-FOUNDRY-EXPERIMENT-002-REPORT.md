# UAO-FOUNDRY-EXPERIMENT-002-REPORT

**Semantic Relationships · Concept Manufacture · Visual Identity Bridge**
**Report page:** https://claude.ai/code/artifact/41fc9897-c92c-48c4-8fc1-ea2f3896dff2 · **Baseline page:** https://claude.ai/code/artifact/1458d27f-40c4-4066-8d0a-12fccba4a223
**Date:** 2026-09-05 · **Authority:** founder-authorised experimental continuation · **Status:** EXPERIMENTAL, returned for founder review
**Branch:** `experiment/002-relationships-visual-bridge` (from `3a15bd2`, PR #16 head; commits 613e1d6 → 04ea6df) · **Operator:** Claude (Fable 5.1); provider Claude Code 2.1.261 / sonnet
**Evidence root (gitignored):** `work/usi-people/{registry,runs,visual-evidence}`, `work/exp002/{phase3,phase45,visual,spriteforge-input,spriteforge-output,graph}` · **Registry at close:** 16 packages · 29 identities · 22 typed relationships · verification PASS · 0 unreconciled

## Verdict in one paragraph

Fifteen of sixteen acceptance criteria are met; one fails honestly. Typed, evidenced, fail-closed relationships now exist in the registry (22 edges over 29 identities), the six-person baseline was reused rather than rebuilt (19 identities re-observed, 45 registry sources reused, 75 assertions restated verbatim, 0 duplicates), concept identities for *human* and *computation* were manufactured and traversal from *computation* reaches Church, Turing, the Turing machine and the lambda calculus through registered edges, all six people carry licence-checked visual evidence and profiles, SpriteForge received six identity-derived packages and produced one same-profile group output. *Intellect* was manufactured three times and refused three times, the last time because the provider declared a required boundary question unresolved — the engine held the gate and the identity is not in the registry. The founder's expected edges *computation → Logic Theorist* and *human → intellect → Augmenting Human Intellect* were not evidenced by the provider and were not fabricated.

## Baseline (Phase 0)

- **Before:** six root packages, 19 identities, 6/6 admitted, PASS, 0 relationships; running state "3a15bd2 + adapter patch".
- **0.1 Patch canonicalised** as `613e1d6` (adapter: `$schema` stripped for the CLI, `--bare` explicit opt-out `UAO_FOUNDRY_CLAUDE_BARE=0`, CLI schema composed from the strict stage schemas, README/tests updated). Nothing in this experiment depends on an uncommitted change.
- **0.2 Reproduction** from a clean tree: `mvn verify` 162/162 at baseline (toolchain: Temurin 21 + Maven 3.9.9 provisioned in the session scratchpad, JRE-only workstation), adapter 12/12, demonstration fixtures reproduce 2 packages / 5 identities with 3 reused, six-person registry verifies (PASS, 19/6). No provider tokens spent.
- **0.3 Ostrom discrepancy = export truncation.** The Desktop PDF is a 12-page print of the artifact that stops mid-way through Beer's sources ("12 of 12"); Ostrom appears only in the summary table. The HTML artifact contains all six sections (`id="elinor-ostrom"` present) and her package `pkg-e5dd1d8570e4c45c` is intact. No repair needed; consistent with the known claude.ai PDF-print truncation.
- **GATE 0: PASS.**

## What changed upstream, and the governance posture taken (Phases 1–2)

ASA-canonical (local 8952b0c; `origin/asa/baseline-001`, not `origin/main`) now holds a governed **Relationship Type Registry facet** — edition 2026.2, ASA-SPEC-0006, adopted by D-040 (2026-08-31), digest `sha256:a0c6a69c…7059c`. Its admitted types are five `asa.core` meta-types and six `asa.cc0` Orchard-Zero claim types. **None of the predicates this experiment needs is admitted**, GitHub `17th2nd/ASA#29` is still open (last updated 2026-08-09), and CSS 2026.2 `uri_versioned` still rejects any non-`asa.core` type id (ASA-SPEC-0006 §10.3, AU-1).

Posture: the Foundry **consumes RTR-format editions and fails closed** on `RTR-EDITION-MISMATCH` / `RTR-DIGEST-MISMATCH` exactly as §9 requires (proved: the ASA 2026.2 facet loads in Java and its digest recomputes byte-for-byte against the Python kernel; tampered facets are refused whole). This experiment's predicates are bound only through a **Foundry-local proposed edition** `config/relationship-types/foundry-exp002.json` (registry_version 2026.902, provenance_anchor PROPOSED, five core meta-types carried verbatim + 17 proposed domain types, built with the ASA kernel's JCS hashing). Every record produced carries `ARCHITECTURAL-UNCERTAINTY AU-1` and `RTR-TYPE-NOT-ADMITTED` as **undetermined** diagnostics that nothing collapses. `config/upstream-authority-lock.json` records the review. Classification: **Constitutional** (authority not created here; the gap is named, not worked around).

## Relationships (Phase 1)

`--relationship-edition <facet>` — never inferred — makes stage 11 resolve each candidate's type in the edition, bind every participant to a persistent uid, and validate the instance under RTR §10.1 (V1–V6, V8). Anything else stays unresolved (`RTR-TYPE-UNKNOWN`, `PARTICIPANT_UNBOUND`, `URO-INSTANCE-INVALID` + §10.2 diagnostics) and keeps the package `EVIDENCE_INCOMPLETE`. A passing candidate becomes an **experimental typed relationship record**: `relationshipId urx-<12hex>` (pure function of type + identity-bearing bindings, so restatements resolve to the same id and accumulate as occurrences), participants with uids, evidence `sourceRefs`, `basis` EXPLICIT|INFERRED|UNSTATED, rendered `statement`, the edition digest, `stateVersion`, `certifying:false`. It is **not a CSS URO**: canonical UROs stay 0 and `URO_FAIL_CLOSED_TYPE_AUTHORITY` still passes. Records and a full copy of the edition travel in the package (meaning-bearing for the content digest); the verifier re-derives every record and every unresolved finding from the package's own candidates and demands byte-equality. Registry index gains `relationships` (only when present, so older registries verify unchanged), `relationshipNeighbourhood`, `graph`; console gains `relationships <ref>`, `graph`, `visual <ref>`. Tests: `RelationshipEditionTest` (10) + `CompletenessTest` (2); suite 175/175; adapter 13/13.

## Semantic graph (Phases 3, 5, 6)

**Phase 3 — reuse-only relate (0 provider calls).** `scripts/exp002/relate.py` restates each person package verbatim over `registry://` sources and adds the founder's proposed edges with the registered claims that evidence them. Result: 13 edges, 19 identities reused, 0 new, 36 registry sources reused, all `basis: EXPLICIT`, 0 semantic variants. Ostrom → *Governing the Commons* and Engelbart → the mouse patent (`created`) were added beyond the founder's list because the evidence was already on record.

**Phase 5 — live concept and neighbourhood runs (8 provider calls).** Edges the provider evidenced and the Foundry admitted: Simon, Newell, Shaw —co-created→ Logic Theorist (n-ary, EXPLICIT); Beer —created→ VSM; *Brain of the Firm* —about→ VSM; VSM —influenced→ Cybersyn; Turing —author-of→ *On Computable Numbers*; Church —created→ lambda calculus; Turing —created→ Turing machine; Turing, Church —co-created→ Church–Turing thesis; Church–Turing thesis —about→ Computation; *On Computable Numbers* —about→ Computation. **Not evidenced, therefore absent:** human ↔ intellect, intellect ↔ computation, computation ↔ Logic Theorist, computation ↔ cybernetics, VSM ↔ cybernetics (no *cybernetics* concept identity exists; Wiener's package holds the book only), *Augmenting Human Intellect* ↔ human/intellect. The one INFERRED edge the provider proposed (*Augmenting Human Intellect* —precursor-to→ NLS) sits in a refused package.

**Phase 6 — traversal** (`work/exp002/graph/GRAPH.md`, `graph.json`, `traversals.json`):
- Computation ←about— Church–Turing thesis ←co-created— Alan Turing —created→ Turing machine (depth 3); … ←co-created— Alonzo Church —created→ Lambda calculus.
- Viable System Model ←created— Stafford Beer; ←about— *Brain of the Firm*; —influenced→ Project Cybersyn.
- Logic Theorist ←co-created— Simon / Newell / Shaw; Simon —author-of→ *Administrative Behavior*.
- Human: no registered relationship leaves it. The founder's target path human → intellect → Augmenting Human Intellect → Engelbart does not exist in the registry.

## Reuse (Phase 4) — and the law the engine enforces

Reuse in this engine means **re-observation with identical meaning**: the semantic-variant digest covers `assertions` and `aliases`, so a live re-manufacture that re-words or adds claims about a registered identity is `SEMANTIC_VARIANT_DIVERGENCE` and automatic reuse is refused (the alternative, explicit admission, would mark the identity `MULTIPLE_UNRECONCILED_VARIANTS` and block all future automatic reuse). This happened on every live run that touched a registered identity — Logic Theorist, Viable System Model, and the intellect run that resolved to Engelbart's report — despite the adapter instructing verbatim restatement. **Enrichment of a registered identity therefore happens through relationships, not assertion growth.** `scripts/exp002/reconcile_reuse.py` restores registered identities verbatim from registry bytes, keeps the provider's genuinely new identities/claims/relationships, and re-manufactures with 0 provider calls: Logic Theorist → 2 reused + 2 new (Newell, Shaw) + 1 n-ary edge; VSM → 4 reused, 0 new, 2 new edges + 2 restated. The refused packages remain on disk as evidence of what the provider proposed.

Duplicates prevented: 3 (the three divergent live re-manufactures). Uid derivation (`uao-` = f(resolutionKey)) means the same key can never yield a second identity; the guard is on meaning, not on identity.

## New concept identities (Phase 4)

| Seed | Outcome | Identity |
|---|---|---|
| human | REGISTERED (1st attempt) | Human, `ext:wikidata:Q5`, 4 assertions; provider left "Human vs Homo sapiens (Q15978631)" as an open scope question rather than merging |
| computation | REGISTERED (3rd manufacture; 2 provider calls) | Computation, `ext:wikidata:Q12525525`, plus Turing, Church, Turing machine, lambda calculus, Church–Turing thesis, *On Computable Numbers* (7 identities, 6 edges). Refused twice: 1st for an unresolved scope-boundary question (concept vs discipline), 2nd for an **optional** precursors question left unresolved. The second refusal exposed an engine defect — the plan's `required` flag was counted and ignored — fixed in `04ea6df` (`CompletenessTest`); the provider bundle was then re-manufactured unchanged as a fixture. |
| intellect | **NOT REGISTERED** (3 provider calls) | 1st: lost to an operator error (jar rebuilt under a running JVM). 2nd: registry context biased the seed to Engelbart's report (refused: divergent + report's durable id unresolved). 3rd, concept-disambiguated: Intellect `ext:wikidata:Q353284`, 5 assertions, refused because the provider marked its required question "boundary with intelligence resolved" unresolved. The engine's refusal is correct; the identity exists only in `work/exp002/phase45/dist/…INTELLECT…evidence-incomplete…`. |

Founder wording "variable system model" was preserved as run-record context on the VSM run only; it was **not** added as an alias (aliases are in the variant digest and would have diverged the identity).

## Existing concept enrichment (Phase 4/5)

- **Logic Theorist** (`uao-cc3830d07f1e`): reused, now bound to Simon, Newell and Shaw by one n-ary `co-created` edge (3 creators, 1..8 cardinality). Occurrences: 3 packages, 1 state version.
- **Viable System Model** (`uao-ddeb0c84f330`): reused; new edges *Brain of the Firm* —about→ VSM and VSM —influenced→ Cybersyn beside the restated Beer edges. Occurrences: 3, 1 state version.

## Visual identity (Phases 7–8)

- **Acquisition** (`scripts/exp002/visual_acquire.py`): Wikimedia Commons API, licence read from `extmetadata` and recorded verbatim; PD / CC0 / CC BY / CC BY-SA only; NC/ND/fair-use and undersized files rejected; 23 references kept across the six (rejections logged with reasons). Two Wiener files were kept by the filter but **excluded from the profile** on inspection: a 1932 group photograph (subject not individually identifiable) and a photograph of the 1948 book's title page (evidence for the book, not the person). Wiener's clean portrait is a 2024 CC0 upload whose underlying photograph's provenance the declaration does not establish — recorded as an uncertainty.
- **Profiles** (`visual-profiles.json`): observed by the operating model from labelled contact sheets; every field is an observation about a dated image with temporal context (Engelbart spans 1968 and 2008; Beer is a single 1990 session; Ostrom one 2009 week). Monochrome sources leave colour unknown; nothing sensitive is inferred.
- **Store**: `work/usi-people/visual-evidence/<uid>/{references.json, profile.json, receipt.json, references/}` — a sibling of the registry like `runs/`; `certifying:false`; content-named bytes; receipts bind the identity's state versions and the registry index hash. Console `visual <ref>` reads it.

## SpriteForge (Phases 9–10)

- **Bridge** (`scripts/exp002/spriteforge_bridge.py`): six packages under `work/exp002/spriteforge-input/<slug>/` — `manifest.json` (uid, state versions, identity/profile/brief digests, reference count, generation constraints), `identity.json`, `visual-profile.json`, `provenance.json` (licence + attribution per reference, source packages), `references/`, `spriteforge-brief.json` (era, age profile, head/hair, facial hair, glasses, silhouette, clothing period, distinguishing features, allowable simplifications, must-not-invent, uncertain). No style field: rendering is SpriteForge's decision.
- **Output** (`scripts/exp002/spriteforge_render.py`, run with the ComfyUI venv, reusing SpriteForge's pose-guide/upload helpers read-only at repo head `01276b96`): one rendering profile `exp002-portrait-v1` for all six — Illustrious-XL-v2.0, no LoRA, SpriteForge south pose via xinsir OpenPose (0.7), IP-Adapter plus on the primary reference (0.55), fixed style block + brief-derived tags, dpmpp_2m/karras 28 steps cfg 5.5, seed 2002, 1024². Receipt `receipt-s2002.json` binds per subject: uid + state versions, profile digest, brief digest, reference sha256 + licence, graph sha256, ComfyUI 0.26.0, output sha256. Group sheet `group-exp002-s2002.png` (sha `311d27d0…`).
- **Honest read of the image:** Wiener (round dark glasses, white goatee, stocky, suit), Pearl (swept grey hair, beard, glasses) and Beer (chest-length white beard, pinstripe) carry their evidenced cues; Ostrom partially (silver bob, glasses, red-trimmed outfit, but the jacket colour drifted); **Simon acquired glasses the profile says he never wore** (negation in a positive prompt does not hold), and **Engelbart lost his thick white hair and tweed jacket** (rendered bald in a beige cardigan) — identity information did not survive that render. 4 of 6 recognisable from evidence; 2 defects. The mechanism (registered identity → evidence → brief → receipted render) works; fidelity is a SpriteForge-side problem to solve with its own tools (reference isolation, negative conditioning), not a Foundry one.
- Ops: the GPU was shared with the SpriteForge HD4 session's round G throughout; the background render client was killed by a low-memory policy after one subject and resumed in the foreground (`--skip-existing`).

## Failure / debt

1. **intellect** not registered (criterion 8 fails). Three provider calls; last refusal is a required question the provider itself left open.
2. **Expected neighbourhood edges absent** (human↔intellect, intellect↔computation, computation↔Logic Theorist, ↔cybernetics): the provider found no evidence linking the classical computability cluster to the cyberneticians, and no *cybernetics* concept identity exists. Criterion 12 passes on other paths only.
3. **Providers do not restate verbatim** even when told to (3/3 live runs that touched registered identities diverged). Reconciliation is an operator script, not a provider behaviour; the adapter could enforce it by rewriting reused identities from registry bytes itself — deliberately not done here (it would be a silent repair inside the provider boundary).
4. **Edition is PROPOSED only**; every relationship is `undetermined` under ASA-SPEC-0006 until a Council decision admits domain types (or an alias mechanism resolves AU-1). No canonical URO exists.
5. Earlier flagged keys unchanged: Beer's *Brain of the Firm* on a 1981 ISBN, Pearl's 1988 book on an edition ISBN, Engelbart's report/patent on `foundry:` keys. Fixing keys now would diverge identities that carry edges; needs an identity operation, not a re-manufacture.
6. `semantic_type` null on every identity (by design for `ext:` keys); the relationship layer does not use it.
7. Operator error: one provider call lost to a jar rebuild during a live run (`work/exp002/NOTES.md`).
8. All evidence lives under gitignored `work/`; nothing there is in version control. Archival is a founder decision.
9. This branch is built on the un-audited PR #16 head; PRs #15/#16 remain drafts awaiting independent audit and must not be merged on the strength of this experiment.

## Manufacturing economics (Phase 11)

| Measure | Value |
|---|---|
| Existing root identities reused (six persons) | 6 |
| Existing component identities reused | 13 |
| Existing assertions reused (restated verbatim) | 75 |
| Existing sources reused (`registry://` custody) | 45 |
| Existing identities enriched (≥1 typed edge) | 19 of the original 19 |
| New identities manufactured / admitted | 13 / 10 |
| New relationships (distinct ids) / occurrences | 22 / 24 |
| Duplicate identities prevented | 3 |
| Unresolved relationships in registered packages | 0 |
| Visual references acquired (kept) / profiles / bridge packages | 23 / 6 / 6 |
| Live provider calls (Experiment 002) | 8 (human 1, intellect 3, computation 2, LT 1, VSM 1) |
| Manufactures with 0 provider calls | 9 (6 relate, 2 reconcile, 1 re-fix) |
| Registry at close | 16 packages · 29 identities · 22 relationships · PASS |

Where the Foundry did not need to ask again: all of Phase 3, both reconciliations, the computation re-fix — 9 of 17 Experiment 002 manufactures.

## Acceptance test (Phase 12)

| # | Criterion | Result |
|---|---|---|
| 1 | baseline manufacturing remains valid | PASS (175/175, demo reproduces, registry PASS) |
| 2 | undocumented adapter patch resolved | PASS (613e1d6) |
| 3 | Ostrom export discrepancy resolved or explained | PASS (PDF print truncation) |
| 4 | ≥1 lawful typed relationship registered | PASS (22, under a declared, digest-pinned edition) |
| 5 | relationship provenance preserved | PASS (sourceRefs, basis, edition digest, stateVersion, verifier reconstruction) |
| 6 | relationship failure remains fail-closed | PASS (tests: unknown type, illegal role, unbound participant, tampered facet/record/edition) |
| 7 | human resolvable | PASS (`uao-b30c49ba9bd6`, Q5) |
| 8 | intellect resolvable | **FAIL** (manufactured, refused; not in registry) |
| 9 | computation resolvable | PASS (`uao-4106ac5a4c1b`, Q12525525) |
| 10 | Logic Theorist reused, not duplicated | PASS (same uid; divergent re-manufacture refused; reconciled) |
| 11 | VSM reused, not duplicated | PASS |
| 12 | concept traverses to a person/work | PASS via computation → Church–Turing thesis → Turing/Church and VSM → Beer; the founder's named paths do not exist |
| 13 | six people have visual evidence/profile | PASS (23 refs, 6 profiles, exclusions explained) |
| 14 | SpriteForge receives a machine-readable identity-derived package | PASS (6) |
| 15 | SpriteForge produces the group representation | PASS (produced; 2 of 6 subjects lose identity cues — recorded) |
| 16 | report distinguishes reuse from new manufacture | PASS (ledger above; run ledger in `work/exp002/graph/GRAPH.md`) |

**Experiment 002: 15/16 — PASS WITH ONE RECORDED FAILURE (intellect).** Stop condition honoured: no mass manufacture, no general ontology, no SpriteForge redesign, no course manufacture.

## Next experiment (reserved, not executed)

Experiment 003 — Cross-Domain Knowledge Reuse: Pass A "Certificate III in Electrotechnology", Pass B "Electric Vehicle Maintenance" on the same registry. What this experiment says it will need: (a) a provider that restates registered identities verbatim by construction (or an adapter-side restoration from registry bytes), otherwise every Pass B touch of a Pass A identity diverges; (b) the edition extended with the qualification/unit/competency predicates Pass A will need (contains, requires, part-of already exist in part); (c) a decision on whether `related-to` may be used for curriculum adjacency; (d) discovery keywords for Pass B pointing at Pass A's roots.

## Addendum (2026-09-05, after founder question) — written visual dataset

The founder asked that the USI carry the *entire written dataset* needed to reproduce a person's appearance — freckles, moles, scars, how the hair recedes — without necessarily packaging photographs. Done as follows:

- `visual-evidence/<uid>/description.json`: 11–16 observations per person (head/face shape, hairline and recession pattern, hair colour/texture by era, brows, eyes and lids, eyewear detail, nose, mouth, chin/jaw, ears, skin and pigmentation with locations, facial hair pattern, neck/build/posture, hands, accessories, clothing habit), each tied to the image ids that show it, its era, and a confidence; an explicit `notEvidenced` list per person (eye colour where sources are monochrome, scars nowhere resolvable, etc.). Observed from full frames and magnified face crops; nothing inferred beyond a cited image; medical/ethnic inference excluded by policy.
- Receipts and the store index carry the description digest; console `visual <ref>` prints the description.
- Bridge `--no-references`: text-only packages (`work/exp002/spriteforge-input/`) with `visual-description.json` as the reproduction dataset and references reduced to provenance pointers (Commons URL + sha256 + licence). Photo-bearing packages remain producible.
- Honest limits: Wiener and Beer are monochrome only; Simon's only photograph is a 1981 halftone plus paintings; Pearl's 2013 frames are low resolution; the only moles/age spots resolvable are Engelbart's (2008 colour) and Ostrom's (2009 colour), with one uncertain mark each on Simon and Beer. No scar is resolvable on anyone. The dataset says so rather than filling gaps.


## Addendum 2 (2026-09-05) — structured visual descriptor layer (SpriteForge contract)

SpriteForge reported that the written dataset rendered two of six recognisably from text alone: the prose was read through an anime caption vocabulary ("hooded lids" → a hood, "crown balding" → a crown, "large ears with long lobes" → animal ears). Founder decision: the durable record stays text; add STRUCTURE so a renderer's translator is a table, not a guess.

Contract: `spriteforge.visual-descriptor/v0.1` (`~/SpriteForge-App/schemas/v2/visual-descriptor.schema.json`, SpriteForge commit ed0ed41a). Seven descriptors coded FROM `description.json` and the kept references (Engelbart split 1968 / 2008), by `scripts/exp002/code_descriptors.py`; 47 enumerated fields each plus marks, clothing, accessories and props; every non-unknown value cites image ids; "unknown" wherever the prose and the enumeration do not meet, with the prose in `note`; ages from the registry's birth assertions. `scripts/exp002/validate_descriptors.py` checks JSON Schema 2020-12, enumRef membership, evidence ids against `references.json`, and evidence on every non-unknown value: 7/7 pass. Stored beside `description.json` (`descriptor.json` = latest epoch, `descriptor-<year>.json` earlier), digested into `receipt.json` and the store index; console `visual <ref>` prints them; bridge 0.2.0 `--no-references` packages carry them text-only with digests in `manifest.json`. `description.json` unchanged.

| uid | epoch | store path | descriptor digest |
|---|---|---|---|
| uao-11eb71766754 | 1958 | `work/usi-people/visual-evidence/uao-11eb71766754/descriptor.json` | `858f5edde30a74be0e39e2dff1bf848884032134fcfade7c0db8ed2d58754b48` |
| uao-b160552cb26e | 1981 | `work/usi-people/visual-evidence/uao-b160552cb26e/descriptor.json` | `b99b232878410672fc72ae965683cf0ac088fecd671dd8963e4b21f21403203d` |
| uao-bda651d022bf | 2013 | `work/usi-people/visual-evidence/uao-bda651d022bf/descriptor.json` | `d9179138dd441e6dc6c638399ffc91b4ad971cc6e71ee9514605df535e9292fc` |
| uao-830ce8cca652 | 1968 | `work/usi-people/visual-evidence/uao-830ce8cca652/descriptor-1968.json` | `bbcef137470f00b7ebfc7c1d7a9ed7cecbcdd81b91909066a6543d1b26a9bca5` |
| uao-830ce8cca652 | 2008 | `work/usi-people/visual-evidence/uao-830ce8cca652/descriptor.json` | `0faa5729fac7daa4664cd78de5a800aa0563705413e49fee6345d2284bb4679d` |
| uao-09f5f6b2f9fd | 1990 | `work/usi-people/visual-evidence/uao-09f5f6b2f9fd/descriptor.json` | `f634358fe6b32f4e5aa8186e831d7a673946a6d89ec4a0cb0823cba57135e2da` |
| uao-1fced4a70aa1 | 2009 | `work/usi-people/visual-evidence/uao-1fced4a70aa1/descriptor.json` | `1865ba693b42c2b7c8be4737987563b6f7d37229ddf2643eac7f188842778090` |

**Amendment (2026-09-05, founder):** Stafford Beer wore a **monocle in his right eye on a cord**, not spectacles. Corrected in `description.json` (eyewear observation and clothing note), `profile.json` (`apparent_glasses`), the descriptor (no monocle enumeration exists, so `eyewear.present` and `worn` are `unknown` with the monocle in `note`; `lens_shape=round`, `frame=metal` retained as supportable), the accessory entry, receipts and bridge manifest. New Beer descriptor digest: see `receipt.json` (`descriptorDigests.1990`); the table above is superseded for that row. Translator note for SpriteForge: the enumeration needs a `monocle` value under `eyewear.present`.

**Amendment 2 (2026-09-06, Foundry recode):** SpriteForge's schema rev of 2026-09-05 (commit `18df7bdd`) added `monocle` to `eyewear.present` and `right_eye`/`left_eye` to `eyewear.worn`, and staged a SpriteForge-side pending correction for Beer awaiting a Foundry recode. Beer is now coded `eyewear.present=monocle` (img-01–04) and `eyewear.worn=right_eye` (img-01/02); the accessory is `habitual=true` across all four frames. Unknown fields 12 → 10; validator 7/7 against the revised schema. New digest in `receipt.json` (`descriptorDigests.1990`, with `supersedes` = the amended digest above). **Evidence note for SpriteForge:** its pending-correction file cites img-02 and img-04 for the worn monocle and states that img-01 and img-03 show no eyewear. Re-inspection of the kept frames: the monocle is worn in the right eye in img-01 and img-02, and hangs unworn on its cord at the chest in img-03 and img-04. The Foundry record cites accordingly; the SpriteForge copy should be retired in favour of this descriptor rather than merged.
