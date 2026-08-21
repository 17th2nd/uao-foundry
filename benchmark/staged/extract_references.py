#!/usr/bin/env python3
"""Deterministic file-reference extraction and relationship-bearing bundle construction (E-6/E-7).

Edges are derived from repository content only — Python import statements resolved to repository
files, and Markdown references to repository paths. `task.json` is never read here; the
pre-registration's ground-truth firewall depends on that.

Each edge becomes a fixture bundle carrying BOTH participant file identities (identity material
reproduced from build_repo_registry.bundle_for so the canonical identity projection — and with it
the semantic-variant digest — is unchanged) plus one `asa.core/references@1` relationship
candidate. Manufacturing such a bundle is refused registry admission under ASA#29 and staged as
non-canonical candidate memory by the application; that refusal-plus-staging is the behaviour
under test, not an error.
"""
import ast
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "pima"))
from build_repo_registry import bundle_for, repo_files, slug  # noqa: E402


def python_targets(repo: Path, rel: str, files: set[str]) -> set[str]:
    """Repository files referenced by import statements in one Python file."""
    try:
        tree = ast.parse((repo / rel).read_text(errors="replace"))
    except SyntaxError:
        return set()
    modules = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            # A relative import is resolved against the importing file's package.
            prefix = ".".join(Path(rel).parent.parts[: len(Path(rel).parent.parts) - node.level + 1]) \
                if node.level else ""
            modules.add(f"{prefix}.{node.module}".strip(".") if node.level else node.module)
    out = set()
    for module in modules:
        candidate = module.replace(".", "/")
        for form in (f"{candidate}.py", f"{candidate}/__init__.py"):
            if form in files and form != rel:
                out.add(form)
        # src-layout: the import path may omit a leading source directory.
        for f in files:
            if f != rel and (f.endswith(f"/{candidate}.py") or f.endswith(f"/{candidate}/__init__.py")):
                out.add(f)
    return out


def markdown_targets(repo: Path, rel: str, files: set[str]) -> set[str]:
    """Repository files a Markdown file mentions by path or unambiguous file name."""
    text = (repo / rel).read_text(errors="replace")
    out = set()
    names = {}
    for f in files:
        names.setdefault(Path(f).name, []).append(f)
    for token in set(re.findall(r"[\w./-]+\.(?:py|md|yml|toml|txt)", text)):
        cleaned = token.lstrip("./")
        if cleaned in files and cleaned != rel:
            out.add(cleaned)
        elif token in names and len(names[token]) == 1 and names[token][0] != rel:
            out.add(names[token][0])
    return out


def edges_of(repo: Path) -> list[tuple[str, str]]:
    """Every (referrer, referent) pair derivable from repository content, deterministically ordered."""
    files = set(repo_files(repo))
    out = set()
    for rel in files:
        targets = python_targets(repo, rel, files) if rel.endswith(".py") \
            else markdown_targets(repo, rel, files) if rel.endswith(".md") else set()
        out.update((rel, t) for t in targets)
    return sorted(out)


def _suffixed(bundle: dict, rel: str) -> tuple[dict, list, list]:
    """One file's identity, claims and evidence under pair-unique ids with references kept intact."""
    tag = slug(rel)
    identity = dict(bundle["candidates"]["identities"][0])
    identity["candidateId"] = f"cid-{tag}"
    claims, evidence = [], []
    for claim in bundle["candidates"]["claims"]:
        claims.append({**dict(claim), "candidateId": f"{claim['candidateId']}-{tag}",
                       "subjectIdentityRef": f"cid-{tag}"})
    for ev in bundle["candidates"]["evidence"]:
        evidence.append({**dict(ev), "evidenceId": f"{ev['evidenceId']}-{tag}",
                         "supportsCandidateRef": f"{ev['supportsCandidateRef']}-{tag}"})
    return identity, claims, evidence


def edge_bundle(repo: Path, referrer: str, referent: str) -> dict:
    """A relationship-bearing bundle for one edge, reusing the per-file identity material verbatim."""
    src = bundle_for(repo, referrer)
    dst = bundle_for(repo, referent)
    src_identity, src_claims, src_evidence = _suffixed(src, referrer)
    dst_identity, dst_claims, dst_evidence = _suffixed(dst, referent)
    dst_identity["root"] = False

    out = dict(src)
    out["identitySeed"] = f"{referrer} references {referent}"
    out["candidates"] = {
        "identities": [src_identity, dst_identity],
        "claims": src_claims + dst_claims,
        "relationships": [{
            "candidateId": f"rel-{slug(referrer)}--{slug(referent)}",
            "typeVersion": "asa.core/references@1",
            "participants": [
                {"role": "referrer", "candidateIdentityRef": src_identity["candidateId"]},
                {"role": "referent", "candidateIdentityRef": dst_identity["candidateId"]},
            ],
            "identityLiterals": {},
            "contextualBindings": [],
            "sourceRefs": [src["sources"][0]["sourceId"]],
        }],
        "evidence": src_evidence + dst_evidence,
        "states": [], "events": [], "languageMappings": [],
    }
    out["sources"] = src["sources"] + dst["sources"]
    return out


if __name__ == "__main__":
    import json
    repo = Path(sys.argv[1])
    print(json.dumps(edges_of(repo), indent=1))
