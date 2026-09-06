"""Unit tests for scripts/enrich/bundle_lib.py (Codex pass-B findings F-B2, F-B5, F-B6). Run: python3 -m unittest discover -s scripts/enrich/tests"""
import pathlib, sys, unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from bundle_lib import current_occurrence, split_paraphrases, similarity, SourcePool

V0, V1 = "a" * 64, "b" * 64

class CurrentOccurrence(unittest.TestCase):
    def test_selects_current_variant_not_first_occurrence(self):
        # pkg-0 sorts first but carries the SUPERSEDED variant; the index names v1 current.
        ident = {"uid": "uao-1", "currentVariant": V1, "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}, {"packageId": "pkg-1", "semanticVariantDigest": V1}]}
        self.assertEqual("pkg-1", current_occurrence(ident)["packageId"])
    def test_single_variant_without_pointer_is_any_occurrence(self):
        ident = {"uid": "uao-1", "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}, {"packageId": "pkg-1", "semanticVariantDigest": V0}]}
        self.assertEqual("pkg-0", current_occurrence(ident)["packageId"])
    def test_unreconciled_variants_fail_closed(self):
        ident = {"uid": "uao-1", "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}, {"packageId": "pkg-1", "semanticVariantDigest": V1}]}
        with self.assertRaises(ValueError): current_occurrence(ident)
    def test_dangling_pointer_fails_closed(self):
        ident = {"uid": "uao-1", "currentVariant": V1, "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}]}
        with self.assertRaises(ValueError): current_occurrence(ident)

class Paraphrases(unittest.TestCase):
    RESTATED = ["Norbert Wiener was born on 26 November 1894 in Columbia, Missouri.", "Wiener published Cybernetics in 1948."]
    def test_exact_and_case_variants_are_paraphrases(self):
        claims = [{"candidateId": "c1", "statement": "norbert wiener was born on 26 november 1894 in columbia, missouri"}]
        genuine, para = split_paraphrases(claims, self.RESTATED)
        self.assertEqual([], genuine); self.assertEqual(1.0, para[0][2])
    def test_reworded_prior_assertion_is_a_paraphrase(self):
        claims = [{"candidateId": "c1", "statement": "Wiener was born in Columbia, Missouri, in 1894."}]
        genuine, para = split_paraphrases(claims, self.RESTATED)
        self.assertEqual([], genuine); self.assertEqual(self.RESTATED[0], para[0][1])
    def test_genuinely_new_claim_survives(self):
        claims = [{"candidateId": "c1", "statement": "Wiener died in Stockholm on 18 March 1964."}]
        genuine, para = split_paraphrases(claims, self.RESTATED)
        self.assertEqual(1, len(genuine)); self.assertEqual([], para)
    def test_similarity_is_symmetric_and_bounded(self):
        a, b = self.RESTATED
        self.assertEqual(similarity(a, b), similarity(b, a)); self.assertTrue(0.0 <= similarity(a, b) <= 1.0)

class Sources(unittest.TestCase):
    def test_live_source_colliding_with_registry_source_is_renamed_with_refs(self):
        pool = SourcePool()
        live_claims = [{"candidateId": "clm-1", "sourceRefs": ["src-x", "src-y"]}]; live_ev = [{"evidenceId": "ev-1", "sourceRef": "src-x"}]
        reg_claims = [{"candidateId": "clm-r", "sourceRefs": ["src-x"]}]; reg_ev = [{"evidenceId": "ev-r", "sourceRef": "src-x"}]
        # registry first (historical provenance), live second → live is renamed, registry refs untouched
        pool.install({"sourceId": "src-x", "locator": "registry://pkg-a/x"}, "pkg-a", reg_claims, reg_ev, sha256="1" * 64)
        new_id = pool.install({"sourceId": "src-x", "locator": "https://live"}, "live-pkg-b", live_claims, live_ev)
        self.assertEqual("src-x--live-pkg-b", new_id)
        self.assertEqual(["src-x--live-pkg-b", "src-y"], live_claims[0]["sourceRefs"]); self.assertEqual("src-x--live-pkg-b", live_ev[0]["sourceRef"])
        self.assertEqual(["src-x"], reg_claims[0]["sourceRefs"]); self.assertEqual("src-x", reg_ev[0]["sourceRef"])
        self.assertEqual("registry://pkg-a/x", pool.sources["src-x"]["locator"]); self.assertEqual(1, len(pool.renames))
    def test_registry_source_colliding_with_live_source_is_renamed_not_dropped(self):
        pool = SourcePool()
        live_claims = [{"candidateId": "clm-1", "sourceRefs": ["src-x"]}]; reg_claims = [{"candidateId": "clm-r", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x", "locator": "https://live"}, "live-pkg-b", live_claims, [])
        new_id = pool.install({"sourceId": "src-x", "locator": "registry://pkg-a/x"}, "pkg-a", reg_claims, [], sha256="1" * 64)
        self.assertEqual("src-x--pkg-a", new_id); self.assertEqual(["src-x--pkg-a"], reg_claims[0]["sourceRefs"]); self.assertEqual(["src-x"], live_claims[0]["sourceRefs"])
        self.assertEqual(2, len(pool.sources))
    def test_identical_registry_bytes_are_shared(self):
        pool = SourcePool(); c1 = [{"candidateId": "a", "sourceRefs": ["src-x"]}]; c2 = [{"candidateId": "b", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x"}, "pkg-a", c1, [], sha256="1" * 64); sid = pool.install({"sourceId": "src-x"}, "pkg-b", c2, [], sha256="1" * 64)
        self.assertEqual("src-x", sid); self.assertEqual(1, len(pool.sources)); self.assertEqual([], pool.renames)
    def test_different_registry_bytes_are_not_shared(self):
        pool = SourcePool(); c2 = [{"candidateId": "b", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x"}, "pkg-a", [], [], sha256="1" * 64); sid = pool.install({"sourceId": "src-x"}, "pkg-b", c2, [], sha256="2" * 64)
        self.assertEqual("src-x--pkg-b", sid); self.assertEqual(["src-x--pkg-b"], c2[0]["sourceRefs"])
    def test_same_origin_reinstall_is_idempotent(self):
        pool = SourcePool(); pool.install({"sourceId": "src-x"}, "pkg-a", [], []); pool.install({"sourceId": "src-x"}, "pkg-a", [], [])
        self.assertEqual(1, len(pool.sources)); self.assertEqual([], pool.renames)

if __name__ == "__main__": unittest.main()
