#!/usr/bin/env python3
"""Build an ENRICHMENT bundle for one registered identity (ADR-0007), then optionally manufacture it and record ENRICH.

Input: a live manufacture package about the identity (its provider-snapshot carries new claims, evidence and sources;
the package itself may have been refused as SEMANTIC_VARIANT_DIVERGENCE — that is expected). Output: a fixture bundle in
which the identity's REGISTERED assertions are restated verbatim from registry:// bytes (so the registry can prove the
superset law) and the provider's NEW claims about it are appended with their evidence and sources. Every other
registered identity in the package is restated verbatim too (Experiment 002 reconcile law); new identities and
relationship candidates are kept. 0 provider calls.

Novelty is an OPERATOR ATTESTATION, not a computation (Codex pass C, F-C3): lexical overlap cannot prove that a
provider claim adds a fact. Without --accept the tool prints every provider claim about the target beside its
nearest registered assertion (overlap score, PARAPHRASE? flag) and exits 2; only claims named with
--accept <candidateId> enter the bundle, each recorded in authorityNotes with its overlap score. An exact restatement
of a registered assertion can never be accepted.

--run manufactures the bundle with --fixture into work/dist and then calls `RegistryApplication enrich`, which admits
the package and records the operation as one fail-closed step. Run from the Foundry checkout (schemas are cwd-relative).
"""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from bundle_lib import current_occurrence, split_paraphrases, SourcePool, registry_source, PARAPHRASE_THRESHOLD, validate_threshold, similarity, disambiguate_ids

def load(p): return json.loads(pathlib.Path(p).read_text())
def fail(msg): print(msg, file=sys.stderr); sys.exit(2)
def threshold_arg(value):
    try: return validate_threshold(value)
    except ValueError as ex: raise argparse.ArgumentTypeError(str(ex))
