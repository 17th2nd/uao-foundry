#!/usr/bin/env python3
"""Experiment 002 — reconcile a refused live package into a reuse-consistent bundle (0 provider calls).

When a live manufacture re-states a REGISTERED identity with re-worded claims, ReuseAnalyzer refuses it
(SEMANTIC_VARIANT_DIVERGENCE): reuse means re-observation with identical meaning, and enrichment of a
registered identity belongs in relationships, not in a second assertion set. This script keeps the
manufacture's genuinely new material and restores every registered identity to its registered form:

  * candidate identities whose resolutionKey is registered → label/aliases/externalIdentifiers/claims/
    evidence replaced VERBATIM from the registry package that holds them, over registry:// sources;
  * everything else (new identities, their claims/evidence/sources, all relationship candidates) kept as
    the provider produced it;
  * sourceStrategy.authorityNotes records the transform and the refused package it came from.

Result: reused identities re-resolve with identical semantic-variant digests, new identities are new,
relationships bind both. The refused package stays on disk as evidence of what the provider proposed.
"""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "enrich"))
from bundle_lib import current_occurrence, SourcePool, restate_identity

def load(p): return json.loads(pathlib.Path(p).read_text())
def slug(s): return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--registry", required=True); ap.add_argument("--package", required=True)
    ap.add_argument("--edition", required=True); ap.add_argument("--out", required=True); ap.add_argument("--jar", default="target/uao-foundry-0.1.0.jar")
    ap.add_argument("--run", action="store_true"); ap.add_argument("--repository-commit", default="local"); a = ap.parse_args()
    registry = pathlib.Path(a.registry); pkg = pathlib.Path(a.package); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    index = load(registry / "index.json"); by_key = {i["resolutionKey"]: i for i in index["identities"]}
    snap = load(pkg / "provider-snapshot.json"); refused_id = load(pkg / "manifest.json")["packageId"]
    bundle = json.loads(json.dumps(snap))
    cands = bundle["candidates"]
    keep_claims, keep_evidence, notes, restated = [], [], [], []
    registered_cids = {}
    # F-B6: registry sources are installed first under their package of origin; a live source that reuses a registered id is
    # renamed (with its references) instead of standing in for registry bytes behind restated claims.
    pool = SourcePool(); restated_uids = set()
    taken_claims = {cl["candidateId"] for cl in cands["claims"]}; taken_evidence = {ev["evidenceId"] for ev in cands["evidence"]}
    for c in cands["identities"]:
        ident = by_key.get(c["resolutionKey"])
        if not ident: continue
        occ = current_occurrence(ident)   # F-B2/F-C1: the CURRENT variant of a single-variant identity, never occurrences[0]
        registered_cids[c["candidateId"]] = ident["uid"]
        # restate the identity verbatim, once per identity even when several live candidates share its key (F-E3)
        pkg_claims, pkg_evidence, pkg_notes = restate_identity(registry, ident, occ, c, pool, taken_claims, taken_evidence, restated_uids)
        keep_claims += pkg_claims; keep_evidence += pkg_evidence; notes += pkg_notes
        restated.append(pkg_notes[-1])
    live_records = [c for c in cands["identities"] if c["candidateId"] not in registered_cids] + cands["claims"] + cands["evidence"] + cands.get("relationships", [])
    pool.install_origin(f"live-{refused_id[-8:]}", bundle["sources"], live_records)
    new_sources = pool.sources
    # drop the provider's own claims/evidence about registered identities
    dropped = [cl for cl in cands["claims"] if cl["subjectIdentityRef"] in registered_cids]
    kept_new_claims = [cl for cl in cands["claims"] if cl["subjectIdentityRef"] not in registered_cids]
    kept_ids = {cl["candidateId"] for cl in kept_new_claims} | {c["candidateId"] for c in cands["identities"]}
    kept_new_evidence = [ev for ev in cands["evidence"] if ev["supportsCandidateRef"] in kept_ids]
    cands["claims"] = kept_new_claims + keep_claims
    cands["evidence"] = kept_new_evidence + keep_evidence
    # uniqueness of ids across merged sets
    seen = set()
    for cl in cands["claims"]:
        assert cl["candidateId"] not in seen, cl["candidateId"]; seen.add(cl["candidateId"])
    bundle["sources"] = list(new_sources.values())
    bundle["sourceStrategy"]["authorityNotes"] += [f"Reconciled from refused live package {refused_id} (SEMANTIC_VARIANT_DIVERGENCE): registered identities restated verbatim over registry:// sources; provider claims about them dropped ({len(dropped)}); new identities, claims, evidence and relationship candidates kept as produced. No additional provider call."] + pool.notes() + notes
    path = out / f"{slug(bundle['identitySeed'])}-reconciled.json"; path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    print(f"{bundle['identitySeed']}: restated {len(restated)}, dropped provider claims {len(dropped)}, new claims {len(kept_new_claims)}, relationships {len(cands['relationships'])}, sources {len(bundle['sources'])} → {path}")
    for n in restated: print("   ", n)
    if a.run:
        cmd = ["java", "-cp", a.jar, "org.seventeenthsecond.uaofoundry.console.OperatorConsole", "manufacture", bundle["identitySeed"], "--registry", str(registry), "--fixture", str(path),
               "--relationship-edition", a.edition, "--register", "--json", "--work-dir", str(out / "work"), "--dist-dir", str(out / "dist"), "--repository-commit", a.repository_commit,
               "--context", f"reconciled from refused package {refused_id}"]
        r = subprocess.run(cmd, capture_output=True, text=True); line = (r.stdout.strip().splitlines() or [""])[-1]
        try: rep = json.loads(line); print("   →", rep["publicationStatus"], rep["registryAdmission"], rep["counts"])
        except Exception: print("   → FAILED", (r.stderr or r.stdout)[-1200:])

if __name__ == "__main__": main()
