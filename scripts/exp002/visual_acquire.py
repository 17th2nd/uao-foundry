#!/usr/bin/env python3
"""Experiment 002 Phase 7 — acquire reference imagery for registered persons from Wikimedia Commons.

Commons is used because every file carries machine-readable licence metadata (extmetadata). Only
files whose LicenseShortName is public domain / CC0 / CC BY / CC BY-SA are kept, and the licence,
author, credit, date and attribution-required flags are recorded per file in a receipt that sits
BESIDE the identity, never inside it. Reusability is what the metadata says, not what a download
implies; anything ambiguous is recorded as such and excluded.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re, sys, time, urllib.parse, urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "UAO-Foundry-Experiment-002/0.1 (research provenance acquisition; contact via github.com/17th2nd/uao-foundry)"
OK_LICENCES = re.compile(r"^(Public domain|CC0|CC BY|CC BY-SA|CC-BY|CC-BY-SA|CC BY \d|CC BY-SA \d|PD)", re.I)
BLOCK = re.compile(r"(NC|ND|GFDL only|Fair use|non-free)", re.I)

def get(params):
    params = dict(params, format="json"); url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r: return json.loads(r.read().decode())

def search(person, limit=30):
    hits = []
    for q in (f'"{person}" portrait', f'"{person}"'):
        res = get({"action": "query", "list": "search", "srsearch": q, "srnamespace": 6, "srlimit": limit})
        for h in res.get("query", {}).get("search", []):
            t = h["title"]
            if t not in hits and re.search(r"\.(jpe?g|png|tiff?)$", t, re.I): hits.append(t)
    return hits

def info(titles):
    out = {}
    for i in range(0, len(titles), 20):
        chunk = titles[i:i+20]
        res = get({"action": "query", "prop": "imageinfo", "titles": "|".join(chunk),
                   "iiprop": "url|size|mime|extmetadata|sha1", "iiurlwidth": 1024, "iiextmetadatafilter": "LicenseShortName|License|Artist|Credit|DateTimeOriginal|ImageDescription|AttributionRequired|Copyrighted|UsageTerms|LicenseUrl"})
        for page in res.get("query", {}).get("pages", {}).values():
            ii = (page.get("imageinfo") or [None])[0]
            if ii: out[page["title"]] = ii
    return out

def fetch(url, tries=4):
    delay = 5
    for attempt in range(tries):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=120).read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == tries - 1: raise
            time.sleep(delay); delay *= 3

def clean(v): return re.sub(r"<[^>]+>", "", str(v)).strip() if v is not None else None

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--out", required=True); ap.add_argument("--per-person", type=int, default=4)
    ap.add_argument("people", nargs="+"); a = ap.parse_args()
    out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    receipt = {"receiptVersion": "0.1.0", "acquiredAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "source": "Wikimedia Commons API", "policy": "keep PD/CC0/CC BY/CC BY-SA only; NC/ND/fair-use excluded; licence recorded verbatim from extmetadata", "people": {}}
    for person in a.people:
        slug = re.sub(r"[^a-z0-9]+", "-", person.lower()).strip("-")
        pdir = out / "references" / slug; pdir.mkdir(parents=True, exist_ok=True)
        titles = search(person); meta = info(titles)
        kept, rejected = [], []
        for title in titles:
            ii = meta.get(title)
            if not ii: continue
            em = {k: clean(v.get("value")) for k, v in (ii.get("extmetadata") or {}).items()}
            lic = em.get("LicenseShortName") or em.get("License") or ""
            desc = (em.get("ImageDescription") or "")[:300]
            name_ok = person.split()[-1].lower() in (title + " " + desc).lower()
            if not name_ok: rejected.append({"title": title, "why": "surname absent from title/description"}); continue
            if not OK_LICENCES.match(lic) or BLOCK.search(lic) or BLOCK.search(em.get("UsageTerms") or ""):
                rejected.append({"title": title, "why": f"licence not reusable or ambiguous: {lic!r}"}); continue
            if ii.get("width", 0) < 300 or ii.get("height", 0) < 300:
                rejected.append({"title": title, "why": "too small"}); continue
            if len(kept) >= a.per_person: break
            fname = f"{slug}-{len(kept)+1:02d}" + pathlib.Path(title).suffix.lower()
            data = fetch(ii.get("thumburl") or ii["url"])
            (pdir / fname).write_bytes(data)
            kept.append({"imageId": f"img-{slug}-{len(kept)+1:02d}", "file": f"references/{slug}/{fname}", "commonsTitle": title, "commonsUrl": ii.get("descriptionurl"),
                         "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data), "width": ii.get("width"), "height": ii.get("height"), "fetched": "thumbnail-1024" if ii.get("thumburl") else "original", "originalUrl": ii.get("url"), "mime": ii.get("mime"),
                         "licence": lic, "licenceUrl": em.get("LicenseUrl"), "attributionRequired": em.get("AttributionRequired"), "artist": em.get("Artist"), "credit": em.get("Credit"),
                         "dateTimeOriginal": em.get("DateTimeOriginal"), "description": desc, "reuseStatus": "REUSABLE_PER_COMMONS_METADATA" if lic else "UNKNOWN"})
            time.sleep(3)
        receipt["people"][person] = {"slug": slug, "kept": kept, "rejected": rejected[:25], "searched": len(titles)}
        print(f"{person}: {len(kept)} kept / {len(titles)} searched; rejected {len(rejected)}")
        for k in kept: print(f"   {k['imageId']}  {k['licence']:<22} {k['dateTimeOriginal'] or '?':<22} {k['commonsTitle'][:70]}")
    (out / "visual-references.json").write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n")

if __name__ == "__main__": main()
