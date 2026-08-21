#!/usr/bin/env python3
"""Persistent-identity benchmark lanes C, D and E.

Lanes A (SIM) and B (ASA) already exist in the ASALLM workspace and are NOT re-run, NOT altered
and NOT re-graded here; their results are read from the frozen trace file. This module adds three
lanes that hold the file set constant and vary only what is said *about* those files, so any
difference is attributable to persistent identity rather than to retrieval:

  PID_C  ASA's file set + the persistent identity of each file
  PID_D  C + identity provenance (decision history, occurrences, evidence custody)
  PID_E  D + repo-derived negative-space records

The oracle is imported verbatim from the ASALLM runner rather than reimplemented. A benchmark that
grades itself with its own copy of the grader is not comparable to the baseline it claims to beat.

The registry is built from repository content only; task.json never reaches it, preserving the
ground-truth firewall the ASALLM pipeline documents.
"""
import argparse, importlib.util, json, subprocess, sys, time
from pathlib import Path

CTX_TASKS = ["T01", "T03", "T04", "T06", "T07", "T08"]
LANES = ["PID_C", "PID_D", "PID_E"]


def load_asallm(workspace: Path):
    """Imports the frozen runner so chat() and grade() are the originals, byte for byte."""
    spec = importlib.util.spec_from_file_location("asallm_runner", workspace / "run_llm_benchmark.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def registry_identities(jar: Path, registry: Path):
    proc = subprocess.run(
        ["java", "-cp", str(jar), "org.seventeenthsecond.uaofoundry.registry.RegistryApplication",
         "list", "--registry", str(registry)],
        capture_output=True, text=True, check=True)
    return {i["resolutionKey"]: i for i in json.loads(proc.stdout)["identities"]}


def identity_block(identities, files, slug_of):
    """Persistent identity for each supplied file. Address, type, aliases, content identifier."""
    lines = []
    for f in files:
        ident = identities.get(f"foundry:v0.1:file:{slug_of(f)}")
        if not ident:
            lines.append(f"- {f}: NO PERSISTENT IDENTITY REGISTERED")
            continue
        ext = ident.get("externalIdentifiers") or {}
        lines.append(
            f"- {f}\n"
            f"    persistent id : {ident['uid']}\n"
            f"    address       : {ident['resolutionKey']}\n"
            f"    kind          : {ident.get('semanticType')}\n"
            f"    content id    : sha256:{ext.get('sha256','(none)')}\n"
            f"    also known as : {', '.join(ident.get('aliases') or []) or '(none)'}\n"
            f"    status        : {ident.get('lifecycleState')} / {ident.get('semanticVariantStatus')}")
    return "\n".join(lines)


def provenance_block(identities, files, slug_of):
    """How each identity came to be known: decisions, occurrences, evidence custody."""
    lines = []
    for f in files:
        ident = identities.get(f"foundry:v0.1:file:{slug_of(f)}")
        if not ident:
            continue
        decisions = ident.get("decisionHistory") or []
        occurrences = ident.get("occurrences") or []
        states = ident.get("stateVersions") or []
        reasons = sorted({r for d in decisions for r in (d.get("reasonCodes") or [])})
        lines.append(
            f"- {f} ({ident['uid']})\n"
            f"    known from    : {len(occurrences)} verified package occurrence(s)\n"
            f"    distinct states: {len(states)}\n"
            f"    identity basis : {', '.join(reasons) or '(none recorded)'}\n"
            f"    evidence       : {', '.join(sorted({s for d in decisions for s in (d.get('sourceRefs') or [])})) or '(none)'}")
    return "\n".join(lines)


def negative_space_block(ns_records):
    """Repo-derived absence algebra. States what is expected, what was observed, and the scope."""
    if not ns_records:
        return "(no expectation records were derived for this repository)"
    lines = []
    for record in ns_records:
        coords = record.get("coordinates") or {}
        kappa = coords.get("kappa") or {}
        lines.append(
            f"- {record['what']}\n"
            f"    expected  : {(coords.get('Exp') or {}).get('class')} ({(coords.get('Exp') or {}).get('basis')})\n"
            f"    observed  : {coords.get('Obs')}\n"
            f"    scope     : {kappa.get('scope') if kappa else 'unbounded'}\n"
            f"    state     : {record['derived_state']}"
            + (f"\n    note      : {record['note']}" if record.get("note") else ""))
    return "\n".join(lines)


def build_lane_context(lane, task, repo, files, obs_pred, asallm, identities, ns_records, slug_of):
    """Holds ASA's file set constant and varies only the identity material layered on top."""
    exact = [f for f in obs_pred["relevant_files"] if f in files]
    full = "\n\n".join(f"### {f}\n```\n{(repo / f).read_text(errors='replace')}\n```" for f in exact)
    summaries = "\n".join(asallm.summarise(repo, f) for f in files if f not in exact)

    sections = [f"Repository context:\n{full}",
                f"Other repository files (summaries):\n{summaries}",
                "Persistent identities (stable across sessions and renames):\n"
                + identity_block(identities, exact, slug_of)]
    if lane in ("PID_D", "PID_E"):
        sections.append("Identity provenance (how each identity is known):\n"
                        + provenance_block(identities, exact, slug_of))
    if lane == "PID_E":
        sections.append(
            "Expected-versus-observed records, derived from repository state only:\n"
            + negative_space_block(ns_records)
            + "\n\nRead these carefully: MISSING means expected and not found within the stated scope; "
              "PENDING means expected but not yet due; OBSERVED means found. "
              "An absence outside the stated scope is not evidence of non-existence.")

    system = "You are a precise software engineering assistant. Answer the task exactly; be concise."
    user = f"Task: {task['prompt']}\n\n" + "\n\n".join(sections) + "\n\nAnswer now."
    discards = [{"file": f, "reason": f"{lane.lower()}-nonselected(summary provided)", "recoverable": True}
                for f in files if f not in exact]
    return [{"role": "system", "content": system}, {"role": "user", "content": user}], exact, discards


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--registries", required=True, type=Path, help="directory holding <task>/ registries")
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--models", nargs="+", default=["qwen3-coder:30b", "gpt-oss:20b"])
    ap.add_argument("--tasks", nargs="+", default=CTX_TASKS)
    ap.add_argument("--lanes", nargs="+", default=LANES)
    args = ap.parse_args()

    asallm = load_asallm(args.asallm)
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from build_repo_registry import slug as slug_of

    args.out.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with args.out.open("w") as sink:
        for task_id in args.tasks:
            task_dir = args.asallm / "benchmark" / "tasks" / task_id
            task = json.loads((task_dir / "task.json").read_text())
            repo = task_dir / "repo"
            files = asallm.repo_files(repo)
            obs_pred = json.loads((args.asallm / "observer" / f"{task_id}_predictions.json").read_text())
            ns_records = json.loads((args.asallm / "negative_space" / f"{task_id}_ns.json").read_text())

            lookup_start = time.time()
            identities = registry_identities(args.jar, args.registries / task_id)
            lookup_s = round(time.time() - lookup_start, 3)

            for lane in args.lanes:
                for model in args.models:
                    messages, exact, discards = build_lane_context(
                        lane, task, repo, files, obs_pred, asallm, identities, ns_records, slug_of)
                    response = asallm.chat(model, messages)
                    correct = asallm.grade(task["oracle"], response["text"])
                    record = {
                        "task": task_id, "model": model, "policy": lane, "mode": "context",
                        "correct": bool(correct), "exact_files": exact, "n_discarded": len(discards),
                        "relevant_files_truth": task.get("relevant_files", []),
                        "negative_space_truth": task.get("negative_space", []),
                        "registry_lookup_s": lookup_s,
                        "registered_identities": len(identities),
                        "tokens": {"prompt": response["prompt_tokens"], "output": response["output_tokens"]},
                        "latency_s": response["total_s"], "load_s": response["load_s"],
                        "answer": response["text"], "ts": time.time(),
                    }
                    sink.write(json.dumps(record) + "\n")
                    sink.flush()
                    written += 1
                    print(f"{task_id} {lane:6} {model:16} correct={record['correct']} "
                          f"prompt_tok={record['tokens']['prompt']} {record['latency_s']}s", flush=True)
    print(f"wrote {written} records to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
