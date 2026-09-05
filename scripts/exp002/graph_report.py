#!/usr/bin/env python3
"""Experiment 002 Phase 6/11 — graph traversal proof and reuse accounting from a registry.

Reads `graph --json` output (or the registry index directly), runs breadth-first traversals from named
start identities, prints a human-readable path listing, and aggregates the run records beside the
registry into the reuse ledger the founder asked for. Everything printed is derived; nothing is judged.
"""
from __future__ import annotations
import argparse, collections, json, pathlib, subprocess

def load_graph(registry, jar):
    out = subprocess.run(["java", "-cp", jar, "org.seventeenthsecond.uaofoundry.console.OperatorConsole", "graph", "--registry", str(registry), "--json"], capture_output=True, text=True, check=True).stdout
    return json.loads(out.strip().splitlines()[-1])

def traverse(graph, start_uid, max_depth=4):
    labels = {n["uid"]: n["labels"][0] for n in graph["nodes"]}
    adj = collections.defaultdict(list)
    for e in graph["edges"]:
        uids = [p["uaoId"] for p in e["participants"]]
        for a in uids:
            for b in uids:
                if a != b: adj[a].append((b, e))
    seen = {start_uid: None}; order = [(start_uid, 0)]; q = collections.deque([(start_uid, 0)]); paths = {start_uid: []}
    while q:
        u, d = q.popleft()
        if d >= max_depth: continue
        for v, e in adj[u]:
            if v in seen: continue
            seen[v] = u; paths[v] = paths[u] + [(u, e, v)]; q.append((v, d + 1)); order.append((v, d + 1))
    return labels, order, paths

def render_path(labels, path):
    if not path: return "(start)"
    s = labels[path[0][0]]
    for u, e, v in path:
        arrow = f"—{e['typeName']}→" if not e["symmetric"] else f"—{e['typeName']}—"
        # direction: if v is the object side render forward, else render inverse
        subj = e["statement"].split(" —")[0]
        s += f" {arrow} {labels[v]}" if subj.startswith(labels[u][:20]) else f" ←{e['typeName']}— {labels[v]}"
    return s

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--registry", required=True); ap.add_argument("--jar", default="target/uao-foundry-0.1.0.jar")
    ap.add_argument("--start", action="append", default=[], help="start label substring (repeatable)"); ap.add_argument("--out", required=True); a = ap.parse_args()
    registry = pathlib.Path(a.registry); graph = load_graph(registry, a.jar)
    out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    (out / "graph.json").write_text(json.dumps(graph, indent=2, ensure_ascii=False) + "\n")
    labels = {n["uid"]: n["labels"][0] for n in graph["nodes"]}
    lines = [f"# Graph: {len(graph['nodes'])} identities · {len(graph['edges'])} experimental typed relationships (certifying=false)", ""]
    for e in sorted(graph["edges"], key=lambda e: e["statement"]): lines.append(f"- {e['statement']}  [{e['basis']}, {e['outcome']}, occurrences {e['occurrenceCount']}]")
    traversals = {}
    for start in a.start:
        uid = next((u for u, l in labels.items() if start.lower() == l.lower()), None) or next((u for u, l in labels.items() if start.lower() in l.lower()), None)
        if not uid: lines += ["", f"## Traversal from '{start}': no such identity"]; continue
        lab, order, paths = traverse(graph, uid)
        lines += ["", f"## Traversal from {labels[uid]} ({uid})"]
        reach = []
        for v, d in order[1:]:
            lines.append(f"- depth {d}: {render_path(lab, paths[v])}"); reach.append({"uid": v, "label": labels[v], "depth": d, "path": [(u, e["relationshipId"], w) for u, e, w in paths[v]]})
        if len(order) == 1: lines.append("- (no registered relationship leaves this identity)")
        traversals[start] = {"startUid": uid, "reachable": reach}
    (out / "traversals.json").write_text(json.dumps(traversals, indent=2, ensure_ascii=False) + "\n")
    # reuse ledger from run records
    runs = []
    for f in sorted((registry.parent / "runs").glob("run-*.json")):
        r = json.loads(f.read_text()); rr = r.get("reuseReport") or {}; c = rr.get("counts", {})
        runs.append({"runId": r["runId"], "seed": r["identitySeed"], "provider": r["provider"], "status": r["status"], "packageId": r.get("packageId"), "startedAt": r["startedAt"],
                     "reusedUao": c.get("reusedUaoCount", 0), "newUao": c.get("newUaoCount", 0), "registrySources": c.get("registrySourceCount", 0), "newSources": c.get("newSourceCount", 0)})
    (out / "runs.json").write_text(json.dumps(runs, indent=2) + "\n")
    lines += ["", "## Run ledger", "", "| started | seed | provider | status | reused | new | registry sources | new sources |", "|---|---|---|---|---|---|---|---|"]
    for r in sorted(runs, key=lambda r: r["startedAt"]): lines.append(f"| {r['startedAt'][:19]} | {r['seed']} | {r['provider']} | {r['status']} | {r['reusedUao']} | {r['newUao']} | {r['registrySources']} | {r['newSources']} |")
    (out / "GRAPH.md").write_text("\n".join(lines) + "\n"); print("\n".join(lines[:3])); print(f"... written {out}/GRAPH.md, graph.json, traversals.json, runs.json")

if __name__ == "__main__": main()
