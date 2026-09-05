#!/usr/bin/env python3
"""Code spriteforge.visual-descriptor/v0.1 records for the six cyberneticians FROM description.json.

Every non-unknown value names the image ids that support it; where the prose and the enumeration do not
meet, the value is "unknown" and the prose goes in `note`. Nothing is inferred beyond the evidenced epochs.
Ages come from the registry's birth assertions (year arithmetic only; month ignored where the epoch is a year).
"""
from __future__ import annotations
import hashlib, json, pathlib, time
SCHEMA_ID = "spriteforge.visual-descriptor/v0.1"
STORE = pathlib.Path("work/usi-people/visual-evidence")
NOW = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
BIRTH = {"uao-11eb71766754": 1894, "uao-b160552cb26e": 1916, "uao-bda651d022bf": 1936, "uao-830ce8cca652": 1925, "uao-09f5f6b2f9fd": 1926, "uao-1fced4a70aa1": 1933}
BIRTH_PKG = {"uao-11eb71766754": "pkg-639286abda65eaaf", "uao-b160552cb26e": "pkg-597208fbeaf09a9a", "uao-bda651d022bf": "pkg-549240a93185b759", "uao-830ce8cca652": "pkg-2d4ab4307cf5e410", "uao-09f5f6b2f9fd": "pkg-167fb37d1b66d4dc", "uao-1fced4a70aa1": "pkg-b5f108fed1b95194"}

def F(value, conf, ev, era, note=None):
    f = {"value": value, "confidence": conf, "evidence": list(ev), "era": era}
    if note: f["note"] = note
    return f
def U(era, note=None, conf="low"):
    f = {"value": "unknown", "confidence": conf, "evidence": [], "era": era}
    if note: f["note"] = note
    return f
def mark(t, loc, conf, ev, size="unknown", note=None):
    m = {"type": t, "location": loc, "size": size, "confidence": conf, "evidence": list(ev)}
    if note: m["note"] = note
    return m
def garment(item, colour, pattern, ev, conf="high", detail=None, material=None):
    g = {"item": item, "colour": colour, "pattern": pattern, "evidence": list(ev), "confidence": conf}
    if detail: g["detail"] = detail
    if material: g["material"] = material
    return g
def acc(item, ev, colour=None, position=None, habitual=None, conf="medium"):
    a = {"item": item, "evidence": list(ev), "confidence": conf}
    if colour: a["colour"] = colour
    if position: a["position"] = position
    if habitual is not None: a["habitual"] = habitual
    return a

def descriptor(uid, person, year, year_range, basis, epoch_ev, features, not_ev, description):
    age = year - BIRTH[uid]
    return {"schema": SCHEMA_ID, "version": "0.1.0", "identity_uid": uid, "represented_person": person,
            "epoch": {"year": year, "year_range": year_range, "age_at_epoch": age, "basis": basis + f"; age = {year} − {BIRTH[uid]} (registry birth assertion in {BIRTH_PKG[uid]})", "evidence": epoch_ev},
            "features": features, "not_evidenced": not_ev, "prose": description["observations"], "source_limits": description["sourceLimits"],
            "coded_by": "claude-fable-5-1 (UAO Foundry operator), coded from description.json and the kept references at face-crop magnification", "coded_at": NOW}

W2, W3 = ["img-norbert-wiener-02"], ["img-norbert-wiener-03"]
S3, S12, S4 = ["img-herbert-a-simon-03"], ["img-herbert-a-simon-01", "img-herbert-a-simon-02"], ["img-herbert-a-simon-04"]
P1, P23 = ["img-judea-pearl-01"], ["img-judea-pearl-02", "img-judea-pearl-03"]
E1, E2, E3, E4 = ["img-douglas-engelbart-01"], ["img-douglas-engelbart-02"], ["img-douglas-engelbart-03"], ["img-douglas-engelbart-04"]; E08 = E1 + E3 + E4
B1, B2, B3, B4 = ["img-stafford-beer-01"], ["img-stafford-beer-02"], ["img-stafford-beer-03"], ["img-stafford-beer-04"]; BA = B1 + B2 + B3 + B4
O1, O2, O3, O4 = ["img-elinor-ostrom-01"], ["img-elinor-ostrom-02"], ["img-elinor-ostrom-03"], ["img-elinor-ostrom-04"]; OA = O1 + O2 + O3 + O4

