#!/usr/bin/env python3
"""Build an ENRICHMENT bundle for one registered identity (ADR-0007), then optionally manufacture it and record ENRICH.

Input: a live manufacture package about the identity (its provider-snapshot carries new claims, evidence and sources;
the package itself may have been refused as SEMANTIC_VARIANT_DIVERGENCE — that is expected). Output: a fixture bundle in
which the identity's REGISTERED assertions are restated verbatim from registry:// bytes (so the registry can prove the
superset law) and the provider's NEW claims about it are appended with their evidence and sources. Every other
registered identity in the package is restated verbatim too (Experiment 002 reconcile law); new identities and
relationship candidates are kept. 0 provider calls.

--run manufactures the bundle with --fixture into work/dist and then calls `RegistryApplication enrich`, which admits
the package and records the operation as one fail-closed step. Run from the Foundry checkout (schemas are cwd-relative).
"""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess, sys

def load(p): return json.loads(pathlib.Path(p).read_text())
def slug(s): return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", required=True); ap.add_argument("--uid", required=True, help="registered identity to enrich")
    ap.add_argument("--package", required=True, help="live package (dist) whose provider-snapshot carries the new claims")
    ap.add_argument("--edition", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--classpath", default="target/uao-foundry-0.1.0.jar"); ap.add_argument("--run", action="store_true")
    ap.add_argument("--repository-commit", default="local"); ap.add_argument("--reason", default="LIFE_CHRONOLOGY")
    ap.add_argument("--justification", default=None); ap.add_argument("--recorded-at", default=None); ap.add_argument("--authority", default="operator")
    a = ap.parse_args()
    registry = pathlib.Path(a.registry).resolve(); pkg = pathlib.Path(a.package).resolve(); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    index = load(registry / "index.json"); by_key = {i["resolutionKey"]: i for i in index["identities"]}; by_uid = {i["uid"]: i for i in index["identities"]}
    target = by_uid.get(a.uid) or sys.exit(f"{a.uid} is not a registered identity")
    if target["semanticVariantStatus"] != "SINGLE_VARIANT": sys.exit(f"{a.uid} has unreconciled variants; reconcile before enriching")
    current = target.get("currentVariant") or target["occurrences"][0]["semanticVariantDigest"]
    occ = next(o for o in target["occurrences"] if o["semanticVariantDigest"] == current)

    snap = load(pkg / "provider-snapshot.json"); live_id = load(pkg / "manifest.json")["packageId"]
    bundle = json.loads(json.dumps(snap)); cands = bundle["candidates"]; sources = {s["sourceId"]: s for s in bundle["sources"]}
    claims, evidence, notes = [], [], []
    target_cid = None; registered_cids = {}
    for c in cands["identities"]:
        ident = by_key.get(c["resolutionKey"])
        if not ident: continue
        o = occ if ident["uid"] == a.uid else next(x for x in ident["occurrences"] if x["semanticVariantDigest"] == (ident.get("currentVariant") or x["semanticVariantDigest"]))
        rp = registry / "packages" / o["packageId"]
        rc = next(x for x in load(rp / "candidate-identities.json") if x["resolutionKey"] == c["resolutionKey"])
        c.update({k: rc[k] for k in rc if k not in ("candidateId", "root")}); registered_cids[c["candidateId"]] = ident["uid"]
        if ident["uid"] == a.uid: target_cid = c["candidateId"]
        rsnap = load(rp / "provider-snapshot.json")
        for cl in load(rp / "candidate-claims.json"):
            if cl["subjectIdentityRef"] != rc["candidateId"]: continue
            cl2 = dict(cl); cl2["subjectIdentityRef"] = c["candidateId"]; claims.append(cl2)
            evidence += [dict(ev) for ev in load(rp / "candidate-evidence.json") if ev["supportsCandidateRef"] == cl["candidateId"]]
        for s in load(rp / "source-registry.json")["sources"]:
            sources.setdefault(s["sourceId"], {"sourceId": s["sourceId"], "locator": f"registry://{o['packageId']}/{s['snapshotPath']}", "sourceClass": "foundry-registry",
                                               "retrievedAt": rsnap["fixedClock"], "license": "UAO-FOUNDRY-REGISTRY-SNAPSHOT", "content": "registry evidence; exact bytes restored by the Foundry"})
        notes.append(f"{c['label']} ({c['resolutionKey']}) restated verbatim from {o['packageId']}")
    if target_cid is None: sys.exit(f"the live package proposes no candidate with {a.uid}'s resolution key ({target['resolutionKey']})")
    restated_texts = {cl["statement"] for cl in claims if cl["subjectIdentityRef"] == target_cid}
    # provider claims: about the TARGET → keep as NEW assertions (unless they duplicate a restated statement);
    # about other registered identities → drop (reconcile law); about new identities → keep.
    new_target, dropped = [], 0
    for cl in cands["claims"]:
        subj = cl["subjectIdentityRef"]
        if subj == target_cid:
            if cl["statement"] in restated_texts: dropped += 1; continue
            new_target.append(cl)
        elif subj in registered_cids: dropped += 1
        else: new_target.append(cl)
    kept_ids = {cl["candidateId"] for cl in new_target} | {c["candidateId"] for c in cands["identities"]}
    cands["claims"] = new_target + claims
    cands["evidence"] = [ev for ev in cands["evidence"] if ev["supportsCandidateRef"] in kept_ids] + evidence
    seen = set()
    for cl in cands["claims"]:
        assert cl["candidateId"] not in seen, ("duplicate claim id", cl["candidateId"]); seen.add(cl["candidateId"])
    added = sum(1 for cl in new_target if cl["subjectIdentityRef"] == target_cid)
    if added == 0: sys.exit("the live package adds no new assertion about the target; nothing to enrich")
    bundle["sources"] = list(sources.values())
    bundle["sourceStrategy"]["authorityNotes"] += [f"ENRICHMENT bundle (ADR-0007) for {a.uid} from live package {live_id}: registered assertions restated verbatim over registry:// sources, {added} new sourced assertion(s) appended; other registered identities restated verbatim, provider claims about them dropped ({dropped}). No additional provider call."] + notes
    path = out / f"{slug(bundle['identitySeed'])}-enrichment.json"; path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    print(f"{a.uid}: current variant {current[:12]}…, restated {len(restated_texts)} assertion(s), +{added} new, bundle → {path}")
    if not a.run: return
    dist = out / "dist"; work = out / "work"
    cmd = ["java", "-cp", a.classpath, "org.seventeenthsecond.uaofoundry.console.OperatorConsole", "manufacture", bundle["identitySeed"], "--fixture", str(path),
           "--relationship-edition", a.edition, "--json", "--work-dir", str(work), "--dist-dir", str(dist), "--repository-commit", a.repository_commit,
           "--registry", str(registry), "--enrich", a.uid, "--context", f"enrichment of {a.uid} from live package {live_id}"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    line = (r.stdout.strip().splitlines() or [""])[-1]
    try: rep = json.loads(line)
    except Exception: sys.exit("manufacture failed: " + (r.stderr or r.stdout)[-1500:])
    if rep.get("publicationStatus") not in ("EXPERIMENTAL", "REGISTERED_EXPERIMENTAL", "PUBLISHED"): sys.exit(f"manufactured package not publishable: {json.dumps(rep)[:800]}")
    pkg_path = rep["packagePath"]; print("   manufactured", rep["packageId"], rep["publicationStatus"])
    import datetime
    ecmd = ["java", "-cp", a.classpath, "org.seventeenthsecond.uaofoundry.registry.RegistryApplication", "enrich", pkg_path, "--registry", str(registry), "--subject", a.uid,
            "--reason", a.reason, "--justification", a.justification or f"Enrichment from live package {live_id}: {added} new sourced assertion(s).",
            "--authority", a.authority, "--recorded-at", a.recorded_at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")]
    e = subprocess.run(ecmd, capture_output=True, text=True)
    if e.returncode != 0: sys.exit("enrich refused: " + (e.stderr or e.stdout)[-1500:])
    res = json.loads(e.stdout.strip().splitlines()[-1]); print("   ENRICH recorded", res["operation"]["operationId"], "assertions added", res["assertionsAdded"])

if __name__ == "__main__": main()
