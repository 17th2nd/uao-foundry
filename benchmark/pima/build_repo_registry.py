#!/usr/bin/env python3
"""Manufacture a persistent identity registry over a source repository.

One UAO identity per repository file, addressed by a Foundry-local resolution key and carrying a
content hash as a durable external identifier. Nothing about the benchmark tasks is read: the
registry is built from the repository alone, so the ground-truth firewall the ASALLM pipeline
maintains is preserved. A registry that had seen task.json would make every downstream lane
worthless.

Usage:
  build_repo_registry.py --repo <path> --registry <path> --jar <path> [--work <path>] [--dist <path>]
"""
import argparse, hashlib, json, re, subprocess, sys, tempfile
from pathlib import Path

CLOCK = "2026-08-20T00:00:00Z"
SUFFIXES = (".py", ".md", ".yml", ".toml", ".txt")


def slug(value: str) -> str:
    """Foundry key segments are [a-z0-9._-]; a path becomes one deterministic segment."""
    return re.sub(r"[^a-z0-9._-]+", "-", value.lower()).strip("-")


def repo_files(repo: Path):
    return sorted(
        str(p.relative_to(repo)) for p in repo.rglob("*")
        if p.is_file() and p.suffix in SUFFIXES
        and ".pytest_cache" not in p.parts and "__pycache__" not in p.parts
    )


def bundle_for(repo: Path, rel: str) -> dict:
    text = (repo / rel).read_text(errors="replace")
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    sid = "src-" + slug(rel)
    name = Path(rel).name
    # The content hash is the durable external identifier: unlike a path, it is issued by the
    # content itself and survives a rename. A path is recorded as an alias, which -- correctly --
    # can never establish identity on its own.
    return {
        "fixtureVersion": "0.1.0", "identitySeed": rel,
        "fixedClock": CLOCK, "knowledgeHorizon": CLOCK,
        "interpretations": [{
            "candidateId": "int-file", "label": rel,
            "definition": "A repository file treated as a persistent identity.",
            "semanticTypeProposal": "RepositoryFile", "confidence": 1.0,
            "status": "SELECTED", "references": [f"fixture://{sid}"]}],
        "scopeResolution": {
            "selectedInterpretation": "int-file", "scopeStatus": "FIXTURE_SELECTED",
            "canonicalWorkingLabel": rel,
            "includedBoundaries": ["file content at this revision"],
            "excludedBoundaries": ["runtime behaviour", "version history"],
            "excludedInterpretations": [], "unresolvedQuestions": []},
        "manufacturingPlan": {
            "planVersion": "0.1.0", "selectedIdentity": rel,
            "dimensions": ["content", "location"], "neighbouringIdentities": [],
            "anticipatedSourceClasses": ["repository-file"], "risks": ["path-versus-content identity"],
            "completionQuestions": [
                {"questionId": "q-content", "prompt": "Is the file content represented?", "required": True},
                {"questionId": "q-location", "prompt": "Is the file location represented?", "required": True}],
            "exclusions": ["derived artefacts"]},
        "sourceStrategy": {
            "strategyVersion": "0.1.0",
            "sourceClasses": [{"classId": "repository-file", "purpose": "file content evidence", "priority": 1}],
            "authorityNotes": ["Repository content is evidence, not authority."],
            "safetyConstraints": ["No network access."]},
        "sources": [{
            "sourceId": sid, "locator": f"fixture://{sid}", "sourceClass": "repository-file",
            "retrievedAt": CLOCK, "license": "BENCHMARK-FIXTURE", "content": text or "(empty file)"}],
        "candidates": {
            "identities": [{
                "candidateId": "cid-file", "label": rel,
                "resolutionKey": f"foundry:v0.1:file:{slug(rel)}",
                "root": True, "aliases": [name], "sourceRefs": [sid],
                "externalIdentifiers": {"sha256": digest}}],
            "claims": [
                {"candidateId": "clm-path", "subjectIdentityRef": "cid-file",
                 "statement": f"This identity denotes the repository file at path {rel}.",
                 "channels": ["foundry"], "sourceRefs": [sid]},
                {"candidateId": "clm-digest", "subjectIdentityRef": "cid-file",
                 "statement": f"The content of this file hashes to sha256 {digest}.",
                 "channels": ["foundry"], "sourceRefs": [sid]}],
            # Deliberately empty. A relationship candidate would make the package
            # EVIDENCE_INCOMPLETE under ASA#29 and therefore inadmissible to the registry, so a
            # persistent relationship graph cannot be built at all until that authority exists.
            "relationships": [],
            "evidence": [
                {"evidenceId": "ev-path", "sourceRef": sid, "supportsCandidateRef": "clm-path",
                 "extract": f"File present at {rel}.", "locatorWithinSource": "path"},
                {"evidenceId": "ev-digest", "sourceRef": sid, "supportsCandidateRef": "clm-digest",
                 "extract": f"sha256 {digest}", "locatorWithinSource": "content"}],
            "states": [], "events": [], "languageMappings": []},
        "coverageAnswers": {"q-content": "covered", "q-location": "covered"},
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--registry", required=True, type=Path)
    ap.add_argument("--jar", required=True, type=Path)
    ap.add_argument("--work", type=Path)
    ap.add_argument("--dist", type=Path)
    args = ap.parse_args()

    scratch = Path(tempfile.mkdtemp(prefix="pima-registry-"))
    work = args.work or scratch / "work"
    dist = args.dist or scratch / "dist"
    fixtures = scratch / "fixtures"
    fixtures.mkdir(parents=True, exist_ok=True)

    files = repo_files(args.repo)
    manufactured, failed = [], []
    for rel in files:
        path = fixtures / (slug(rel) + ".json")
        path.write_text(json.dumps(bundle_for(args.repo, rel), indent=2))
        proc = subprocess.run(
            ["java", "-cp", str(args.jar),
             "org.seventeenthsecond.uaofoundry.console.OperatorConsole",
             "manufacture", rel,
             "--registry", str(args.registry), "--fixture", str(path),
             "--work-dir", str(work), "--dist-dir", str(dist),
             "--register", "--json"],
            capture_output=True, text=True)
        if proc.returncode != 0:
            failed.append({"file": rel, "stderr": proc.stderr.strip()[:400]})
            continue
        report = json.loads(proc.stdout)
        manufactured.append({"file": rel, "packageId": report["packageId"],
                             "admission": report["registryAdmission"],
                             "counts": report["counts"]})

    summary = {"repo": str(args.repo), "registry": str(args.registry),
               "filesConsidered": len(files), "manufactured": len(manufactured),
               "failed": failed, "packages": manufactured}
    print(json.dumps(summary, indent=1))
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
