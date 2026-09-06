"""Builder-level tests for scripts/enrich/build_enrichment_bundle.py on a synthetic registry (Codex pass C F-C3/F-C4):
review-required exit, operator attestation, exact-restatement refusal, unknown id refusal, paraphrase attestation note,
threshold bounds, source-id and claim-id collisions. No jar, no provider."""
import json, pathlib, subprocess, sys, tempfile, unittest
HERE = pathlib.Path(__file__).resolve().parent; BUILDER = HERE.parent / "build_enrichment_bundle.py"
V0 = "a" * 64; UID = "uao-000000000001"; KEY = "ext:wikidata:Q1"; PKG = "pkg-0000000000000001"; LIVE = "pkg-1111111111111111"
BIRTH = "Norbert Wiener was born on 26 November 1894 in Columbia, Missouri, USA."; DEATH = "Norbert Wiener died on 18 March 1964 in Stockholm, Sweden."; MIT = "Wiener was a professor of mathematics at MIT."

def write(p, obj): p.parent.mkdir(parents=True, exist_ok=True); p.write_text(json.dumps(obj, indent=1))

def make_registry(root, second_candidate=False):
    """A one-package registry holding Wiener with two assertions; with second_candidate=True the package also carries a
    second candidate for the SAME resolution key (the pipeline maps both to one UAO) with a third assertion."""
    reg = root / "registry"; pk = reg / "packages" / PKG
    write(reg / "index.json", {"registryVersion": "0.1.0", "packages": [PKG], "identityOperations": [], "identities": [
        {"uid": UID, "resolutionKey": KEY, "semanticVariantStatus": "SINGLE_VARIANT", "occurrences": [{"packageId": PKG, "canonicalPath": "packages/x", "semanticVariantDigest": V0, "stateVersion": "0.1.0"}]}]})
    idents = [{"candidateId": "cid-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}]
    claims = [{"candidateId": "clm-birth", "subjectIdentityRef": "cid-root", "statement": BIRTH, "channels": ["biography"], "sourceRefs": ["src-wikidata"]},
              {"candidateId": "clm-death", "subjectIdentityRef": "cid-root", "statement": DEATH, "channels": ["biography"], "sourceRefs": ["src-wikidata"]}]
    evidence = [{"evidenceId": "ev-birth", "sourceRef": "src-wikidata", "supportsCandidateRef": "clm-birth", "extract": "born 1894", "locatorWithinSource": "p1"},
                {"evidenceId": "ev-death", "sourceRef": "src-wikidata", "supportsCandidateRef": "clm-death", "extract": "died 1964", "locatorWithinSource": "p2"}]
    canonical_fi = {"canonical_label": "Norbert Wiener", "aliases": [], "external_identifiers": {"wikidata": "Q1"}, "resolution_key": KEY}
    if second_candidate:
        # a second candidate for the same key, as the pipeline groups them: canonicalisation keeps the first label (by
        # candidate id), unions aliases and external identifiers, and maps every claim to the one UAO
        idents.append({"candidateId": "cid-root-2", "root": False, "label": "N. Wiener", "aliases": ["Wiener, Norbert"], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1", "viaf": "12345"}, "sourceRefs": ["src-wikidata", "src-viaf"]})
        claims.append({"candidateId": "clm-mit", "subjectIdentityRef": "cid-root-2", "statement": MIT, "channels": ["biography"], "sourceRefs": ["src-wikidata"]})
        evidence.append({"evidenceId": "ev-mit", "sourceRef": "src-wikidata", "supportsCandidateRef": "clm-mit", "extract": "MIT", "locatorWithinSource": "p3"})
        evidence.append({"evidenceId": "ev-ident-2", "sourceRef": "src-viaf", "supportsCandidateRef": "cid-root-2", "extract": "VIAF record", "locatorWithinSource": "p4"})
        canonical_fi = {"canonical_label": "Norbert Wiener", "aliases": ["N. Wiener", "Wiener, Norbert"], "external_identifiers": {"viaf": "12345", "wikidata": "Q1"}, "resolution_key": KEY}
    write(pk / "candidate-identities.json", idents); write(pk / "candidate-claims.json", claims); write(pk / "candidate-evidence.json", evidence)
    write(pk / "canonical-identities.json", [{"uid": UID, "assertions": [{"statement": c["statement"], "channels": c["channels"], "epistemic_class": "ASSERTED"} for c in claims],
                                              "internal_state": {"foundry_identity": canonical_fi}, "relationship_references": [], "disclaimer": "synthetic", "lifecycle_status": "ACTIVE", "provenance": {}}])
    write(pk / "source-registry.json", {"sources": [{"sourceId": "src-wikidata", "locator": "https://www.wikidata.org/wiki/Q1", "snapshotPath": "source-corpus/src-wikidata.txt", "sha256": "1" * 64},
                                                     {"sourceId": "src-viaf", "locator": "https://viaf.org/12345", "snapshotPath": "source-corpus/src-viaf.txt", "sha256": "3" * 64}]})
    write(pk / "provider-snapshot.json", {"fixedClock": "2026-01-01T00:00:00Z"})
    return reg

def make_live(root, claims, sources=None, identities=None, evidence=None, relationships=None):
    live = root / "live"
    write(live / "manifest.json", {"packageId": LIVE})
    write(live / "provider-snapshot.json", {"identitySeed": "Norbert Wiener", "fixedClock": "2026-02-01T00:00:00Z", "knowledgeHorizon": "2026-02-01",
        "candidates": {"identities": identities or [{"candidateId": "cid-live-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}],
                       "claims": claims, "evidence": evidence or [], "relationships": relationships or []},
        "sources": sources or [{"sourceId": "src-wikidata", "locator": "https://www.wikidata.org/wiki/Q1", "sourceClass": "wikidata", "retrievedAt": "2026-02-01T00:00:00Z", "license": "CC0", "content": "LIVE BYTES"}],
        "sourceStrategy": {"authorityNotes": []}})
    return live

def claim(cid, statement, subj="cid-live-root", refs=("src-wikidata",)):
    return {"candidateId": cid, "subjectIdentityRef": subj, "statement": statement, "channels": ["biography"], "sourceRefs": list(refs)}

def run(reg, live, out, *extra):
    return subprocess.run([sys.executable, str(BUILDER), "--registry", str(reg), "--uid", UID, "--package", str(live), "--edition", "edition.json", "--out", str(out), *extra], capture_output=True, text=True)

def bundle(out): return json.loads(next(out.glob("*-enrichment.json")).read_text())

class Builder(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.root = pathlib.Path(self.tmp.name); self.reg = make_registry(self.root); self.out = self.root / "out"
    def tearDown(self): self.tmp.cleanup()

    def test_without_accept_the_tool_lists_and_refuses(self):
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948."), claim("clm-same", BIRTH)])
        r = run(self.reg, live, self.out)
        self.assertEqual(2, r.returncode, r.stderr); self.assertIn("review required", r.stderr)
        self.assertIn("[EXACT", r.stdout); self.assertIn("[candidate", r.stdout); self.assertFalse(self.out.exists(), "a refusal writes nothing, not even the output directory")

    def test_operator_attestation_admits_exactly_the_named_claims(self):
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948."), claim("clm-other", "Wiener taught at MIT."), claim("clm-same", BIRTH)])
        r = run(self.reg, live, self.out, "--accept", "clm-new")
        self.assertEqual(0, r.returncode, r.stderr); b = bundle(self.out)
        ids = [c["candidateId"] for c in b["candidates"]["claims"]]
        self.assertIn("clm-new", ids); self.assertNotIn("clm-other", ids); self.assertNotIn("clm-same", ids)
        self.assertEqual({BIRTH, DEATH, "Wiener published Cybernetics in 1948."}, {c["statement"] for c in b["candidates"]["claims"]})
        notes = " ".join(b["sourceStrategy"]["authorityNotes"])
        self.assertIn("operator attested clm-new", notes); self.assertIn("1 new assertion(s) admitted by operator attestation", notes); self.assertIn("2 provider claim(s) about the target not accepted", notes)

    def test_every_candidate_resolving_to_the_target_is_reviewed(self):
        # Codex pass D F-D2: two live candidates share the target's resolution key; both their claims must be listed and attestable.
        twin = {"candidateId": "cid-twin", "root": False, "label": "N. Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}
        root = {"candidateId": "cid-live-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}
        live = make_live(self.root, [claim("clm-a", "Wiener published Cybernetics in 1948."), claim("clm-b", "Wiener taught at MIT.", subj="cid-twin")], identities=[root, twin])
        listed = run(self.reg, live, self.out); self.assertEqual(2, listed.returncode); self.assertIn("clm-a", listed.stdout); self.assertIn("clm-b", listed.stdout)
        r = run(self.reg, live, self.out, "--accept", "clm-a", "--accept", "clm-b"); self.assertEqual(0, r.returncode, r.stderr); c = bundle(self.out)["candidates"]
        ids = [x["candidateId"] for x in c["claims"]]; self.assertIn("clm-a", ids); self.assertIn("clm-b", ids)
        self.assertEqual(1, sum(1 for x in c["claims"] if x["statement"] == BIRTH), "registered assertions restated once, not once per candidate")
        self.assertIn("2 new assertion(s) admitted", " ".join(bundle(self.out)["sourceStrategy"]["authorityNotes"]))
    def test_registry_assertions_from_every_registry_candidate_are_restated(self):
        # Codex pass E F-E2: the registry package holds two candidates for the key; all three registered assertions must be restated.
        self.tmp.cleanup(); self.tmp = tempfile.TemporaryDirectory(); self.root = pathlib.Path(self.tmp.name); self.reg = make_registry(self.root, second_candidate=True); self.out = self.root / "out"
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948.")])
        r = run(self.reg, live, self.out, "--accept", "clm-new"); self.assertEqual(0, r.returncode, r.stderr); c = bundle(self.out)["candidates"]
        self.assertEqual({BIRTH, DEATH, MIT, "Wiener published Cybernetics in 1948."}, {x["statement"] for x in c["claims"]})
        self.assertEqual(1, len({x["subjectIdentityRef"] for x in c["claims"]}), "all restated onto the one live candidate")
        self.assertEqual(3, sum(1 for e in c["evidence"] if e["evidenceId"].startswith("ev-") and e["supportsCandidateRef"] in {"clm-birth", "clm-death", "clm-mit"}))
        self.assertIn("2 registry candidate(s), 3 assertion(s), 4 evidence record(s)", " ".join(bundle(self.out)["sourceStrategy"]["authorityNotes"]))
        # Codex pass F F-F1/F-F2: names, identifiers and identity-level evidence follow the registry's canonical UAO
        restated = next(i for i in c["identities"] if i["resolutionKey"] == KEY)
        self.assertEqual("Norbert Wiener", restated["label"]); self.assertEqual(["N. Wiener", "Wiener, Norbert"], restated["aliases"])
        self.assertEqual({"viaf": "12345", "wikidata": "Q1"}, restated["externalIdentifiers"]); self.assertEqual(["src-wikidata", "src-viaf"], restated["sourceRefs"])
        ident_ev = next(e for e in c["evidence"] if e["evidenceId"] == "ev-ident-2"); self.assertEqual(restated["candidateId"], ident_ev["supportsCandidateRef"]); self.assertEqual("src-viaf", ident_ev["sourceRef"])

    def test_explicit_generated_namespace_source_keeps_its_own_references_through_the_builder(self):
        # Codex pass E F-E1: the live package carries src-wikidata (collides with the registry) AND an explicit
        # src-wikidata--live-<suffix>; each live claim must end up on the bytes it cited, in either declaration order.
        suffix = LIVE[-8:]; explicit_id = f"src-wikidata--live-{suffix}"
        for order in ("base-first", "explicit-first"):
            self.tmp.cleanup(); self.tmp = tempfile.TemporaryDirectory(); self.root = pathlib.Path(self.tmp.name); self.reg = make_registry(self.root); self.out = self.root / "out"
            base = {"sourceId": "src-wikidata", "locator": "https://live/base", "sourceClass": "web", "retrievedAt": "2026-02-01T00:00:00Z", "license": "CC0", "content": "BASE BYTES"}
            explicit = {"sourceId": explicit_id, "locator": "https://live/explicit", "sourceClass": "web", "retrievedAt": "2026-02-01T00:00:00Z", "license": "CC0", "content": "EXPLICIT BYTES"}
            live = make_live(self.root, [claim("clm-a", "Wiener published Cybernetics in 1948.", refs=("src-wikidata",)), claim("clm-b", "Wiener received the National Medal of Science in 1963.", refs=(explicit_id,))],
                             sources=[base, explicit] if order == "base-first" else [explicit, base])
            r = run(self.reg, live, self.out, "--accept", "clm-a", "--accept", "clm-b"); self.assertEqual(0, r.returncode, r.stderr); b = bundle(self.out)
            content = {s["sourceId"]: s.get("content") for s in b["sources"]}
            ref_a = next(x for x in b["candidates"]["claims"] if x["candidateId"] == "clm-a")["sourceRefs"]; ref_b = next(x for x in b["candidates"]["claims"] if x["candidateId"] == "clm-b")["sourceRefs"]
            self.assertEqual(["BASE BYTES"], [content[x] for x in ref_a], order); self.assertEqual(["EXPLICIT BYTES"], [content[x] for x in ref_b], order)
            self.assertTrue(next(s for s in b["sources"] if s["sourceId"] == "src-wikidata")["locator"].startswith("registry://"), order)

    def test_exact_restatement_cannot_be_attested(self):
        live = make_live(self.root, [claim("clm-same", BIRTH)])
        r = run(self.reg, live, self.out, "--accept", "clm-same")
        self.assertEqual(2, r.returncode); self.assertIn("exact restatements", r.stderr)

    def test_unknown_or_foreign_claim_ids_are_refused(self):
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948.")])
        r = run(self.reg, live, self.out, "--accept", "clm-nope")
        self.assertEqual(2, r.returncode); self.assertIn("not provider claims about the target", r.stderr)

    def test_a_flagged_paraphrase_is_admissible_only_by_name_and_the_note_says_so(self):
        live = make_live(self.root, [claim("clm-para", "Wiener was born in Columbia, Missouri, in 1894.")])
        listed = run(self.reg, live, self.out); self.assertEqual(2, listed.returncode); self.assertIn("[PARAPHRASE?", listed.stdout)
        r = run(self.reg, live, self.out, "--accept", "clm-para")
        self.assertEqual(0, r.returncode, r.stderr); notes = " ".join(bundle(self.out)["sourceStrategy"]["authorityNotes"])
        self.assertIn("operator attested clm-para", notes); self.assertIn("flagged PARAPHRASE?", notes)

    def test_threshold_outside_unit_interval_is_a_usage_error(self):
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948.")])
        for bad in ("1.5", "nan", "-1"):
            r = run(self.reg, live, self.out, "--accept", "clm-new", "--paraphrase-threshold", bad)
            self.assertEqual(2, r.returncode, bad); self.assertIn("paraphrase threshold", r.stderr)

    def test_colliding_live_source_is_renamed_with_every_reference(self):
        new_ident = {"candidateId": "cid-book", "root": False, "label": "Cybernetics (book)", "aliases": [], "resolutionKey": "ext:wikidata:Q2", "externalIdentifiers": {"wikidata": "Q2"}, "sourceRefs": ["src-wikidata"]}
        live = make_live(self.root, [claim("clm-new", "Wiener published Cybernetics in 1948."), claim("clm-book", "Cybernetics appeared in 1948.", subj="cid-book")],
                         identities=[{"candidateId": "cid-live-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}, new_ident],
                         evidence=[{"evidenceId": "ev-new", "sourceRef": "src-wikidata", "supportsCandidateRef": "clm-new", "extract": "1948", "locatorWithinSource": "p3"}],
                         relationships=[{"candidateId": "rel-author", "typeVersion": "asa:type:x/author-of@1", "participants": [], "identityLiterals": {}, "contextualBindings": [], "sourceRefs": ["src-wikidata"], "basis": "EXPLICIT"}])
        r = run(self.reg, live, self.out, "--accept", "clm-new"); self.assertEqual(0, r.returncode, r.stderr); b = bundle(self.out); c = b["candidates"]
        src_ids = {s["sourceId"] for s in b["sources"]}; self.assertEqual({"src-wikidata", "src-viaf", f"src-wikidata--live-{LIVE[-8:]}"}, src_ids)
        reg = next(s for s in b["sources"] if s["sourceId"] == "src-wikidata"); self.assertTrue(reg["locator"].startswith("registry://" + PKG), "the registered id keeps registry bytes")
        live_id = f"src-wikidata--live-{LIVE[-8:]}"
        self.assertEqual([live_id], next(x for x in c["claims"] if x["candidateId"] == "clm-new")["sourceRefs"])
        self.assertEqual([live_id], next(x for x in c["identities"] if x["candidateId"] == "cid-book")["sourceRefs"])
        self.assertEqual([live_id], c["relationships"][0]["sourceRefs"]); self.assertEqual(live_id, next(e for e in c["evidence"] if e["evidenceId"] == "ev-new")["sourceRef"])
        restated_root = next(x for x in c["identities"] if x["resolutionKey"] == KEY); self.assertEqual(["src-wikidata"], restated_root["sourceRefs"], "restated identity keeps registry provenance")
        for x in c["claims"]:
            if x["candidateId"] in ("clm-birth", "clm-death"): self.assertEqual(["src-wikidata"], x["sourceRefs"])

    def test_colliding_claim_ids_from_the_registry_are_renamed(self):
        live = make_live(self.root, [claim("clm-birth", "Wiener published Cybernetics in 1948.")])   # live reuses a registered claim id
        r = run(self.reg, live, self.out, "--accept", "clm-birth"); self.assertEqual(0, r.returncode, r.stderr); c = bundle(self.out)["candidates"]
        ids = [x["candidateId"] for x in c["claims"]]; self.assertEqual(len(ids), len(set(ids)))
        renamed = next(x for x in c["claims"] if x["statement"] == BIRTH); self.assertEqual("clm-birth--" + PKG, renamed["candidateId"])
        self.assertEqual(renamed["candidateId"], next(e for e in c["evidence"] if e["extract"] == "born 1894")["supportsCandidateRef"])

if __name__ == "__main__": unittest.main()
