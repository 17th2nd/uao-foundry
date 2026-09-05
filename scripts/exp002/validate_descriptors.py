#!/usr/bin/env python3
"""Validate spriteforge.visual-descriptor/v0.1 files: JSON Schema 2020-12, enumRef membership (advisory in
the schema, enforced here), evidence ids against the identity's references.json, and the rule that every
non-unknown value carries at least one evidence id. Fails closed on the first class of error found."""
from __future__ import annotations
import json, pathlib, sys
import jsonschema
SCHEMA = pathlib.Path.home() / "SpriteForge-App/schemas/v2/visual-descriptor.schema.json"
STORE = pathlib.Path("work/usi-people/visual-evidence")

def enum_map(schema):
    out = {}
    for group, g in schema["properties"]["features"]["properties"].items():
        for name, prop in g.get("properties", {}).items():
            if "enumRef" in prop: out[(group, name)] = set(prop["enumRef"])
    return out

def check(path, schema, enums):
    d = json.loads(pathlib.Path(path).read_text()); errors = []
    v = jsonschema.Draft202012Validator(schema)
    for e in sorted(v.iter_errors(d), key=lambda e: e.path): errors.append(f"schema: {'/'.join(map(str, e.path))}: {e.message[:120]}")
    refs = json.loads((STORE / d["identity_uid"] / "references.json").read_text())
    known = {r["imageId"] for r in refs["references"]}
    def ev_ok(ids, where):
        for i in ids:
            if i not in known: errors.append(f"{where}: unknown evidence id {i}")
    ev_ok(d["epoch"].get("evidence", []), "epoch")
    for group, g in d["features"].items():
        if isinstance(g, dict):
            for name, f in g.items():
                if name == "marks":
                    for i, m in enumerate(f):
                        ev_ok(m.get("evidence", []), f"{group}.marks[{i}]")
                        if not m.get("evidence"): errors.append(f"{group}.marks[{i}]: mark without evidence")
                    continue
                allowed = enums.get((group, name))
                if allowed is None: errors.append(f"{group}.{name}: no enumRef in schema"); continue
                if f["value"] not in allowed: errors.append(f"{group}.{name}: value {f['value']!r} not in enumeration")
                ev_ok(f.get("evidence", []), f"{group}.{name}")
                if f["value"] != "unknown" and not f.get("evidence"): errors.append(f"{group}.{name}: non-unknown value without evidence")
        else:
            for i, item in enumerate(g):
                ev_ok(item.get("evidence", []), f"{group}[{i}]")
                if not item.get("evidence"): errors.append(f"{group}[{i}]: entry without evidence")
    return d, errors

def main(paths):
    schema = json.loads(SCHEMA.read_text()); enums = enum_map(schema); bad = 0
    for p in paths:
        d, errors = check(p, schema, enums)
        vals = [f for g in d["features"].values() if isinstance(g, dict) for n, f in g.items() if n != "marks"]
        unknown = sum(1 for f in vals if f["value"] == "unknown")
        print(f"{pathlib.Path(p).name}: {'OK' if not errors else 'FAIL'} · {len(vals)} fields, {unknown} unknown, {sum(len(g) for g in d['features'].values() if isinstance(g, list))} list entries")
        for e in errors: print("   ", e)
        bad += bool(errors)
    if bad: raise SystemExit(f"{bad} descriptor(s) failed")

if __name__ == "__main__": main(sys.argv[1:])
