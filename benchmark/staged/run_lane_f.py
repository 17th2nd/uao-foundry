#!/usr/bin/env python3
"""E-7 (H2) LLM lane PID_F: lane B's exact file set plus staged relationship memory.

Isolation mirrors the C/D/E design: the file set is held constant at lane B's, and the ONLY
addition is the staged relationship block — no identity, provenance or negative-space material —
so F−B attributes to relationship memory exactly as C−B attributed to identity.

Frozen ASALLM runner supplies chat(), grade(), summarise() and repo_files() verbatim; lanes
A/B/C/D/E are read from frozen traces elsewhere and are not re-run here.
"""
import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from build_repo_registry import repo_files as registry_repo_files, slug  # noqa: E402,F401
from build_task_stores import CTX_TASKS  # noqa: E402
from h1_reconstruction import uid_to_file  # noqa: E402
from run_lanes import load_asallm  # noqa: E402


def staged_edges(home: Path, file_of: dict):
    """Distinct staged edges as (referrer file, referent file, observations, evidence, packages)."""
    edges = {}
    store = home / "staged-relationships"
    for path in sorted(store.glob("stg-*.json")):
        record = json.loads(path.read_text())
        assert record["certifying"] is False and record["status"] == "NON_CANONICAL_CANDIDATE_MEMORY"
        roles = {p["role"]: p.get("uaoId") for p in record["participants"]}
        referrer, referent = file_of.get(roles.get("referrer")), file_of.get(roles.get("referent"))
        if not referrer or not referent:
            continue
        entry = edges.setdefault((referrer, referent), {
            "uids": (roles["referrer"], roles["referent"]),
            "observations": 0, "evidence": set(), "packages": set()})
        entry["observations"] += 1
        entry["evidence"].update(record.get("sourceRefs") or [])
        entry["packages"].add(record["packageId"])
    return edges


def staged_block(edges, exact):
    """The relationship-memory context section for the files supplied in full."""
    lines = []
    for (referrer, referent), e in sorted(edges.items()):
        if referrer not in exact and referent not in exact:
            continue
        lines.append(
            f"- {referrer} references {referent}\n"
            f"    persistent ids : {e['uids'][0]} -> {e['uids'][1]}\n"
            f"    observed       : {e['observations']} time(s) across {len(e['packages'])} package(s)\n"
            f"    evidence       : {', '.join(sorted(e['evidence'])) or '(none)'}")
    if not lines:
        return "(no staged relationship memory mentions these files)"
    return "\n".join(lines)


def build_context(task, repo, files, obs_pred, asallm, edges):
    exact = [f for f in obs_pred["relevant_files"] if f in files]
    full = "\n\n".join(f"### {f}\n```\n{(repo / f).read_text(errors='replace')}\n```" for f in exact)
    summaries = "\n".join(asallm.summarise(repo, f) for f in files if f not in exact)
    sections = [
        f"Repository context:\n{full}",
        f"Other repository files (summaries):\n{summaries}",
        "Persistent relationship memory (staged, NON-CANONICAL candidate memory — these edges were "
        "observed and recorded across earlier sessions; they are asserted, not governed):\n"
        + staged_block(edges, set(exact)),
    ]
    system = "You are a precise software engineering assistant. Answer the task exactly; be concise."
    user = f"Task: {task['prompt']}\n\n" + "\n\n".join(sections) + "\n\nAnswer now."
    return [{"role": "system", "content": system}, {"role": "user", "content": user}], exact


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--stores-root", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--models", nargs="+", default=["qwen3-coder:30b", "gpt-oss:20b"])
    ap.add_argument("--tasks", nargs="+", default=CTX_TASKS)
    args = ap.parse_args()

    asallm = load_asallm(args.asallm)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with args.out.open("w") as sink:
        for task_id in args.tasks:
            task_dir = args.asallm / "benchmark" / "tasks" / task_id
            task = json.loads((task_dir / "task.json").read_text())
            repo = task_dir / "repo"
            files = asallm.repo_files(repo)
            obs_pred = json.loads((args.asallm / "observer" / f"{task_id}_predictions.json").read_text())

            home = args.stores_root / f"{task_id}-home"
            t0 = time.time()
            file_of = uid_to_file(args.jar, home / "registry", files)
            edges = staged_edges(home, file_of)
            lookup_s = round(time.time() - t0, 3)

            for model in args.models:
                messages, exact = build_context(task, repo, files, obs_pred, asallm, edges)
                response = asallm.chat(model, messages)
                correct = asallm.grade(task["oracle"], response["text"])
                record = {
                    "task": task_id, "model": model, "policy": "PID_F", "mode": "context",
                    "correct": bool(correct), "exact_files": exact,
                    "relevant_files_truth": task.get("relevant_files", []),
                    "staged_edges_total": len(edges), "registry_lookup_s": lookup_s,
                    "tokens": {"prompt": response["prompt_tokens"], "output": response["output_tokens"]},
                    "latency_s": response["total_s"], "load_s": response["load_s"],
                    "answer": response["text"], "ts": time.time(),
                }
                sink.write(json.dumps(record) + "\n")
                sink.flush()
                written += 1
                print(f"{task_id} PID_F  {model:16} correct={record['correct']} "
                      f"prompt_tok={record['tokens']['prompt']} {record['latency_s']}s", flush=True)
    print(f"wrote {written} records to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
