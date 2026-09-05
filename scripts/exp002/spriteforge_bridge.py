#!/usr/bin/env python3
"""Experiment 002 Phase 9 — SpriteForge bridge contract.

Writes one bounded interchange package per identity so SpriteForge can consume a visual identity
without understanding the registry:

  spriteforge-input/<slug>/
    manifest.json          uid, identity digests (state versions), profile digest, reference count, constraints
    identity.json          the registry identity record (labels, key, external ids, occurrences) — what the subject IS
    visual-profile.json    the evidence-backed observations — what was SEEN, when
    provenance.json        every reference with licence/attribution + the registry packages behind the identity
    references/            the image bytes (content-named)
    spriteforge-brief.json SpriteForge-consumable characteristics; NO artistic style — style is SpriteForge's decision

The brief translates observations, never invents: an `uncertain` list carries everything the evidence
does not settle, and `allowable_simplifications` names what a sprite may drop.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, shutil, time

def sha(b): return hashlib.sha256(b).hexdigest()
def canon(o): return json.dumps(o, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()

def brief_from(profile: dict, ident: dict) -> dict:
    primary = profile["source_image_ids"][0]
    return {
        "briefVersion": "0.1.0",
        "subject": {"uid": profile["identity_uid"], "label": ident["canonicalLabels"][0], "resolutionKey": ident["resolutionKey"]},
        "represented_era": profile["temporal_context"],
        "apparent_age_profile": profile["approximate_age_in_reference"],
        "head_and_hair": profile["apparent_hair"],
        "facial_hair": profile["apparent_facial_hair"],
        "glasses": profile["apparent_glasses"],
        "face": profile["face_shape"],
        "silhouette_build": profile["apparent_build"],
        "clothing_period_style": profile["recurrent_clothing"],
        "distinguishing_features": profile["distinctive_visible_traits"],
        "allowable_simplifications": ["drop fine wrinkles and skin texture", "reduce clothing to silhouette + two dominant colours where colour is evidenced",
                                      "glasses may be a frame outline", "beard/hair as mass and colour, not strands"],
        "must_not_invent": ["eye colour unless evidenced", "colour of monochrome-only references", "height", "expression beyond what references show", "any attribute not listed here"],
        "uncertain": profile["uncertain_traits"],
        "reference_images": [{"imageId": r["imageId"], "sha256": r["sha256"], "licence": r["licence"], "attributionRequired": r["attributionRequired"], "primary": r["imageId"] == primary} for r in profile["provenance"]["references"]],
        "style_note": "None. Rendering style, palette and resolution are SpriteForge's choice; the identity describes the subject only.",
    }

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--registry", required=True); ap.add_argument("--out", required=True); a = ap.parse_args()
    registry = pathlib.Path(a.registry); store = registry.parent / "visual-evidence"; out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    index = json.load(open(registry / "index.json")); identities = {i["uid"]: i for i in index["identities"]}
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()); summary = []
    for entry in json.load(open(store / "index.json"))["identities"]:
        uid = entry["identityUid"]; ident = identities[uid]; d = store / uid
        profile = json.load(open(d / "profile.json")); refs = json.load(open(d / "references.json"))
        slug = "".join(c if c.isalnum() else "-" for c in ident["canonicalLabels"][0].lower()).strip("-")
        pkg = out / slug; (pkg / "references").mkdir(parents=True, exist_ok=True)
        used = [r for r in refs["references"] if r["usedInProfile"]]
        for r in used: shutil.copy(d / r["file"], pkg / "references" / pathlib.Path(r["file"]).name)
        identity = {"uid": uid, "resolutionKey": ident["resolutionKey"], "canonicalLabels": ident["canonicalLabels"], "aliases": ident["aliases"], "externalIdentifiers": ident["externalIdentifiers"],
                    "lifecycleState": ident["lifecycleState"], "stateVersions": ident["stateVersions"], "occurrences": ident["occurrences"], "semanticVariantStatus": ident["semanticVariantStatus"]}
        brief = brief_from(profile, ident)
        provenance = {"identityPackages": [o["packageId"] for o in ident["occurrences"]], "registryIndexHash": entry["registryIndexHash"], "visualReceipt": entry,
                      "references": [{k: r[k] for k in ("imageId", "file", "sha256", "licence", "licenceUrl", "attributionRequired", "artist", "credit", "commonsUrl", "originalUrl", "dateTimeOriginal", "reuseStatus")} for r in used],
                      "observedBy": profile["provenance"]["observedBy"], "observedAt": profile["provenance"]["observedAt"]}
        files = {"identity.json": identity, "visual-profile.json": profile, "provenance.json": provenance, "spriteforge-brief.json": brief}
        digests = {}
        for name, obj in files.items():
            b = json.dumps(obj, indent=2, ensure_ascii=False).encode() + b"\n"; (pkg / name).write_bytes(b); digests[name] = sha(canon(obj))
        manifest = {"manifestVersion": "0.1.0", "producer": "uao-foundry experiment 002 bridge", "producedAt": now, "uid": uid, "label": ident["canonicalLabels"][0],
                    "identityStateVersions": ident["stateVersions"], "identityDigest": sha(canon(identity)), "visualProfileVersion": profile["recordVersion"], "visualProfileDigest": digests["visual-profile.json"],
                    "briefDigest": digests["spriteforge-brief.json"], "referenceCount": len(used), "references": [{"file": f"references/{pathlib.Path(r['file']).name}", "sha256": r["sha256"], "licence": r["licence"]} for r in used],
                    "generationConstraints": {"style": "SpriteForge decides", "identityAuthority": "UAO Foundry registry (this manifest's uid + state versions)", "mustCite": "attribution for every CC BY / CC BY-SA reference used", "mustNotInvent": brief["must_not_invent"]},
                    "certifying": False, "status": "SPRITEFORGE_INPUT_EXPERIMENTAL"}
        (pkg / "manifest.json").write_bytes(json.dumps(manifest, indent=2, ensure_ascii=False).encode() + b"\n")
        summary.append({"slug": slug, "uid": uid, "label": manifest["label"], "references": len(used), "briefDigest": digests["spriteforge-brief.json"][:16]})
        print(slug, uid, "refs", len(used), "brief", digests["spriteforge-brief.json"][:12])
    (out / "index.json").write_text(json.dumps({"bridgeVersion": "0.1.0", "producedAt": now, "packages": summary}, indent=2) + "\n")

if __name__ == "__main__": main()
