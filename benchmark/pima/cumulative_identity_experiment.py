#!/usr/bin/env python3
"""Does persistent identity survive a codebase that changes?

Builds ONE registry incrementally across every task repository variant, in order, and records what
happens to each file identity. The variants share most files byte-for-byte and differ in the few
that carry each task's planted defect, so this is a natural experiment in exactly the situation
persistent identity is supposed to help with: the same codebase, observed repeatedly, changing a
little each time.

Measures H3 (duplicate/conflicting entities) directly, and surfaces what the fail-closed reuse
discipline does when content moves under a stable address.

No model is involved. This is a property of the machine, not of any LLM.
"""
import argparse, json, subprocess, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_repo_registry import bundle_for, repo_files, slug


def manufacture(jar, registry, fixture, seed, work, dist):
    return subprocess.run(
        ["java", "-cp", str(jar), "org.seventeenthsecond.uaofoundry.console.OperatorConsole",
         "manufacture", seed, "--registry", str(registry), "--fixture", str(fixture),
         "--work-dir", str(work), "--dist-dir", str(dist), "--register", "--json"],
        capture_output=True, text=True)


def index(jar, registry):
    proc = subprocess.run(
        ["java", "-cp", str(jar), "org.seventeenthsecond.uaofoundry.registry.RegistryApplication",
         "list", "--registry", str(registry)], capture_output=True, text=True)
    return json.loads(proc.stdout) if proc.returncode == 0 else {"identities": [], "packages": []}


def classify(reason: str) -> str:
    """Separates the two genuinely different causes of a refused cumulative manufacture."""
    if "EXTERNAL_IDENTIFIER_CONTRADICTION" in reason:
        # Content moved under a stable path address. A design consequence of addressing a file by
        # path while carrying its content hash as durable external identity.
        return "CONTENT_CHANGED_UNDER_STABLE_ADDRESS"
    if "package-id collision" in reason or "Package output collision" in reason:
        # Finding P9-1: same meaning-bearing content, different registry-relative reuse report.
        return "PACKAGE_ID_COLLISION_P9_1"
    return "OTHER"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--asallm", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--registry", required=True, type=Path)
    ap.add_argument("--scratch", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--tasks", nargs="+", default=["T01", "T03", "T04", "T06", "T07", "T08"])
    args = ap.parse_args()

    fixtures = args.scratch / "fixtures"
    fixtures.mkdir(parents=True, exist_ok=True)
    work = args.scratch / "work"

    steps, refusals = [], []
    for task_id in args.tasks:
        repo = args.asallm / "benchmark" / "tasks" / task_id / "repo"
        # A per-observation dist directory isolates finding P9-1 (a package-id collision caused by
        # reuse-report.json living inside a content-addressed package but outside the content
        # address) from the identity behaviour actually under test. The registry-level half of
        # P9-1 still fires and is classified below rather than hidden.
        dist = args.scratch / "dist" / task_id
        reused = created = refused = 0
        for rel in repo_files(repo):
            fixture = fixtures / f"{task_id}-{slug(rel)}.json"
            fixture.write_text(json.dumps(bundle_for(repo, rel), indent=2))
            proc = manufacture(args.jar, args.registry, fixture, rel, work, dist)
            if proc.returncode != 0:
                refused += 1
                message = (proc.stderr or proc.stdout).strip().splitlines()
                reason = message[0][:220] if message else "(no message)"
                refusals.append({"task": task_id, "file": rel, "reason": reason,
                                 "cause": classify(reason)})
                continue
            report = json.loads(proc.stdout)
            admission = report["registryAdmission"]
            if admission != "REGISTERED":
                refused += 1
                refusals.append({"task": task_id, "file": rel, "reason": admission[:220],
                                 "cause": classify(admission)})
                continue
            counts = report["counts"]
            reused += counts["existingIdentitiesReused"]
            created += counts["newIdentitiesManufactured"]
        current = index(args.jar, args.registry)
        steps.append({"task": task_id, "reused": reused, "created": created, "refused": refused,
                      "registryIdentities": len(current["identities"]),
                      "registryPackages": len(current["packages"])})
        print(f"{task_id}: reused={reused} new={created} refused={refused} "
              f"registry_identities={len(current['identities'])}", flush=True)

    final = index(args.jar, args.registry)
    identities = final["identities"]
    # H3: one identity per addressed thing. A duplicate would be two uids for one address.
    by_key = {}
    for identity in identities:
        by_key.setdefault(identity["resolutionKey"], set()).add(identity["uid"])
    duplicates = {k: sorted(v) for k, v in by_key.items() if len(v) > 1}
    unreconciled = [i["resolutionKey"] for i in identities
                    if i.get("semanticVariantStatus") == "MULTIPLE_UNRECONCILED_VARIANTS"]

    summary = {
        "experiment": "cumulative-identity-across-repository-variants",
        "tasks": args.tasks, "steps": steps,
        "finalIdentities": len(identities), "finalPackages": len(final["packages"]),
        "duplicateIdentities": duplicates,
        "duplicateIdentityCount": len(duplicates),
        "unreconciledIdentities": sorted(unreconciled),
        "unreconciledCount": len(unreconciled),
        "refusedManufactures": refusals,
        "refusedCount": len(refusals),
        "refusalsByCause": {c: sum(1 for r in refusals if r["cause"] == c)
                            for c in sorted({r["cause"] for r in refusals})},
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(summary, indent=1) + "\n")
    print(json.dumps({k: v for k, v in summary.items() if k not in ("steps", "refusedManufactures")}, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
