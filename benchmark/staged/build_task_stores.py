#!/usr/bin/env python3
"""Session 1 of E-6/E-7: build per-task identity registries and staged relationship stores.

Everything goes through the running USI Foundry application — the shipped operator flow — into
disposable homes under --out-root. Per round, every repository file is manufactured and registered,
then every extracted reference edge is manufactured as a relationship-bearing bundle: refused
admission under ASA#29 and staged as non-canonical candidate memory. Multiple rounds prove
observations accumulate past the third (the depth P9-1 used to cap).

  build_task_stores.py --asallm <ws> --jar <jar> --out-root <dir> [--tasks ...] [--rounds 3]
"""
import argparse
import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from build_repo_registry import bundle_for, repo_files, slug  # noqa: E402
from extract_references import edge_bundle, edges_of  # noqa: E402

CTX_TASKS = ["T01", "T03", "T04", "T06", "T07", "T08"]


class App:
    """One USI Foundry application instance over a disposable home."""

    def __init__(self, jar: Path, home: Path, port: int):
        self.base = f"http://127.0.0.1:{port}"
        self.proc = subprocess.Popen(
            ["java", "-cp", str(jar), "org.seventeenthsecond.usifoundry.UsiFoundryApp",
             "--home", str(home), "--port", str(port), "--no-open"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(60):
            try:
                self.call("/api/status")
                return
            except Exception:
                time.sleep(0.5)
        raise RuntimeError("application did not come up")

    def call(self, path, payload=None):
        req = urllib.request.Request(self.base + path) if payload is None else urllib.request.Request(
            self.base + path, data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=300) as response:
            return json.load(response)

    def manufacture(self, seed: str, bundle: dict, fixture_path: Path, register: bool):
        fixture_path.write_text(json.dumps(bundle, indent=1))
        token = self.call("/api/manufacture", {
            "identity": seed, "provider": "fixture",
            "fixture": str(fixture_path), "register": register})["jobToken"]
        while True:
            status = self.call("/api/manufacture/" + token)
            if status["state"] != "RUNNING":
                return status
            time.sleep(0.15)

    def stop(self):
        self.proc.terminate()
        self.proc.wait(timeout=30)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--out-root", required=True, type=Path)
    ap.add_argument("--tasks", nargs="+", default=CTX_TASKS)
    ap.add_argument("--rounds", type=int, default=3)
    ap.add_argument("--port-base", type=int, default=7801)
    ap.add_argument("--summary", type=Path, required=True)
    args = ap.parse_args()

    summary = {"rounds": args.rounds, "tasks": {}}
    for i, task_id in enumerate(args.tasks):
        repo = args.asallm / "benchmark" / "tasks" / task_id / "repo"
        home = args.out_root / f"{task_id}-home"
        fixtures = args.out_root / f"{task_id}-fixtures"
        fixtures.mkdir(parents=True, exist_ok=True)
        files = repo_files(repo)
        edges = edges_of(repo)

        app = App(args.jar, home, args.port_base + i)
        counts = {"files": len(files), "edges": len(edges),
                  "registered": 0, "edgeRefused": 0, "unintendedFailures": []}
        try:
            for round_no in range(1, args.rounds + 1):
                for rel in files:
                    status = app.manufacture(rel, bundle_for(repo, rel),
                                             fixtures / (slug(rel) + ".json"), register=True)
                    result = status.get("result") or {}
                    if status["state"] == "COMPLETE" and result.get("registryAdmission") == "REGISTERED":
                        counts["registered"] += 1
                    else:
                        counts["unintendedFailures"].append({
                            "round": round_no, "what": rel,
                            "detail": (status.get("failure") or result or {})})
                for referrer, referent in edges:
                    bundle = edge_bundle(repo, referrer, referent)
                    status = app.manufacture(bundle["identitySeed"], bundle,
                                             fixtures / f"edge-{slug(referrer)}--{slug(referent)}.json",
                                             register=True)
                    result = status.get("result") or {}
                    # REFUSED admission with authority named is the intended fail-closed path.
                    if status["state"] == "COMPLETE" and result.get("registryAdmission") == "REFUSED" \
                            and result.get("relationshipAuthority") == "URO_TYPE_AUTHORITY_UNAVAILABLE":
                        counts["edgeRefused"] += 1
                    else:
                        counts["unintendedFailures"].append({
                            "round": round_no, "what": f"{referrer} -> {referent}",
                            "detail": (status.get("failure") or result or {})})
            plant = app.call("/api/status")
            counts["stagedRelationshipCount"] = plant.get("stagedRelationshipCount")
            counts["registryVerification"] = plant.get("registryVerification")
        finally:
            app.stop()
        summary["tasks"][task_id] = counts
        print(f"{task_id}: files={counts['files']} edges={counts['edges']} "
              f"registered={counts['registered']} edgeRefused={counts['edgeRefused']} "
              f"staged={counts.get('stagedRelationshipCount')} "
              f"registry={counts.get('registryVerification')} "
              f"unintended={len(counts['unintendedFailures'])}", flush=True)

    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=1))
    bad = sum(len(t["unintendedFailures"]) for t in summary["tasks"].values())
    print(f"build complete; unintended failures: {bad}")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
