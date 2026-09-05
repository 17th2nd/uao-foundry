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
import argparse, json, pathlib, re, subprocess

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
    cands = bundle["candidates"]; new_sources = {s["sourceId"]: s for s in bundle["sources"]}
    keep_claims, keep_evidence, notes, restated = [], [], [], []
    registered_cids = {}
    for c in cands["identities"]:
        ident = by_key.get(c["resolutionKey"])
        if not ident: continue
        occ = ident["occurrences"][0]; rp = registry / "packages" / occ["packageId"]
        rcands = load(rp / "candidate-identities.json"); rclaims = load(rp / "candidate-claims.json"); rev = load(rp / "candidate-evidence.json"); rsnap = load(rp / "provider-snapshot.json")
        rsources = load(rp / "source-registry.json")["sources"]
        rc = next(x for x in rcands if x["resolutionKey"] == c["resolutionKey"])
        # restate the identity verbatim (root flag is this bundle's decision, everything else is the registry's)
        c.update({k: rc[k] for k in rc if k not in ("candidateId", "root")})
        registered_cids[c["candidateId"]] = rc["candidateId"]
        for cl in rclaims:
            if cl["subjectIdentityRef"] != rc["candidateId"]: continue
            cl2 = dict(cl); cl2["subjectIdentityRef"] = c["candidateId"]; keep_claims.append(cl2)
            for ev in rev:
                if ev["supportsCandidateRef"] == cl["candidateId"]: keep_evidence.append(dict(ev))
        for s in rsources:
            if s["sourceId"] in new_sources: continue
            new_sources[s["sourceId"]] = {"sourceId": s["sourceId"], "locator": f"registry://{occ['packageId']}/{s['snapshotPath']}", "sourceClass": "foundry-registry",
                                          "retrievedAt": rsnap["fixedClock"], "license": "UAO-FOUNDRY-REGISTRY-SNAPSHOT", "content": "registry evidence; exact bytes restored by the Foundry"}
        restated.append(f"{c['label']} ({c['resolutionKey']}) restated verbatim from {occ['packageId']}")
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
    bundle["sourceStrategy"]["authorityNotes"] += [f"Reconciled from refused live package {refused_id} (SEMANTIC_VARIANT_DIVERGENCE): registered identities restated verbatim over registry:// sources; provider claims about them dropped ({len(dropped)}); new identities, claims, evidence and relationship candidates kept as produced. No additional provider call."] + restated
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
