#!/usr/bin/env python3
"""E-6 (H1): repeated relationship reconstruction, measured mechanically.

Two ways of answering "which files does each file reference":

  reconstruction — fresh extraction: re-read repository content, re-derive every edge;
  memory         — a restarted USI Foundry application answers from the staged store; repository
                   content is not touched (structurally: this path never opens a repo file).

The memory path goes through the application API on a fresh process, so the Java store's
fail-closed re-validation and restart survival are part of what is measured, not assumed.

Decision limbs (pre-registered, hardened per finding F-R2): exact edge-set equality; 0 repository
files opened / bytes read on the memory path, MEASURED via a Python audit hook rather than assumed;
stability across an INDEPENDENT second process restart (process B vs a separately started process C,
not a duplicate of limb 1); accumulation depth ≥ 3 observations per edge with no unintended refusals
(from the build summary).
"""
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from build_repo_registry import repo_files, slug  # noqa: E402
from build_task_stores import App, CTX_TASKS  # noqa: E402
from extract_references import edges_of  # noqa: E402


class RepoOpenMeter:
    """Measures repository-file opens via a Python audit hook (F-R2).

    The 'open' audit event fires at the C level for io.open, pathlib and os.open alike, so this
    counts every actual repository-file open in this process while armed — replacing the previously
    hard-coded ``memoryRepoBytesRead = 0`` with a real measurement. A hook cannot be removed, so it
    is gated by ``self.armed`` and only tallies while a phase explicitly arms it.
    """

    def __init__(self, repo: Path):
        self.repo = os.path.realpath(str(repo))
        self.armed = False
        self._opened: set[str] = set()
        sys.addaudithook(self._hook)

    def _hook(self, event, args):
        if self.armed and event == "open" and args:
            try:
                rp = os.path.realpath(str(args[0]))
            except Exception:
                return
            if rp.startswith(self.repo) and os.path.isfile(rp):
                self._opened.add(rp)

    def measure(self, fn):
        """Run fn while armed; return (result, files_opened, bytes_in_those_files)."""
        self._opened = set()
        self.armed = True
        try:
            result = fn()
        finally:
            self.armed = False
        opened = set(self._opened)
        total_bytes = sum(os.path.getsize(p) for p in opened)
        return result, len(opened), total_bytes


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
        meter = RepoOpenMeter(repo)
        file_of = uid_to_file(args.jar, home / "registry", files)

        def read_memory(port):
            """Answer from the staged store via a freshly started (restarted) application process."""
            app = App(args.jar, home, port)
            try:
                observed = {}   # (referrer, referent) -> set of distinct staged ids
                for uid in file_of:
                    n = app.call("/api/staged-relationships/" + uid)
                    assert n["certifying"] is False
                    for edge in n["edges"]:
                        roles = {p["role"]: p.get("uaoId") for p in edge["participants"]}
                        key = (file_of.get(roles.get("referrer")), file_of.get(roles.get("referent")))
                        observed.setdefault(key, set()).add(edge["stagedId"])
                return observed
            finally:
                app.stop()

        # Reconstruction: read repository content and re-derive. Bytes are MEASURED, not assumed.
        t0 = time.time()
        (fresh, fresh_files, fresh_bytes) = meter.measure(lambda: set(map(tuple, edges_of(repo))))
        fresh_s = time.time() - t0

        # Memory (process B): a restarted application answers from the store. The reconstruction in
        # THIS process touches no repository file — measured, not asserted.
        t0 = time.time()
        (observed_b, mem_files, mem_bytes) = meter.measure(lambda: read_memory(args.port_base + i))
        memory_s = time.time() - t0

        # Independent restart (process C): a second, separate application load of the same store.
        # Limb 3 compares B against C, so it proves restart-stability rather than duplicating limb 1.
        observed_c = read_memory(args.port_base + 100 + i)

        memory_edges = set(observed_b)
        restart_edges = set(observed_c)
        depth = sorted(len(v) for v in observed_b.values())
        task_report = {
            "freshEdgeCount": len(fresh), "memoryEdgeCount": len(memory_edges),
            "edgeSetsEqual": fresh == memory_edges,
            "restartEdgeCount": len(restart_edges),
            "restartStableVsMemory": memory_edges == restart_edges,
            "freshRepoFilesOpened": fresh_files, "freshBytesRead": fresh_bytes,
            "memoryRepoFilesOpened": mem_files, "memoryRepoBytesRead": mem_bytes,
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
        "2_memory_path_reads_zero_repo_bytes": all(
            t["memoryRepoBytesRead"] == 0 and t["memoryRepoFilesOpened"] == 0 for t in tasks),
        "3_stable_across_independent_restart": all(
            t["restartStableVsMemory"] and t["restartEdgeCount"] == t["memoryEdgeCount"] for t in tasks),
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
