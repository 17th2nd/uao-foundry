#!/usr/bin/env python3
"""E-6/E-7 analysis: H1 limbs, H2 mechanical precision, and lane PID_F against the frozen lanes.

Frozen lane records (A/B from the ASALLM trace, C/D/E from pid_lanes.jsonl) are collected with the
SAME code E-3 used — imported from analyse_lanes, not copied — and are not re-run or re-graded.
"""
import argparse
import json
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from analyse_lanes import collect, recall  # noqa: E402
from build_task_stores import CTX_TASKS  # noqa: E402
from h1_reconstruction import uid_to_file  # noqa: E402
from run_lane_f import staged_edges  # noqa: E402

NOISE_FLOOR = 1 / 12


def lane_summary(rows, truths, lane):
    lane_rows = [r for r in rows if r["lane"] == lane]
    if not lane_rows:
        return None
    recalls = [recall(r["exact_files"], truths[r["task"]]["relevant_files"]) for r in lane_rows]
    recalls = [r for r in recalls if r is not None]
    return {
        "n": len(lane_rows),
        "success": round(sum(r["correct"] for r in lane_rows) / len(lane_rows), 3),
        "recall": round(sum(recalls) / len(recalls), 3) if recalls else None,
        "medianPromptTokens": int(statistics.median(r["prompt_tokens"] for r in lane_rows)),
    }


def graph_prediction(task, repo_file_list, edges):
    """Seeds = repository files named in the task prompt; prediction = seeds + staged neighbours."""
    prompt = task["prompt"]
    seeds = {f for f in repo_file_list if Path(f).name in prompt or f in prompt}
    neighbours = set()
    for (referrer, referent) in edges:
        if referrer in seeds:
            neighbours.add(referent)
        if referent in seeds:
            neighbours.add(referrer)
    return sorted(seeds | neighbours)


def precision_recall(predicted, truth):
    if not predicted:
        return 0.0, 0.0 if truth else None
    hits = sum(1 for t in truth if (t in predicted if not t.endswith("/")
                                    else any(p.startswith(t) for p in predicted)))
    covered = sum(1 for p in predicted if p in truth
                  or any(t.endswith("/") and p.startswith(t) for t in truth))
    return round(covered / len(predicted), 3), round(hits / len(truth), 3) if truth else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--lanes", required=True, type=Path, help="frozen pid_lanes.jsonl")
    ap.add_argument("--staged-lanes", required=True, type=Path)
    ap.add_argument("--h1", required=True, type=Path)
    ap.add_argument("--stores-root", required=True, type=Path)
    ap.add_argument("--tasks", nargs="+", default=CTX_TASKS)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    rows, truths, errored = collect(args.asallm, args.lanes, args.tasks)
    for line in args.staged_lanes.read_text().splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        rows.append({"task": r["task"], "model": r["model"], "lane": r["policy"],
                     "correct": bool(r["correct"]), "exact_files": r.get("exact_files") or [],
                     "prompt_tokens": (r.get("tokens") or {}).get("prompt") or 0,
                     "latency_s": r.get("latency_s") or 0, "answer": r.get("answer") or "",
                     "registry_lookup_s": r.get("registry_lookup_s"), "source": "staged-rerun"})

    lanes = {}
    for lane in ["SIM", "ASA", "PID_C", "PID_D", "PID_E", "PID_F"]:
        summary = lane_summary(rows, truths, lane)
        if summary:
            lanes[lane] = summary

    # H2 mechanical: staged-graph file prediction vs the frozen observer prediction lane B used.
    from run_lanes import load_asallm
    asallm = load_asallm(args.asallm)
    mechanical = {}
    for task_id in args.tasks:
        task_dir = args.asallm / "benchmark" / "tasks" / task_id
        task = json.loads((task_dir / "task.json").read_text())
        repo_file_list = asallm.repo_files(task_dir / "repo")
        home = args.stores_root / f"{task_id}-home"
        file_of = uid_to_file(args.jar, home / "registry", repo_file_list)
        edges = staged_edges(home, file_of)
        graph_pred = graph_prediction(task, repo_file_list, edges)
        obs_pred = json.loads((args.asallm / "observer" / f"{task_id}_predictions.json").read_text())
        truth = task.get("relevant_files", [])
        gp, gr = precision_recall(graph_pred, truth)
        op, orr = precision_recall([f for f in obs_pred["relevant_files"] if f in repo_file_list], truth)
        mechanical[task_id] = {
            "graph": {"predicted": graph_pred, "precision": gp, "recall": gr},
            "observer_frozen": {"precision": op, "recall": orr},
        }

    def mean(values):
        values = [v for v in values if v is not None]
        return round(sum(values) / len(values), 3) if values else None

    h1 = json.loads(args.h1.read_text())
    delta = None
    if "PID_F" in lanes and "ASA" in lanes:
        delta = round(lanes["PID_F"]["success"] - lanes["ASA"]["success"], 3)

    mech_graph_recall = mean(m["graph"]["recall"] for m in mechanical.values())
    mech_graph_precision = mean(m["graph"]["precision"] for m in mechanical.values())
    mech_obs_recall = mean(m["observer_frozen"]["recall"] for m in mechanical.values())
    mech_obs_precision = mean(m["observer_frozen"]["precision"] for m in mechanical.values())

    out = {
        "lanes": lanes,
        "errored_frozen_runs": errored,
        "H1": {"limbs": h1["limbs"], "supported": h1["H1_supported"], "perTask": h1["tasks"]},
        "H2_mechanical": {
            "perTask": mechanical,
            "graphMeanPrecision": mech_graph_precision, "graphMeanRecall": mech_graph_recall,
            "observerMeanPrecision": mech_obs_precision, "observerMeanRecall": mech_obs_recall,
            "supported": (mech_graph_recall is not None and mech_obs_recall is not None
                          and mech_graph_recall >= mech_obs_recall
                          and mech_graph_precision >= mech_obs_precision),
        },
        "H2_llm": {
            "successDeltaFvsB": delta,
            "noiseFloor": round(NOISE_FLOOR, 3),
            "verdict": None if delta is None else (
                "no effect (within noise floor)" if abs(delta) <= NOISE_FLOOR else
                ("above noise floor, positive" if delta > 0 else "above noise floor, negative")),
        },
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(out, indent=1))
    print(json.dumps({k: v for k, v in out.items() if k != "H2_mechanical"}, indent=1))
    print("H2 mechanical: graph", mech_graph_precision, mech_graph_recall,
          "vs observer", mech_obs_precision, mech_obs_recall,
          "=> supported:", out["H2_mechanical"]["supported"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