def build():
    out = []
    d = json.load(open(STORE / "uao-11eb71766754/description.json")); e = "c.1955–1962 (portrait) / 1951"
    out.append(descriptor("uao-11eb71766754", "Norbert Wiener", 1958, [1951, 1962], "undated studio portrait (img-02) placed c.1955–1962 by appearance and the MIT-era attribution; img-03 dated 1951", W2 + W3, {
        "presentation": {"sex_presentation": F("man", "high", W2 + W3, e), "age_band": F("60s", "medium", W2, e, "portrait undated; range 57–68 across the epoch")},
        "build": {"stature_build": F("heavy", "high", W2 + W3, e), "shoulders": F("broad", "high", W2 + W3, e), "posture": F("slightly_stooped", "medium", W3, "1951"), "neck": F("short_thick", "high", W2, e)},
        "head": {"face_shape": F("round", "high", W2, e), "forehead": F("high", "high", W2, e), "cheeks": F("full", "high", W2, e), "jaw": F("wide", "medium", W2, e), "chin": F("receding", "medium", W2, e, "small chin under the goatee, receding into a full neck")},
        "hair": {"hairline": F("receding_temples_deep", "high", W2, e, "broad central tongue of hair remains; crown thin but present; Norwood III–IV"), "length": F("short", "high", W2, e), "texture": F("fine", "medium", W2, e), "colour": F("white_with_grey", "high", W2, e, "monochrome source; near-white rendering"), "style": F("brushed_back", "high", W2, e), "volume": F("thin", "medium", W2, e)},
        "brows": {"thickness": F("bushy", "high", W2, e), "shape": F("downward_sloping", "high", W2, e), "colour": F("dark", "medium", W2, e, "darker than the hair in monochrome")},
        "eyes": {"size": F("small", "high", W2, e), "set": F("deep_set", "high", W2, e), "lids": F("heavy", "high", W2, e), "bags": F("pronounced", "high", W2, e), "colour": U(e, "monochrome sources")},
        "eyewear": {"present": F("glasses", "high", W2 + W3, e), "lens_shape": F("panto", "high", W2, e, "round-to-panto; rounder in 1951"), "frame": F("thick_acetate", "high", W2, e), "frame_colour": F("black", "high", W2, e, "black-appearing in monochrome"), "worn": F("normal", "high", W2, e, "keyhole bridge; sits high on the nose")},
        "nose": {"length": F("medium", "medium", W2, e), "width": F("broad", "high", W2, e), "bridge": F("low", "medium", W2, e), "tip": F("rounded", "high", W2, e)},
        "mouth": {"lips": F("full", "medium", W2, e, "lower lip full below the moustache; upper lip hidden"), "width": F("medium", "low", W2, e), "resting": F("downturned", "medium", W2, e)},
        "facial_hair": {"pattern": F("moustache_and_goatee", "high", W2, e, "walrus-style moustache with a short chin goatee; cheeks and jaw clean-shaven"), "length": F("short", "high", W2, e), "colour": F("white_with_grey", "high", W2, e)},
        "ears": {"size": F("large", "medium", W2, e), "protrusion": F("average", "low", W2, e), "lobes": F("attached", "low", W2, e), "visibility": F("visible", "high", W2, e)},
        "skin": {"tone": U(e, "monochrome sources"), "complexion": U(e, "monochrome sources"), "lines": F("deep", "high", W2, e, "forehead furrows, glabellar lines, nasolabial folds, pouched lower lids"), "marks": []},
        "clothing": [garment("suit_jacket", "dark", "plain", W2, "high", "dark jacket in the portrait"), garment("shirt", "white", "plain", W2 + W3), garment("tie", "dark", "patterned", W2 + W3, "medium", "foulard/spotted"), garment("jacket", "light", "tweed", W3, "medium", "1951 only")],
        "accessories": [], "props": [{"item": "El Ajedrecista chess automaton (in frame, not held)", "evidence": W3}]},
        d["notEvidenced"] + ["moles/freckles/scars: none resolvable"], d))

    d = json.load(open(STORE / "uao-b160552cb26e/description.json")); e = "1981 (photograph); paintings 1965 and 1987"
    out.append(descriptor("uao-b160552cb26e", "Herbert A. Simon", 1981, [1965, 1987], "the one photograph is dated 1981-03-19; paintings bracket it", S3 + S12 + S4, {
        "presentation": {"sex_presentation": F("man", "high", S3, e), "age_band": F("60s", "high", S3, "1981")},
        "build": {"stature_build": F("medium", "medium", S3 + S4, e), "shoulders": F("medium", "medium", S3, "1981"), "posture": F("upright", "medium", S3, "1981"), "neck": F("medium", "medium", S3, "1981")},
        "head": {"face_shape": F("rectangular", "high", S3, "1981"), "forehead": F("high", "high", S3, "1981"), "cheeks": F("lean", "high", S3, "1981"), "jaw": F("square", "high", S3, "1981"), "chin": F("square", "medium", S3, "1981", "a faint cleft is suggested but not certain in the halftone")},
        "hair": {"hairline": F("receding_temples", "high", S3, "1981", "high rounded hairline with a slight central peak; frontal-central scalp bare by 1987 (img-04)"), "length": F("short", "high", S3, "1981"), "texture": F("wavy", "high", S3 + S12, e), "colour": F("dark_brown", "medium", S3 + S12, e, "near-black in the 1981 halftone with lighter temples; paint renders it dark"), "style": F("brushed_back", "high", S3, "1981"), "volume": F("thick", "medium", S3 + S12, e)},
        "brows": {"thickness": F("thick", "high", S3, "1981"), "shape": F("straight", "medium", S3, "1981", "straight to slightly arched, heavy inner end"), "colour": F("dark", "high", S3, "1981")},
        "eyes": {"size": F("medium", "medium", S3, "1981"), "set": F("deep_set", "high", S3, "1981"), "lids": F("heavy", "high", S3, "1981", "hooded"), "bags": F("pronounced", "high", S3, "1981"), "colour": U(e, "monochrome photograph; paintings are not colour evidence")},
        "eyewear": {"present": F("none", "high", S3 + S12 + S4, e), "lens_shape": F("unknown", "high", [], e, "not applicable"), "frame": F("unknown", "high", [], e, "not applicable"), "frame_colour": F("unknown", "high", [], e, "not applicable"), "worn": F("unknown", "high", [], e, "not applicable")},
        "nose": {"length": F("long", "high", S3 + S12, e), "width": F("broad", "medium", S3, "1981"), "bridge": F("straight", "high", S3, "1981"), "tip": F("bulbous", "medium", S3, "1981")},
        "mouth": {"lips": F("thin", "high", S3, "1981"), "width": F("wide", "high", S3, "1981"), "resting": F("broad_smile", "high", S3, "1981", "the photograph shows a broad smile, asymmetric, higher on his right; the paintings show a sober downward gaze")},
        "facial_hair": {"pattern": F("clean_shaven", "high", S3 + S12 + S4, e), "length": F("none", "high", S3 + S12 + S4, e), "colour": F("unknown", "high", [], e, "not applicable")},
        "ears": {"size": F("medium", "medium", S3, "1981"), "protrusion": F("flat", "medium", S3, "1981"), "lobes": U("1981"), "visibility": F("visible", "high", S3, "1981")},
        "skin": {"tone": U(e, "colour sources are paint"), "complexion": U(e, "colour sources are paint"), "lines": F("deep", "high", S3, "1981", "forehead creases, glabellar furrows, nasolabial folds, cheek creases"), "marks": [mark("mole", "left cheek (viewer's right), level with the nose tip", "low", S3, "small", "may be a halftone printing artefact")]},
        "clothing": [garment("suit_jacket", "dark", "plain", S3), garment("shirt", "white", "plain", S3), garment("tie", "dark", "striped", S3, "high", "diagonal stripes")],
        "accessories": [], "props": []},
        d["notEvidenced"], d))

    d = json.load(open(STORE / "uao-bda651d022bf/description.json")); e = "2007–2013"
    out.append(descriptor("uao-bda651d022bf", "Judea Pearl", 2013, [2007, 2013], "two 2013 poster-session frames (low resolution) and one 2007 photograph", P1 + P23, {
        "presentation": {"sex_presentation": F("man", "high", P1 + P23, e), "age_band": F("70s", "high", P1 + P23, e)},
        "build": {"stature_build": F("medium", "medium", P1 + P23, e), "shoulders": F("medium", "low", P1, "2007", "rounded"), "posture": F("stooped", "medium", P1 + P23, e, "stooped forward when reading or lighting a candle; may be task posture"), "neck": U(e)},
        "head": {"face_shape": F("long", "high", P1, "2007"), "forehead": F("high", "high", P1, "2007"), "cheeks": F("lean", "medium", P1, "2007"), "jaw": U(e, "beard covers the jawline"), "chin": F("hidden_by_beard", "high", P1 + P23, e)},
        "hair": {"hairline": F("high", "medium", P1 + P23, e, "frontal hairline intact but high, hair pushed back; crown covered by a kippah in 2007, thinner and flatter in 2013"), "length": F("medium", "high", P1 + P23, e, "sweeps over the ears and collar"), "texture": F("straight", "medium", P1 + P23, e, "straight to slightly wavy"), "colour": F("grey", "high", P1, "2007", "silver-grey/white by 2013 (img-02/03)"), "style": F("swept_back", "high", P1 + P23, e), "volume": F("medium", "medium", P1, "2007")},
        "brows": {"thickness": F("medium", "medium", P1, "2007"), "shape": F("downward_sloping", "medium", P1, "2007"), "colour": F("grey", "medium", P1, "2007", "dark-to-grey")},
        "eyes": {"size": U(e, "eyes cast down in every frame"), "set": U(e), "lids": F("heavy", "medium", P1, "2007", "hooded"), "bags": U(e), "colour": U(e)},
        "eyewear": {"present": F("glasses", "high", P1 + P23, e), "lens_shape": F("rectangular", "high", P1 + P23, e, "narrow lens height"), "frame": F("wire", "medium", P1, "2007", "thin metal, rimless-looking"), "frame_colour": F("silver", "low", P1, "2007"), "worn": F("normal", "medium", P1, "2007")},
        "nose": {"length": F("long", "high", P1, "2007"), "width": F("medium", "medium", P1, "2007"), "bridge": F("high", "medium", P1, "2007", "pronounced bridge"), "tip": F("downturned", "high", P1, "2007")},
        "mouth": {"lips": U(e, "hidden by moustache and downward pose"), "width": U(e), "resting": U(e)},
        "facial_hair": {"pattern": F("short_full_beard", "high", P1 + P23, e, "trimmed along jaw and chin with moustache in 2007; fuller and whiter in 2013; denser on the chin than the cheeks"), "length": F("short", "high", P1 + P23, e), "colour": F("grey", "high", P1, "2007", "white by 2013")},
        "ears": {"size": U(e), "protrusion": U(e), "lobes": U(e), "visibility": F("partly_covered", "medium", P1 + P23, e, "hair sweeps over the ears")},
        "skin": {"tone": F("fitzpatrick_II", "low", P1, "2007", "light-to-medium in one indoor colour photograph"), "complexion": F("fair", "low", P1, "2007"), "lines": F("deep", "medium", P1, "2007", "nasolabial folds and forehead lines"), "marks": []},
        "clothing": [garment("suit_jacket", "black", "plain", P1, "high", "2007, with white pocket square"), garment("shirt", "white", "plain", P1 + P23), garment("tie", "dark", "striped", P1, "high", "2007"), garment("jacket", "dark", "plain", P23, "medium", "2013")],
        "accessories": [acc("kippah", P1, "dark", "crown", habitual=None, conf="high"), acc("wristwatch", P1, position="left wrist", conf="medium"), acc("conference lanyard", P23, position="neck", conf="medium")], "props": []},
        d["notEvidenced"] + ["mouth (hidden), ears, eye set and size, neck"], d))

    d = json.load(open(STORE / "uao-830ce8cca652/description.json"))
    e = "1968"
    out.append(descriptor("uao-830ce8cca652", "Douglas Engelbart", 1968, [1968, 1968], "one monochrome SRI photograph dated 1968-12 (img-02)", E2, {
        "presentation": {"sex_presentation": F("man", "high", E2, e), "age_band": F("40s", "high", E2, e)},
        "build": {"stature_build": F("lean", "high", E2, e), "shoulders": F("medium", "medium", E2, e), "posture": F("upright", "medium", E2, e, "seated, leaning forward while gesturing"), "neck": F("medium", "medium", E2, e)},
        "head": {"face_shape": F("long", "high", E2, e), "forehead": F("very_high", "high", E2, e), "cheeks": F("lean", "high", E2, e), "jaw": F("square", "high", E2, e), "chin": F("square", "medium", E2, e)},
        "hair": {"hairline": F("full", "high", E2, e), "length": F("short", "high", E2, e), "texture": F("wavy", "medium", E2, e, "slight wave, glossy"), "colour": F("dark_brown", "medium", E2, e, "monochrome; mid-brown rendering"), "style": F("side_parted", "high", E2, e, "parted on his left, brushed back and up"), "volume": F("thick", "medium", E2, e)},
        "brows": {"thickness": F("medium", "medium", E2, e), "shape": F("straight", "medium", E2, e), "colour": F("dark", "high", E2, e)},
        "eyes": {"size": F("medium", "medium", E2, e), "set": F("average", "low", E2, e), "lids": F("average", "low", E2, e), "bags": F("mild", "low", E2, e), "colour": U(e, "monochrome; blue-grey in the 2008 epoch")},
        "eyewear": {"present": F("none", "high", E2, e), "lens_shape": F("unknown", "high", [], e, "not applicable"), "frame": F("unknown", "high", [], e, "not applicable"), "frame_colour": F("unknown", "high", [], e, "not applicable"), "worn": F("unknown", "high", [], e, "not applicable")},
        "nose": {"length": F("long", "high", E2, e), "width": F("medium", "medium", E2, e), "bridge": F("straight", "high", E2, e), "tip": F("rounded", "medium", E2, e)},
        "mouth": {"lips": F("thin", "high", E2, e), "width": F("wide", "high", E2, e), "resting": F("neutral", "medium", E2, e, "mid-speech")},
        "facial_hair": {"pattern": F("clean_shaven", "high", E2, e), "length": F("none", "high", E2, e), "colour": F("unknown", "high", [], e, "not applicable")},
        "ears": {"size": F("large", "medium", E2, e), "protrusion": F("average", "low", E2, e), "lobes": F("long", "low", E2, e), "visibility": F("visible", "high", E2, e)},
        "skin": {"tone": U(e, "monochrome"), "complexion": U(e, "monochrome"), "lines": F("fine", "medium", E2, e), "marks": []},
        "clothing": [garment("suit_jacket", "dark", "plain", E2), garment("shirt", "white", "plain", E2), garment("tie", "dark", "plain", E2, "medium")],
        "accessories": [acc("wired earpiece and microphone headset (demonstration rig)", E2, position="right ear and cheek", habitual=False, conf="high")], "props": [{"item": "chalkboard (in frame)", "evidence": E2}]},
        ["eye colour (monochrome)", "skin tone", "moles/freckles/scars", "height", "appearance between 1968 and 2008"], d))
    e = "2008"
    out.append(descriptor("uao-830ce8cca652", "Douglas Engelbart", 2008, [2008, 2008], "three colour photographs from November–December 2008 (img-01 close-up, img-03 candid, img-04 studio)", E08, {
        "presentation": {"sex_presentation": F("man", "high", E08, e), "age_band": F("80s", "high", E08, e)},
        "build": {"stature_build": F("lean", "high", E08, e), "shoulders": F("medium", "medium", E4, e), "posture": F("upright", "high", E4, e), "neck": F("long", "medium", E4, e)},
        "head": {"face_shape": F("long", "high", E1 + E4, e), "forehead": F("very_high", "high", E1 + E4, e), "cheeks": F("lean", "medium", E1 + E4, e, "prominent cheekbones"), "jaw": F("square", "high", E1 + E4, e), "chin": F("square", "medium", E4, e)},
        "hair": {"hairline": F("full", "high", E1 + E4, e, "full, low frontal hairline; essentially no temple recession at 83"), "length": F("short", "high", E08, e, "sides cover the top half of the ears"), "texture": F("wavy", "medium", E1 + E4, e, "coarse-looking, slightly wavy"), "colour": F("white", "high", E08, e, "faint yellow cast"), "style": F("side_parted", "high", E1 + E4, e, "parted on his left, front swept up and across"), "volume": F("thick", "high", E08, e)},
        "brows": {"thickness": F("thin", "high", E1, e, "sparse"), "shape": F("straight", "medium", E1, e), "colour": F("white", "high", E1, e)},
        "eyes": {"size": F("medium", "medium", E1, e), "set": F("average", "medium", E1, e), "lids": F("heavy", "high", E1, e, "hooded upper lids"), "bags": F("pronounced", "high", E1, e), "colour": F("blue_grey", "high", E1, e)},
        "eyewear": {"present": F("none", "high", E08, e), "lens_shape": F("unknown", "high", [], e, "not applicable"), "frame": F("unknown", "high", [], e, "not applicable"), "frame_colour": F("unknown", "high", [], e, "not applicable"), "worn": F("unknown", "high", [], e, "not applicable")},
        "nose": {"length": F("long", "high", E1 + E4, e), "width": F("medium", "medium", E1, e, "broad tip"), "bridge": F("straight", "high", E1, e), "tip": F("rounded", "high", E1, e)},
        "mouth": {"lips": F("thin", "high", E1 + E4, e), "width": F("wide", "high", E1 + E4, e), "resting": F("broad_smile", "high", E1 + E4, e, "smiling in every 2008 frame, upper teeth visible")},
        "facial_hair": {"pattern": F("clean_shaven", "high", E08, e), "length": F("none", "high", E08, e), "colour": F("unknown", "high", [], e, "not applicable")},
        "ears": {"size": F("large", "medium", E1 + E4, e), "protrusion": F("protruding", "low", E4, e, "slightly"), "lobes": F("long", "medium", E1 + E4, e), "visibility": F("partly_covered", "high", E1 + E4, e, "hair covers the top half")},
        "skin": {"tone": F("fitzpatrick_II", "medium", E1 + E4, e), "complexion": F("ruddy", "high", E1 + E4, e, "pink-ruddy with visible redness"), "lines": F("moderate", "high", E1, e, "fine wrinkling across the forehead; deep nasolabial folds and marionette lines"),
                 "marks": [mark("redness", "nose tip and nostrils, cheeks, forehead", "high", E1 + E4, "medium"), mark("capillaries", "nose tip", "medium", E1, "small"), mark("age_spots", "forehead and temples, scattered", "medium", E1 + E4, "small"), mark("mole", "right cheek (viewer's left) below the eye", "medium", E4, "small", "brownish spot in the studio portrait")]},
        "clothing": [garment("jacket", "brown", "herringbone", E08, "high", "worn open in all three frames", "tweed"), garment("shirt", "mid blue", "plain", E08, "high", "open collar, no tie")],
        "accessories": [acc("small round lapel pin", E4 + E3, position="left lapel", habitual=None, conf="medium"), acc("name badge", E3, position="chest", habitual=False, conf="medium")], "props": [{"item": "wooden mouse prototype (held in both hands)", "evidence": E4}]},
        ["scars", "dental detail", "height", "appearance between 1968 and 2008"], d))

    d = json.load(open(STORE / "uao-09f5f6b2f9fd/description.json")); e = "1990"
    out.append(descriptor("uao-09f5f6b2f9fd", "Stafford Beer", 1990, [1990, 1990], "four monochrome frames from one 1990 lecture session (University of St. Gallen archive)", BA, {
        "presentation": {"sex_presentation": F("man", "high", BA, e), "age_band": F("60s", "high", BA, e)},
        "build": {"stature_build": F("stocky", "high", B3 + B4, e), "shoulders": F("broad", "high", B3 + B4, e), "posture": F("upright", "medium", B3, e, "leans over papers in img-01/02"), "neck": F("short", "medium", B2, e, "hidden by the beard")},
        "head": {"face_shape": F("round", "medium", B2, e, "broad, wide face; lower face hidden by the beard"), "forehead": F("high", "high", B2, e), "cheeks": F("medium", "low", B2, e), "jaw": U(e, "hidden by beard"), "chin": F("hidden_by_beard", "high", BA, e)},
        "hair": {"hairline": F("bald_top_with_sides", "high", B2 + B3, e, "top largely bare, hairline high and receded; Norwood VI-like; sides and back long and full"), "length": F("collar_length", "medium", B2 + B3, e, "sides and back sweep back over the ears"), "texture": F("wavy", "high", B2, e, "wiry"), "colour": F("white_with_grey", "high", BA, e, "monochrome"), "style": F("disordered", "high", B2 + B3, e, "swept back, standing out in wisps"), "volume": F("medium", "medium", B2, e, "at the sides; none on top")},
        "brows": {"thickness": F("bushy", "high", B2, e), "shape": F("angled", "medium", B2, e, "meet deep frown lines"), "colour": F("grey", "medium", B2, e, "dark-grey in monochrome")},
        "eyes": {"size": F("small", "high", B2, e), "set": F("deep_set", "high", B2, e), "lids": F("heavy", "high", B2, e, "hooded"), "bags": F("pronounced", "high", B2, e), "colour": U(e, "monochrome")},
        "eyewear": {"present": F("monocle", "high", BA, e, "single round lens on a cord: worn in the right eye in img-01/02, hanging unworn on its cord at the chest in img-03/04; not spectacles — founder correction 2026-09-05, coded under the schema rev of 2026-09-05 (SpriteForge 18df7bdd) that adds this value"), "lens_shape": F("round", "high", B2 + B1, e, "single lens, right eye"), "frame": F("metal", "high", B2, e, "monocle rim"), "frame_colour": U(e, "monochrome"), "worn": F("right_eye", "high", B1 + B2, e, "in the right eye (viewer's left) while reading in img-01/02; in img-03/04 it hangs on the cord and is not worn")},
        "nose": {"length": F("medium", "medium", B2, e), "width": F("broad", "high", B2, e), "bridge": F("bumped", "medium", B2, e), "tip": F("bulbous", "medium", B2, e, "wide rounded tip, flared nostrils")},
        "mouth": {"lips": U(e, "hidden by moustache"), "width": U(e), "resting": U(e)},
        "facial_hair": {"pattern": F("long_full_beard", "high", BA, e, "from sideburns and cheeks to the chest, tapering to a rounded point; full moustache merging into it"), "length": F("chest_length", "high", BA, e), "colour": F("white_with_grey", "high", BA, e)},
        "ears": {"size": U(e, "partly covered"), "protrusion": U(e), "lobes": F("long", "low", B2, e), "visibility": F("partly_covered", "high", B2, e)},
        "skin": {"tone": U(e, "monochrome"), "complexion": U(e, "monochrome"), "lines": F("deep", "high", B2, e, "several forehead creases, strong glabellar fold, crow's feet; weathered texture"), "marks": [mark("mole", "forehead above the left brow (viewer's right)", "low", B2, "small", "may be a blemish or print mark")]},
        "clothing": [garment("shirt", "black", "pinstripe", BA, "high", "long-sleeved, with a white open collar folded over the neckline"), garment("trousers", "dark", "plain", B3 + B4, "high")],
        "accessories": [acc("monocle on a cord (right eye)", BA, position="right eye when worn; otherwise hanging on the cord at the chest", habitual=True, conf="high")], "props": [{"item": "sheets of paper (held)", "evidence": B1 + B2}, {"item": "pen or cigarette (right hand)", "evidence": B3}, {"item": "chalkboard (in frame)", "evidence": B3 + B4}]},
        d["notEvidenced"] + ["jaw, mouth (hidden)", "monocle rim colour (monochrome)"], d))

    d = json.load(open(STORE / "uao-1fced4a70aa1/description.json")); e = "December 2009"
    out.append(descriptor("uao-1fced4a70aa1", "Elinor Ostrom", 2009, [2009, 2009], "four colour photographs from Nobel week, 7–9 December 2009", OA, {
        "presentation": {"sex_presentation": F("woman", "high", OA, e), "age_band": F("70s", "high", OA, e)},
        "build": {"stature_build": F("medium", "medium", O1 + O3, e), "shoulders": F("medium", "medium", O3, e), "posture": F("slightly_stooped", "medium", O1 + O4, e), "neck": F("short", "high", O1 + O4, e, "loose skin under the chin")},
        "head": {"face_shape": F("round", "high", O1 + O4, e), "forehead": F("medium", "medium", O1, e, "largely covered by the fringe"), "cheeks": F("full", "high", O1 + O4, e), "jaw": F("medium", "medium", O3, e), "chin": F("rounded", "high", O1 + O3, e)},
        "hair": {"hairline": F("covered", "high", O1 + O3 + O4, e, "under the fringe"), "length": F("medium", "high", O1 + O3, e, "chin-length"), "texture": F("fine", "high", O1 + O3, e, "straight to slightly wavy, flyaway"), "colour": F("silver", "high", O1 + O3, e, "pale blonde-yellow tones at the front, darker grey beneath"), "style": F("bob", "high", O1 + O3 + O4, e, "chin-length bob with a long side-swept fringe from her right toward her left; ends turn under at the jaw"), "volume": F("medium", "medium", O1 + O3, e)},
        "brows": {"thickness": F("thin", "high", O1 + O4, e), "shape": F("arched", "medium", O1, e, "lightly arched"), "colour": F("pale", "high", O1 + O4, e)},
        "eyes": {"size": F("small", "medium", O1 + O4, e, "behind lenses"), "set": F("average", "low", O1, e), "lids": F("heavy", "high", O1, e, "hooded"), "bags": F("pronounced", "high", O1, e), "colour": U(e, "lenses and lighting")},
        "eyewear": {"present": F("glasses", "high", OA, e), "lens_shape": F("rectangular", "high", O1 + O3 + O4, e, "soft corners"), "frame": F("wire", "high", O1 + O3, e, "fine wire, half-rim look"), "frame_colour": F("gold", "high", O1 + O3 + O4, e, "gold to rose-gold"), "worn": F("low_on_nose", "medium", O1 + O4, e, "brows sit above the frame")},
        "nose": {"length": F("short", "high", O1, e), "width": F("medium", "medium", O1, e), "bridge": F("straight", "high", O1, e), "tip": F("rounded", "high", O1, e)},
        "mouth": {"lips": F("thin", "high", O1 + O4, e), "width": F("medium", "medium", O1, e), "resting": F("slight_smile", "high", O1 + O4, e, "closed-mouth smile lifting both corners; open laugh in img-02")},
        "facial_hair": {"pattern": F("unknown", "high", [], e, "not applicable"), "length": F("unknown", "high", [], e, "not applicable"), "colour": F("unknown", "high", [], e, "not applicable")},
        "ears": {"size": U(e, "covered by the bob"), "protrusion": U(e), "lobes": U(e), "visibility": F("covered", "high", O3, e)},
        "skin": {"tone": F("fitzpatrick_II", "medium", O1, e), "complexion": F("fair", "high", O1 + O4, e, "warm flush on the cheeks"), "lines": F("moderate", "high", O1, e, "fine wrinkling around eyes and mouth; deep nasolabial folds and marionette lines"), "marks": [mark("age_spots", "right cheek (viewer's left) near the cheekbone", "medium", O1, "small"), mark("redness", "cheeks", "medium", O1 + O4, "medium", "warm flush")]},
        "clothing": [garment("turtleneck", "white", "plain", O1 + O4, "high"), garment("jacket", "black", "plain", O1 + O4, "high", "red embroidered trim at the collar and front edge (press conference)"), garment("jacket", "red", "patterned", O2 + O3, "high", "over black (banquet)")],
        "accessories": [acc("necklace of red oval beads on a dark cord", OA, "red", "collar", habitual=True, conf="high"), acc("name badge", O3, position="chest", habitual=False, conf="medium")], "props": [{"item": "microphone (in frame)", "evidence": O1 + O4}]},
        d["notEvidenced"] + ["ears (covered)", "natural hairline"], d))
    return out

if __name__ == "__main__":
    import sys
    descs = build()
    out_dir = pathlib.Path("work/exp002/visual/descriptors"); out_dir.mkdir(parents=True, exist_ok=True)
    for dsc in descs:
        body = dict(dsc); body.pop("descriptor_digest", None)
        dsc["descriptor_digest"] = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()
        name = f"{dsc['identity_uid']}-{dsc['epoch']['year']}.json"
        (out_dir / name).write_text(json.dumps(dsc, indent=2, ensure_ascii=False) + "\n")
        print(name, dsc["represented_person"], "age", dsc["epoch"]["age_at_epoch"], "digest", dsc["descriptor_digest"][:12])
