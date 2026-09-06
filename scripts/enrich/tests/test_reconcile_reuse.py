"""Reconcile-path tests for scripts/exp002/reconcile_reuse.py on the synthetic registry (Codex pass E F-E3, F-E1):
two live candidates sharing a registered key restate it once; every registry candidate's assertions are restated;
colliding live sources keep their own references. No jar (no --run), no provider."""
import json, pathlib, subprocess, sys, tempfile, unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from test_build_enrichment_bundle import make_registry, make_live, claim, BIRTH, DEATH, MIT, LIVE, KEY
RECONCILE = pathlib.Path(__file__).resolve().parents[2] / "exp002" / "reconcile_reuse.py"

def run(reg, live, out):
    return subprocess.run([sys.executable, str(RECONCILE), "--registry", str(reg), "--package", str(live), "--edition", "edition.json", "--out", str(out)], capture_output=True, text=True)

def bundle(out): return json.loads(next(out.glob("*-reconciled.json")).read_text())

class Reconcile(unittest.TestCase):
    def setUp(self): self.tmp = tempfile.TemporaryDirectory(); self.root = pathlib.Path(self.tmp.name); self.out = self.root / "out"
    def tearDown(self): self.tmp.cleanup()

    def test_two_live_candidates_for_one_registered_identity_restate_it_once(self):
        reg = make_registry(self.root)
        root = {"candidateId": "cid-live-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}
        twin = {"candidateId": "cid-twin", "root": False, "label": "N. Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}
        book = {"candidateId": "cid-book", "root": False, "label": "Cybernetics (book)", "aliases": [], "resolutionKey": "ext:wikidata:Q2", "externalIdentifiers": {"wikidata": "Q2"}, "sourceRefs": ["src-wikidata"]}
        live = make_live(self.root, [claim("clm-a", "reworded birth", subj="cid-live-root"), claim("clm-b", "reworded death", subj="cid-twin"), claim("clm-book", "Cybernetics appeared in 1948.", subj="cid-book")], identities=[root, twin, book])
        r = run(reg, live, self.out); self.assertEqual(0, r.returncode, r.stderr); c = bundle(self.out)["candidates"]
        statements = [x["statement"] for x in c["claims"]]
        self.assertEqual(1, statements.count(BIRTH)); self.assertEqual(1, statements.count(DEATH)); self.assertIn("Cybernetics appeared in 1948.", statements)
        self.assertNotIn("reworded birth", statements); self.assertNotIn("reworded death", statements)
        self.assertEqual(len(c["claims"]), len({x["candidateId"] for x in c["claims"]}))

    def test_every_registry_candidates_assertions_are_restated(self):
        reg = make_registry(self.root, second_candidate=True)
        live = make_live(self.root, [claim("clm-a", "reworded birth")])
        r = run(reg, live, self.out); self.assertEqual(0, r.returncode, r.stderr); c = bundle(self.out)["candidates"]
        self.assertEqual({BIRTH, DEATH, MIT}, {x["statement"] for x in c["claims"]})

    def test_colliding_live_sources_keep_their_own_references(self):
        reg = make_registry(self.root); suffix = LIVE[-8:]; explicit_id = f"src-wikidata--live-{suffix}"
        base = {"sourceId": "src-wikidata", "locator": "https://live/base", "sourceClass": "web", "retrievedAt": "2026-02-01T00:00:00Z", "license": "CC0", "content": "BASE BYTES"}
        explicit = {"sourceId": explicit_id, "locator": "https://live/explicit", "sourceClass": "web", "retrievedAt": "2026-02-01T00:00:00Z", "license": "CC0", "content": "EXPLICIT BYTES"}
        book = {"candidateId": "cid-book", "root": False, "label": "Cybernetics (book)", "aliases": [], "resolutionKey": "ext:wikidata:Q2", "externalIdentifiers": {"wikidata": "Q2"}, "sourceRefs": ["src-wikidata"]}
        root = {"candidateId": "cid-live-root", "root": True, "label": "Norbert Wiener", "aliases": [], "resolutionKey": KEY, "externalIdentifiers": {"wikidata": "Q1"}, "sourceRefs": ["src-wikidata"]}
        live = make_live(self.root, [claim("clm-1", "Cybernetics appeared in 1948.", subj="cid-book", refs=("src-wikidata",)), claim("clm-2", "It was published by MIT Press.", subj="cid-book", refs=(explicit_id,))],
                         sources=[base, explicit], identities=[root, book])
        r = run(reg, live, self.out); self.assertEqual(0, r.returncode, r.stderr); b = bundle(self.out); content = {s["sourceId"]: s.get("content") for s in b["sources"]}
        c1 = next(x for x in b["candidates"]["claims"] if x["candidateId"] == "clm-1"); c2 = next(x for x in b["candidates"]["claims"] if x["candidateId"] == "clm-2")
        self.assertEqual(["BASE BYTES"], [content[x] for x in c1["sourceRefs"]]); self.assertEqual(["EXPLICIT BYTES"], [content[x] for x in c2["sourceRefs"]])
        self.assertEqual(["BASE BYTES"], [content[x] for x in next(i for i in b["candidates"]["identities"] if i["candidateId"] == "cid-book")["sourceRefs"]])
        self.assertTrue(next(s for s in b["sources"] if s["sourceId"] == "src-wikidata")["locator"].startswith("registry://"))

if __name__ == "__main__": unittest.main()
