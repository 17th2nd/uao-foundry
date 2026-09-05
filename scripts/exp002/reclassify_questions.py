#!/usr/bin/env python3
"""Experiment 002 — operator reclassification of open-ended completion questions (0 provider calls).

The adapter's rule: completion questions are the plan's own definition of done and must be answered
from the acquired sources; open matters ("should X be added in a future pass") belong in
scopeResolution.unresolvedQuestions. When a provider nevertheless poses such a question and leaves it
`unresolved`, the package is refused (EVIDENCE_INCOMPLETE) although its evidence is complete. This
script moves each unresolved question whose prompt is a future-scope question into
scopeResolution.unresolvedQuestions, drops it from completionQuestions/coverageAnswers, and records the
reclassification in authorityNotes. Answers are never changed; a question that asks whether evidence
IS present stays where it is, and the refused package remains on disk.
"""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess
FUTURE = re.compile(r"(future|should .* be (added|folded|treated|modeled|manufactured)|separate identity|neighbouring identit)", re.I)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--registry", required=True); ap.add_argument("--package", required=True)
    ap.add_argument("--edition", required=True); ap.add_argument("--out", required=True); ap.add_argument("--jar", default="target/uao-foundry-0.1.0.jar")
    ap.add_argument("--run", action="store_true"); ap.add_argument("--repository-commit", default="local"); ap.add_argument("--seed", help="override the identity seed (label stays the provider's)")
    a = ap.parse_args(); pkg = pathlib.Path(a.package); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    snap = json.loads((pkg / "provider-snapshot.json").read_text()); refused = json.loads((pkg / "manifest.json").read_text())["packageId"]
    bundle = json.loads(json.dumps(snap)); moved = []
    plan = bundle["manufacturingPlan"]; answers = bundle["coverageAnswers"]
    keep = []
    for q in plan["completionQuestions"]:
        if answers.get(q["questionId"], "unresolved") == "unresolved" and FUTURE.search(q["prompt"]):
            moved.append(q["prompt"]); answers.pop(q["questionId"], None)
        else: keep.append(q)
    if not moved: raise SystemExit("nothing to reclassify")
    plan["completionQuestions"] = keep
    uq = bundle["scopeResolution"]["unresolvedQuestions"]
    for m in moved:
        if m not in uq: uq.append(m)
    if a.seed: bundle["identitySeed"] = a.seed
    bundle["sourceStrategy"]["authorityNotes"] += [f"Operator reclassification from refused package {refused}: {len(moved)} open-ended completion question(s) moved to scopeResolution.unresolvedQuestions (they ask about future scope, not about evidence present). Coverage answers unchanged; no additional provider call."]
    path = out / (re.sub(r"[^a-z0-9]+", "-", bundle["identitySeed"].lower()).strip("-")[:60] + "-reclassified.json"); path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    print(f"{bundle['identitySeed']}: moved {len(moved)} question(s); remaining {len(keep)} → {path}")
    for m in moved: print("   -", m)
    if a.run:
        cmd = ["java", "-cp", a.jar, "org.seventeenthsecond.uaofoundry.console.OperatorConsole", "manufacture", bundle["identitySeed"], "--registry", a.registry, "--fixture", str(path),
               "--relationship-edition", a.edition, "--register", "--json", "--work-dir", str(out / "work"), "--dist-dir", str(out / "dist"), "--repository-commit", a.repository_commit, "--context", f"reclassified from refused package {refused}"]
        r = subprocess.run(cmd, capture_output=True, text=True); line = (r.stdout.strip().splitlines() or [""])[-1]
        try: rep = json.loads(line); print("   →", rep["publicationStatus"], rep["registryAdmission"], rep["counts"])
        except Exception: print("   → FAILED", (r.stderr or r.stdout)[-1200:])

if __name__ == "__main__": main()
