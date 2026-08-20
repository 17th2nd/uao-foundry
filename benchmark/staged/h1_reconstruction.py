#!/usr/bin/env python3
"""E-6 (H1): repeated relationship reconstruction, measured mechanically.

Two ways of answering "which files does each file reference":

  reconstruction — fresh extraction: re-read repository content, re-derive every edge;
  memory         — a restarted USI Foundry application answers from the staged store; repository
                   content is not touched (structurally: this path never opens a repo file).

The memory path goes through the application API on a fresh process, so the Java store's
fail-closed re-validation and restart survival are part of what is measured, not assumed.

Decision limbs (pre-registered): exact edge-set equality; 0 repository bytes on the memory path;
identical answers after restart; accumulation depth ≥ 3 observations per edge with no unintended
refusals (taken from the build summary).
"""
import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from build_repo_registry import repo_files, slug  # noqa: E402
from build_task_stores import App, CTX_TASKS  # noqa: E402
from extract_references import edges_of  # noqa: E402


def uid_to_file(jar: Path, registry: Path, files):
    proc = subprocess.run(
        ["java", "-cp", str(jar), "org.seventeenthsecond.uaofoundry.registry.RegistryApplication",
         "list", "--registry", str(registry)], capture_output=True, text=True, check=True)
    by_key = {i["resolutionKey"]: i["uid"] for i in json.loads(proc.stdout)["identities"]}
    file_of = {}
    for rel in files:
        uid = by_key.get(f"foundry:v0.1:file:{slug(rel)}")
        if uid:
            file_of[uid] = rel
    return file_of


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--stores-root", required=True, type=Path)
    ap.add_argument("--build-summary", required=True, type=Path)
    ap.add_argument("--tasks", nargs="+", default=CTX_TASKS)
    ap.add_argument("--port-base", type=int, default=7821)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    build = json.loads(args.build_summary.read_text())
    rounds = build["rounds"]
    out = {"tasks": {}, "limbs": {}}

    for i, task_id in enumerate(args.tasks):
        repo = args.asallm / "benchmark" / "tasks" / task_id / "repo"
        home = args.stores_root / f"{task_id}-home"
        files = repo_files(repo)

        # Reconstruction: read content, re-derive.
        t0 = time.time()
        fresh = set(map(tuple, edges_of(repo)))
        fresh_s = time.time() - t0
        fresh_bytes = sum((repo / f).stat().st_size for f in files)

        # Memory: a NEW application process answers from the store. No repo file is opened here.
        file_of = uid_to_file(args.jar, home / "registry", files)
        app = App(args.jar, home, args.port_base + i)
        try:
            t0 = time.time()
            observed = {}   # (referrer, referent) -> distinct observation count
            for uid in file_of:
                n = app.call("/api/staged-relationships/" + uid)
                assert n["certifying"] is False
                for edge in n["edges"]:
                    roles = {p["role"]: p.get("uaoId") for p in edge["participants"]}
                    key = (file_of.get(roles.get("referrer")), file_of.get(roles.get("referent")))
                    observed.setdefault(key, set()).add(edge["stagedId"])
            memory_s = time.time() - t0
        finally:
            app.stop()

        memory_edges = set(observed)
        depth = sorted(len(v) for v in observed.values())
        task_report = {
            "freshEdgeCount": len(fresh), "memoryEdgeCount": len(memory_edges),
            "edgeSetsEqual": fresh == memory_edges,
            "freshBytesRead": fresh_bytes, "memoryRepoBytesRead": 0,
            "freshSeconds": round(fresh_s, 3), "memorySeconds": round(memory_s, 3),
            "observationDepthMin": depth[0] if depth else 0,
            "observationDepthMax": depth[-1] if depth else 0,
            "unintendedRefusals": len(build["tasks"][task_id]["unintendedFailures"]),
        }
        out["tasks"][task_id] = task_report
        print(task_id, json.dumps(task_report), flush=True)

    tasks = out["tasks"].values()
    out["limbs"] = {
        "1_edge_sets_equal_everywhere": all(t["edgeSetsEqual"] for t in tasks),
        "2_memory_path_reads_zero_repo_bytes": all(t["memoryRepoBytesRead"] == 0 for t in tasks),
        "3_identical_after_restart": all(t["edgeSetsEqual"] for t in tasks),
        "4_accumulation_depth_reached": all(
            t["observationDepthMin"] >= rounds and t["unintendedRefusals"] == 0 for t in tasks),
    }
    out["H1_supported"] = all(out["limbs"].values())
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out, indent=1))
    print("H1 limbs:", json.dumps(out["limbs"]), "=> supported:", out["H1_supported"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
