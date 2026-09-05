#!/usr/bin/env python3
"""Experiment 002 Phase 7/8 — write the visual-evidence store beside the registry.

<registry-parent>/visual-evidence/<uid>/
    references.json   the acquired reference images for this identity (licence, provenance, sha256)
    profile.json      the evidence-backed visual profile (observations about dated images)
    references/       the image bytes, content-named
    receipt.json      content addresses of the two records + the identity's registry state at write time

It is a SIBLING of the registry (like runs/ and staged-relationships/), never a child: visual evidence
is provenance-bearing observation about an identity, not identity. Nothing here changes a package,
a digest, or a publication decision, and every record carries `certifying: false`.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, shutil, time

def sha(b): return hashlib.sha256(b).hexdigest()
def canon(o): return json.dumps(o, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--registry", required=True); ap.add_argument("--visual", required=True)
    a = ap.parse_args()
    registry = pathlib.Path(a.registry); store = registry.parent / "visual-evidence"; store.mkdir(exist_ok=True)
    visual = pathlib.Path(a.visual)
    refs = json.load(open(visual / "visual-references.json")); profiles = json.load(open(visual / "visual-profiles.json"))
    index = json.load(open(registry / "index.json")); identities = {i["uid"]: i for i in index["identities"]}
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    written = []
    for prof in profiles["profiles"]:
        uid = prof["identity_uid"]; ident = identities[uid]
        person = refs["people"][prof["represented_person"]]
        d = store / uid; (d / "references").mkdir(parents=True, exist_ok=True)
        kept = []
        for k in person["kept"]:
            src = visual / k["file"]; b = src.read_bytes(); assert sha(b) == k["sha256"], k["file"]
            name = f"{k['sha256'][:16]}{src.suffix}"; (d / "references" / name).write_bytes(b)
            kept.append({**{x: k[x] for x in ("imageId", "commonsTitle", "commonsUrl", "originalUrl", "sha256", "bytes", "width", "height", "mime", "licence", "licenceUrl", "attributionRequired", "artist", "credit", "dateTimeOriginal", "description", "reuseStatus", "fetched")}, "file": f"references/{name}",
                         "usedInProfile": k["imageId"] in prof["source_image_ids"], "excludedReason": prof["excluded_references"].get(k["imageId"])})
        references = {"recordVersion": "0.1.0", "status": "VISUAL_EVIDENCE_NON_CANONICAL", "certifying": False, "identityUid": uid,
                      "resolutionKey": ident["resolutionKey"], "canonicalLabels": ident["canonicalLabels"], "acquisition": {"source": refs["source"], "policy": refs["policy"], "acquiredAt": refs["acquiredAt"]},
                      "references": kept, "rejectedAtAcquisition": person["rejected"]}
        profile = {"recordVersion": "0.1.0", "status": "VISUAL_PROFILE_NON_CANONICAL", "certifying": False, **prof,
                   "caveat": "Observations about dated photographs/paintings of the person as acquired; not durable properties of the identity, not identity facts, not evidence for any assertion in the registry."}
        rj = canon(references); pj = canon(profile)
        (d / "references.json").write_bytes(json.dumps(references, indent=2, ensure_ascii=False).encode() + b"\n")
        (d / "profile.json").write_bytes(json.dumps(profile, indent=2, ensure_ascii=False).encode() + b"\n")
        receipt = {"recordVersion": "0.1.0", "identityUid": uid, "writtenAt": now, "referencesDigest": sha(rj), "profileDigest": sha(pj),
                   "identityStateVersions": ident["stateVersions"], "identityOccurrences": [o["packageId"] for o in ident["occurrences"]],
                   "registryIndexHash": sha(canon(index)), "referenceCount": len(kept), "usedInProfile": len(prof["source_image_ids"])}
        (d / "receipt.json").write_bytes(json.dumps(receipt, indent=2).encode() + b"\n")
        written.append(receipt); print(uid, ident["canonicalLabels"][0], "refs", len(kept), "used", len(prof["source_image_ids"]), "profile", receipt["profileDigest"][:12])
    (store / "index.json").write_text(json.dumps({"storeVersion": "0.1.0", "status": "VISUAL_EVIDENCE_NON_CANONICAL", "certifying": False, "identities": written}, indent=2) + "\n")

if __name__ == "__main__": main()
