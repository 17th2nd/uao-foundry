"""Unit tests for scripts/enrich/bundle_lib.py (Codex pass-B findings F-B2, F-B5, F-B6). Run: python3 -m unittest discover -s scripts/enrich/tests"""
import pathlib, sys, unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from bundle_lib import current_occurrence, split_paraphrases, similarity, SourcePool, validate_threshold, disambiguate_ids

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
    def test_explicit_non_single_status_fails_closed_even_with_a_pointer(self):
        # Codex pass C F-C1: the registry's own verdict on the identity outranks a plausible-looking pointer.
        ident = {"uid": "uao-1", "semanticVariantStatus": "MULTIPLE_UNRECONCILED_VARIANTS", "currentVariant": V1,
                 "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}, {"packageId": "pkg-1", "semanticVariantDigest": V1}]}
        with self.assertRaises(ValueError): current_occurrence(ident)
    def test_single_variant_status_with_pointer_is_accepted(self):
        ident = {"uid": "uao-1", "semanticVariantStatus": "SINGLE_VARIANT", "currentVariant": V1,
                 "occurrences": [{"packageId": "pkg-0", "semanticVariantDigest": V0}, {"packageId": "pkg-1", "semanticVariantDigest": V1}]}
        self.assertEqual("pkg-1", current_occurrence(ident)["packageId"])
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

class Threshold(unittest.TestCase):
    def test_only_finite_scores_in_unit_interval(self):
        self.assertEqual(0.5, validate_threshold("0.5")); self.assertEqual(1.0, validate_threshold(1))
        for bad in ("1.5", "-0.1", "nan", "inf", "abc", None): 
            with self.assertRaises(ValueError, msg=bad): validate_threshold(bad)

class ClaimIds(unittest.TestCase):
    def test_copied_claim_and_evidence_ids_are_renamed_on_collision(self):
        live_claims = {"clm-location"}; live_ev = {"ev-1"}
        pkg_claims = [{"candidateId": "clm-location", "statement": "x"}, {"candidateId": "clm-other", "statement": "y"}]
        pkg_ev = [{"evidenceId": "ev-1", "supportsCandidateRef": "clm-location"}, {"evidenceId": "ev-2", "supportsCandidateRef": "clm-other"}]
        notes = disambiguate_ids(pkg_claims, pkg_ev, live_claims, live_ev, "pkg-a")
        self.assertEqual("clm-location--pkg-a", pkg_claims[0]["candidateId"]); self.assertEqual("clm-other", pkg_claims[1]["candidateId"])
        self.assertEqual("clm-location--pkg-a", pkg_ev[0]["supportsCandidateRef"]); self.assertEqual("ev-1--pkg-a", pkg_ev[0]["evidenceId"])
        self.assertEqual({"clm-location", "clm-location--pkg-a", "clm-other"}, live_claims); self.assertEqual(2, len(notes))
    def test_no_collision_no_change(self):
        pkg_claims = [{"candidateId": "clm-a"}]; pkg_ev = [{"evidenceId": "ev-a", "supportsCandidateRef": "clm-a"}]
        self.assertEqual([], disambiguate_ids(pkg_claims, pkg_ev, set(), set(), "pkg-a")); self.assertEqual("clm-a", pkg_claims[0]["candidateId"])

class Sources(unittest.TestCase):
    def test_identity_and_relationship_references_are_rewritten_too(self):
        # Codex pass C F-C4: sourceRefs live on identities and relationships as well as claims/evidence.
        pool = SourcePool()
        pool.install({"sourceId": "src-x", "locator": "registry://pkg-a/x"}, "pkg-a", [], sha256="1" * 64)
        ident = {"candidateId": "cid-new", "sourceRefs": ["src-x"]}; rel = {"candidateId": "rel-1", "sourceRefs": ["src-x", "src-z"]}
        claim = {"candidateId": "clm-1", "sourceRefs": ["src-x"]}; ev = {"evidenceId": "ev-1", "sourceRef": "src-x"}
        pool.install({"sourceId": "src-x", "locator": "https://live"}, "live-b", [ident, claim, ev, rel])
        for r in (ident, claim, rel): self.assertNotIn("src-x", r["sourceRefs"], r); self.assertIn("src-x--live-b", r["sourceRefs"])
        self.assertEqual("src-x--live-b", ev["sourceRef"]); self.assertEqual(["src-x--live-b", "src-z"], rel["sourceRefs"])
    def test_reinstalling_an_already_renamed_origin_is_idempotent(self):
        # Codex pass C F-C4 (second part), reproduced on Macleay reconcile runs 63/64: two identities restated from the
        # same registry package install the same colliding source twice.
        pool = SourcePool()
        pool.install({"sourceId": "src-x"}, "pkg-a", [], sha256="1" * 64)
        r1 = [{"candidateId": "c1", "sourceRefs": ["src-x"]}]; r2 = [{"candidateId": "c2", "sourceRefs": ["src-x"]}]
        first = pool.install({"sourceId": "src-x"}, "pkg-b", r1, sha256="2" * 64); second = pool.install({"sourceId": "src-x"}, "pkg-b", r2, sha256="2" * 64)
        self.assertEqual(first, second); self.assertEqual(["src-x--pkg-b"], r2[0]["sourceRefs"]); self.assertEqual(1, len(pool.renames)); self.assertEqual(2, len(pool.sources))
    def test_a_renamed_id_baked_into_a_registered_package_is_shared_on_equal_bytes(self):
        # Macleay reconcile run 63: pkg-062b (registered from an earlier reconciliation) carries src-x--pkg-a with pkg-a's bytes;
        # restating pkg-a itself later must land on that id, not fail.
        pool = SourcePool(); pool.install({"sourceId": "src-x"}, "pkg-062b", [], sha256="9" * 64)
        pool.install({"sourceId": "src-x--pkg-a"}, "pkg-062b", [], sha256="1" * 64)
        r = [{"candidateId": "c", "sourceRefs": ["src-x"]}]
        self.assertEqual("src-x--pkg-a", pool.install({"sourceId": "src-x"}, "pkg-a", r, sha256="1" * 64)); self.assertEqual(["src-x--pkg-a"], r[0]["sourceRefs"]); self.assertEqual(2, len(pool.sources))
    def test_a_renamed_id_taken_with_different_bytes_gets_a_further_suffix(self):
        pool = SourcePool(); pool.install({"sourceId": "src-x"}, "pkg-c", [], sha256="9" * 64); pool.install({"sourceId": "src-x--pkg-b"}, "pkg-c", [], sha256="8" * 64)
        r = [{"candidateId": "c", "sourceRefs": ["src-x"]}]
        self.assertEqual("src-x--pkg-b-2", pool.install({"sourceId": "src-x"}, "pkg-b", r, sha256="1" * 64)); self.assertEqual(["src-x--pkg-b-2"], r[0]["sourceRefs"])
        self.assertEqual("src-x--pkg-b-2", pool.install({"sourceId": "src-x"}, "pkg-b", [], sha256="1" * 64), "idempotent on the suffixed id too")
    def test_live_source_colliding_with_registry_source_is_renamed_with_refs(self):
        pool = SourcePool()
        live_claims = [{"candidateId": "clm-1", "sourceRefs": ["src-x", "src-y"]}]; live_ev = [{"evidenceId": "ev-1", "sourceRef": "src-x"}]
        reg_claims = [{"candidateId": "clm-r", "sourceRefs": ["src-x"]}]; reg_ev = [{"evidenceId": "ev-r", "sourceRef": "src-x"}]
        # registry first (historical provenance), live second → live is renamed, registry refs untouched
        pool.install({"sourceId": "src-x", "locator": "registry://pkg-a/x"}, "pkg-a", reg_claims + reg_ev, sha256="1" * 64)
        new_id = pool.install({"sourceId": "src-x", "locator": "https://live"}, "live-pkg-b", live_claims + live_ev)
        self.assertEqual("src-x--live-pkg-b", new_id)
        self.assertEqual(["src-x--live-pkg-b", "src-y"], live_claims[0]["sourceRefs"]); self.assertEqual("src-x--live-pkg-b", live_ev[0]["sourceRef"])
        self.assertEqual(["src-x"], reg_claims[0]["sourceRefs"]); self.assertEqual("src-x", reg_ev[0]["sourceRef"])
        self.assertEqual("registry://pkg-a/x", pool.sources["src-x"]["locator"]); self.assertEqual(1, len(pool.renames))
    def test_registry_source_colliding_with_live_source_is_renamed_not_dropped(self):
        pool = SourcePool()
        live_claims = [{"candidateId": "clm-1", "sourceRefs": ["src-x"]}]; reg_claims = [{"candidateId": "clm-r", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x", "locator": "https://live"}, "live-pkg-b", live_claims)
        new_id = pool.install({"sourceId": "src-x", "locator": "registry://pkg-a/x"}, "pkg-a", reg_claims, sha256="1" * 64)
        self.assertEqual("src-x--pkg-a", new_id); self.assertEqual(["src-x--pkg-a"], reg_claims[0]["sourceRefs"]); self.assertEqual(["src-x"], live_claims[0]["sourceRefs"])
        self.assertEqual(2, len(pool.sources))
    def test_identical_registry_bytes_are_shared(self):
        pool = SourcePool(); c1 = [{"candidateId": "a", "sourceRefs": ["src-x"]}]; c2 = [{"candidateId": "b", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x"}, "pkg-a", c1, sha256="1" * 64); sid = pool.install({"sourceId": "src-x"}, "pkg-b", c2, sha256="1" * 64)
        self.assertEqual("src-x", sid); self.assertEqual(1, len(pool.sources)); self.assertEqual([], pool.renames)
    def test_different_registry_bytes_are_not_shared(self):
        pool = SourcePool(); c2 = [{"candidateId": "b", "sourceRefs": ["src-x"]}]
        pool.install({"sourceId": "src-x"}, "pkg-a", [], sha256="1" * 64); sid = pool.install({"sourceId": "src-x"}, "pkg-b", c2, sha256="2" * 64)
        self.assertEqual("src-x--pkg-b", sid); self.assertEqual(["src-x--pkg-b"], c2[0]["sourceRefs"])
    def test_same_origin_reinstall_is_idempotent(self):
        pool = SourcePool(); pool.install({"sourceId": "src-x"}, "pkg-a", []); pool.install({"sourceId": "src-x"}, "pkg-a", [])
        self.assertEqual(1, len(pool.sources)); self.assertEqual([], pool.renames)

if __name__ == "__main__": unittest.main()