def slug(s): return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", required=True); ap.add_argument("--uid", required=True, help="registered identity to enrich")
    ap.add_argument("--package", required=True, help="live package (dist) whose provider-snapshot carries the new claims")
    ap.add_argument("--edition", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--classpath", default="target/uao-foundry-0.1.0.jar"); ap.add_argument("--run", action="store_true")
    ap.add_argument("--repository-commit", default="local"); ap.add_argument("--reason", default="LIFE_CHRONOLOGY")
    ap.add_argument("--justification", default=None); ap.add_argument("--recorded-at", default=None); ap.add_argument("--authority", default="operator")
    ap.add_argument("--paraphrase-threshold", type=threshold_arg, default=PARAPHRASE_THRESHOLD, help="content-token overlap in [0,1] at or above which a provider claim is FLAGGED as a likely paraphrase in the review listing (triage aid only)")
    ap.add_argument("--accept", action="append", default=[], metavar="CANDIDATE_ID", help="operator attestation: this provider claim about the target adds a fact not already asserted; repeatable. Without it nothing is built.")
    a = ap.parse_args()
    registry = pathlib.Path(a.registry).resolve(); pkg = pathlib.Path(a.package).resolve(); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    index = load(registry / "index.json"); by_key = {i["resolutionKey"]: i for i in index["identities"]}; by_uid = {i["uid"]: i for i in index["identities"]}
    target = by_uid.get(a.uid) or fail(f"{a.uid} is not a registered identity")
    if target["semanticVariantStatus"] != "SINGLE_VARIANT": fail(f"{a.uid} has unreconciled variants; reconcile before enriching")
    occ = current_occurrence(target); current = occ["semanticVariantDigest"]

    snap = load(pkg / "provider-snapshot.json"); live_id = load(pkg / "manifest.json")["packageId"]
    bundle = json.loads(json.dumps(snap)); cands = bundle["candidates"]
    claims, evidence, notes = [], [], []
    target_cid = None; registered_cids = {}
    # F-B6: one pool for every source id. Registry sources are installed first, each under its own package as origin, so a
    # live source that reuses a registered id is renamed (with its claim/evidence references) rather than substituting bytes
    # behind historical claims; registry packages that share an id with different bytes are separated the same way.
    pool = SourcePool()
    taken_claims = {cl["candidateId"] for cl in cands["claims"]}; taken_evidence = {ev["evidenceId"] for ev in cands["evidence"]}
    for c in cands["identities"]:
        ident = by_key.get(c["resolutionKey"])
        if not ident: continue
        o = occ if ident["uid"] == a.uid else current_occurrence(ident)
        rp = registry / "packages" / o["packageId"]
        rc = next(x for x in load(rp / "candidate-identities.json") if x["resolutionKey"] == c["resolutionKey"])
        c.update({k: rc[k] for k in rc if k not in ("candidateId", "root")}); registered_cids[c["candidateId"]] = ident["uid"]
        if ident["uid"] == a.uid: target_cid = c["candidateId"]
        rsnap = load(rp / "provider-snapshot.json"); pkg_claims, pkg_evidence = [], []
        for cl in load(rp / "candidate-claims.json"):
            if cl["subjectIdentityRef"] != rc["candidateId"]: continue
            cl2 = dict(cl); cl2["subjectIdentityRef"] = c["candidateId"]; pkg_claims.append(cl2)
            pkg_evidence += [dict(ev) for ev in load(rp / "candidate-evidence.json") if ev["supportsCandidateRef"] == cl["candidateId"]]
        for src in load(rp / "source-registry.json")["sources"]:
            pool.install(registry_source(src, o["packageId"], rsnap["fixedClock"]), o["packageId"], [c] + pkg_claims + pkg_evidence, sha256=src.get("sha256"))
        notes += disambiguate_ids(pkg_claims, pkg_evidence, taken_claims, taken_evidence, o["packageId"])
        claims += pkg_claims; evidence += pkg_evidence
        notes.append(f"{c['label']} ({c['resolutionKey']}) restated verbatim from {o['packageId']} (variant {o['semanticVariantDigest'][:12]}…)")
    if target_cid is None: fail(f"the live package proposes no candidate with {a.uid}'s resolution key ({target['resolutionKey']})")
    live_records = [c for c in cands["identities"] if c["candidateId"] not in registered_cids] + cands["claims"] + cands["evidence"] + cands.get("relationships", [])
    for src in bundle["sources"]:
        pool.install(src, f"live-{live_id[-8:]}", live_records)
    restated_texts = {cl["statement"] for cl in claims if cl["subjectIdentityRef"] == target_cid}
    # provider claims about the TARGET: every one is listed for review; only operator-named ones (--accept) are admitted (F-C3).
    # provider claims about other registered identities → dropped (reconcile law); about new identities → kept.
    about_target = [cl for cl in cands["claims"] if cl["subjectIdentityRef"] == target_cid]
    _, flagged = split_paraphrases(about_target, restated_texts, a.paraphrase_threshold); flagged_ids = {cl["candidateId"] for cl, _, _ in flagged}
    def nearest(stmt):
        best = max(restated_texts, key=lambda r: similarity(stmt, r), default=None); return best, (similarity(stmt, best) if best else 0.0)
    review = [(cl, *nearest(cl["statement"])) for cl in about_target]
    print(f"{a.uid}: {len(about_target)} provider claim(s) about the target vs {len(restated_texts)} registered assertion(s):")
    for cl, near, score in review:
        tag = "EXACT" if cl["statement"] in restated_texts else ("PARAPHRASE?" if cl["candidateId"] in flagged_ids else "candidate")
        print(f"   [{tag:11}] {cl['candidateId']} overlap {score:.2f}: {cl['statement'][:110]!r}\n{'':16}≈ {near[:110]!r}" if near else f"   [{tag:11}] {cl['candidateId']}: {cl['statement'][:110]!r}")
    if not a.accept: fail("review required: name each claim that adds a fact with --accept <candidateId> (exit 2)")
    by_id = {cl["candidateId"]: cl for cl in about_target}
    unknown = [x for x in a.accept if x not in by_id]
    if unknown: fail(f"--accept names claims that are not provider claims about the target: {unknown}")
    exact = [x for x in a.accept if by_id[x]["statement"] in restated_texts]
    if exact: fail(f"--accept names exact restatements of registered assertions; they add nothing: {exact}")
    accepted = [(cl, near, score) for cl, near, score in review if cl["candidateId"] in a.accept]
    not_accepted = [cl for cl in about_target if cl["candidateId"] not in a.accept]
    new_target = [cl for cl, _, _ in accepted]
    dropped = len(not_accepted) + sum(1 for cl in cands["claims"] if cl["subjectIdentityRef"] in registered_cids and cl["subjectIdentityRef"] != target_cid)
    new_target += [cl for cl in cands["claims"] if cl["subjectIdentityRef"] not in registered_cids]
    kept_ids = {cl["candidateId"] for cl in new_target} | {c["candidateId"] for c in cands["identities"]}
    cands["claims"] = new_target + claims
    cands["evidence"] = [ev for ev in cands["evidence"] if ev["supportsCandidateRef"] in kept_ids] + evidence
    seen = set()
    for cl in cands["claims"]:
        if cl["candidateId"] in seen: fail(f"duplicate claim id after disambiguation: {cl['candidateId']}")
        seen.add(cl["candidateId"])
    added = sum(1 for cl in new_target if cl["subjectIdentityRef"] == target_cid)
    if added == 0: fail("no accepted assertion about the target survived; nothing to enrich")
    bundle["sources"] = list(pool.sources.values())
    bundle["sourceStrategy"]["authorityNotes"] += [f"ENRICHMENT bundle (ADR-0007) for {a.uid} from live package {live_id}: registered assertions restated verbatim over registry:// sources; {added} new assertion(s) admitted by operator attestation (--accept), {len(not_accepted)} provider claim(s) about the target not accepted; other registered identities restated verbatim, provider claims about them dropped. Novelty is the operator's attestation, not a computation. No additional provider call."] + notes + pool.notes() + [f"operator attested {cl['candidateId']} adds a fact (overlap {score:.2f} with nearest registered assertion{', flagged PARAPHRASE? at threshold ' + str(a.paraphrase_threshold) if cl['candidateId'] in flagged_ids else ''})" for cl, _, score in accepted]
    path = out / f"{slug(bundle['identitySeed'])}-enrichment.json"; path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    print(f"{a.uid}: current variant {current[:12]}…, restated {len(restated_texts)} assertion(s), +{added} attested new, {len(not_accepted)} not accepted, {len(pool.renames)} source id(s) renamed, bundle → {path}")
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
