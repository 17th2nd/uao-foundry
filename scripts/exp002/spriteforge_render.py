#!/usr/bin/env python3
"""Experiment 002 Phase 10 — SpriteForge experimental group output from bridge packages.

Run with the ComfyUI venv python. One common rendering profile for every subject so differences come
from identity information, not settings:

  profile exp002-portrait-v1: Illustrious-XL-v2.0, no LoRA, SpriteForge south pose guide via the
  xinsir OpenPose ControlNet (0.7), IP-Adapter plus on the PRIMARY bridge reference (0.55, identity
  carrier), prompt = fixed style block + tags translated from the brief, dpmpp_2m/karras 28 steps
  cfg 5.5, seed 2002, 1024x1024.

Every render writes a receipt binding: SpriteForge repo HEAD + the imported helper's sha, ComfyUI
version, identity uid + state versions, visual-profile digest, brief digest, reference sha256,
rendering profile, the exact graph (sha256) and the output sha256. The purpose is reproducibility of
"this image came from this registered identity via this evidence", not photorealism.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, subprocess, sys, time, urllib.request
from PIL import Image, ImageDraw
SF = pathlib.Path.home() / "SpriteForge-App"
sys.path.insert(0, str(SF / "scripts"))
import base4dir_round as b4  # noqa: E402  (read-only reuse of SpriteForge helpers)

PROFILE = {
    "name": "exp002-portrait-v1", "ckpt": "Anime/Illustrious-XL-v2.0.safetensors", "lora": None,
    "style": "masterpiece, best quality, clean lineart, flat cel shading, full body, standing straight, arms at sides, front view, facing viewer, looking at viewer, simple background, pure white background, solo, game character concept art, realistic proportions, adult",
    "negative": b4.NEG + ", text, watermark, signature, multiple people, 2boys, 2girls, chibi, child",
    "pose_strength": 0.7, "pose_end": 0.8, "ip_weight": 0.55, "ip_end": 0.9, "steps": 28, "cfg": 5.5, "sampler": "dpmpp_2m", "scheduler": "karras", "seed": 2002, "size": 1024,
}

def sha(b): return hashlib.sha256(b).hexdigest()

def tags_from_brief(brief: dict) -> str:
    """Translate the brief's observations into prompt tags. Nothing is added that the brief does not
    state; free-text fields are trimmed to their first clause and reference bookkeeping is dropped."""
    label = brief["subject"]["label"].lower()
    parts = ["elderly woman" if "ostrom" in label else "elderly man"]
    def clause(v): return v.split(";")[0].split("(")[0].strip().rstrip(",")
    parts.append(clause(brief["head_and_hair"]))
    fh = brief["facial_hair"].lower()
    parts.append("clean-shaven" if "clean" in fh else (clause(brief["facial_hair"]) if fh not in ("n/a", "none") else ""))
    gl = brief["glasses"].lower()
    parts.append("no glasses" if gl.startswith("none") else clause(brief["glasses"]))
    parts.append(clause(brief["silhouette_build"]))
    parts.append(clause(brief["clothing_period_style"]))
    for t in brief["distinguishing_features"]:
        if any(k in t.lower() for k in ("img-", "lecturing", "holding", "prototype")): continue
        parts.append(clause(t))
    seen, outp = set(), []
    for x in parts:
        x = x.strip()
        if x and x.lower() not in seen: seen.add(x.lower()); outp.append(x)
    return ", ".join(outp)

def graph(profile, pose_name, ref_name, prompt, seed, prefix):
    g = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": profile["ckpt"]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["1", 1], "text": prompt}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["1", 1], "text": profile["negative"]}},
        "5": {"class_type": "EmptyLatentImage", "inputs": {"width": profile["size"], "height": profile["size"], "batch_size": 1}},
        "20": {"class_type": "LoadImage", "inputs": {"image": pose_name}},
        "21": {"class_type": "ControlNetLoader", "inputs": {"control_net_name": "controlnet-openpose-sdxl-xinsir.safetensors"}},
        "22": {"class_type": "ControlNetApplyAdvanced", "inputs": {"positive": ["3", 0], "negative": ["4", 0], "control_net": ["21", 0], "image": ["20", 0], "vae": ["1", 2], "strength": profile["pose_strength"], "start_percent": 0.0, "end_percent": profile["pose_end"]}},
        "10": {"class_type": "LoadImage", "inputs": {"image": ref_name}},
        "11": {"class_type": "IPAdapterModelLoader", "inputs": {"ipadapter_file": "ip-adapter-plus_sdxl_vit-h.safetensors"}},
        "12": {"class_type": "CLIPVisionLoader", "inputs": {"clip_name": "CLIP-ViT-H-14-laion2B-s32B-b79K.safetensors"}},
        "13": {"class_type": "IPAdapterAdvanced", "inputs": {"model": ["1", 0], "ipadapter": ["11", 0], "image": ["10", 0], "clip_vision": ["12", 0], "weight": profile["ip_weight"], "weight_type": "linear", "combine_embeds": "concat", "start_at": 0.0, "end_at": profile["ip_end"], "embeds_scaling": "V only"}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["13", 0], "positive": ["22", 0], "negative": ["22", 1], "latent_image": ["5", 0], "seed": seed, "denoise": 1.0, "steps": profile["steps"], "cfg": profile["cfg"], "sampler_name": profile["sampler"], "scheduler": profile["scheduler"]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["1", 2]}},
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": prefix}},
    }
    return g

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--bridge", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--dry-run", action="store_true"); ap.add_argument("--seed", type=int, default=PROFILE["seed"]); a = ap.parse_args()
    bridge = pathlib.Path(a.bridge); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    sf_head = subprocess.run(["git", "-C", str(SF), "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    helper_sha = sha((SF / "scripts/base4dir_round.py").read_bytes())[:16]
    comfy = None
    if not a.dry_run:
        comfy = json.loads(urllib.request.urlopen(b4.ENGINE + "/system_stats", timeout=10).read())["system"]["comfyui_version"]
    pose_im = b4.render_pose(b4.FRONT)
    pose_name = None if a.dry_run else b4.upload(pose_im, "exp002-pose-south.png")[0]
    receipts, tiles = [], []
    for entry in json.load(open(bridge / "index.json"))["packages"]:
        pkg = bridge / entry["slug"]; manifest = json.load(open(pkg / "manifest.json")); brief = json.load(open(pkg / "spriteforge-brief.json"))
        primary = next(r for r in brief["reference_images"] if r["primary"]); ref_file = next(r["file"] for r in manifest["references"] if r["sha256"] == primary["sha256"])
        ref_im = b4.square_pad(pkg / ref_file)
        prompt = PROFILE["style"] + ", " + tags_from_brief(brief)
        seed = a.seed; prefix = f"exp002/{entry['slug']}-s{seed}"
        ref_name = None if a.dry_run else b4.upload(ref_im, f"exp002-ref-{entry['slug']}.png")[0]
        g = graph(PROFILE, pose_name or "pose", ref_name or "ref", prompt, seed, prefix)
        gj = json.dumps(g, sort_keys=True).encode()
        rec = {"subject": manifest["label"], "uid": manifest["uid"], "identityStateVersions": manifest["identityStateVersions"], "visualProfileDigest": manifest["visualProfileDigest"],
               "briefDigest": manifest["briefDigest"], "referenceImageId": primary["imageId"], "referenceSha256": primary["sha256"], "referenceLicence": primary["licence"],
               "renderingProfile": PROFILE["name"], "profile": {k: v for k, v in PROFILE.items() if k not in ("negative",)}, "prompt": prompt, "seed": seed, "graphSha256": sha(gj),
               "spriteforgeRepoHead": sf_head, "spriteforgeHelperSha256_16": helper_sha, "comfyuiVersion": comfy, "renderedAt": None, "outputFile": None, "outputSha256": None, "seconds": None}
        (out / f"{entry['slug']}-graph.json").write_bytes(gj)
        if not a.dry_run:
            png, secs = b4.run(g)
            f = out / f"{entry['slug']}-s{seed}.png"; f.write_bytes(png)
            rec.update(renderedAt=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), outputFile=f.name, outputSha256=sha(png), seconds=round(secs, 1))
            im = Image.open(f).convert("RGB"); im.thumbnail((512, 512)); tiles.append((manifest["label"], im))
            print(f"{manifest['label']}: {secs:.0f}s → {f.name}")
        receipts.append(rec)
    if tiles:
        sheet = Image.new("RGB", (512 * len(tiles), 560), "white"); d = ImageDraw.Draw(sheet)
        for i, (label, im) in enumerate(tiles):
            sheet.paste(im, (i * 512 + (512 - im.width) // 2, 0)); d.text((i * 512 + 8, 520), label, fill="black"); d.text((i * 512 + 8, 536), f"profile {PROFILE['name']} · seed {a.seed}", fill="black")
        gf = out / f"group-exp002-s{a.seed}.png"; sheet.save(gf)
        group = {"file": gf.name, "sha256": sha(gf.read_bytes()), "subjects": [t[0] for t in tiles], "composition": "six same-profile single-subject renders composed left-to-right; identity information is the only per-subject variable"}
    else:
        group = None
    (out / f"receipt-s{a.seed}.json").write_text(json.dumps({"receiptVersion": "0.1.0", "renderingProfile": PROFILE["name"], "dryRun": a.dry_run, "group": group, "renders": receipts}, indent=2) + "\n")
    print("receipt written", len(receipts), "renders", "(dry run)" if a.dry_run else "")

if __name__ == "__main__": main()
