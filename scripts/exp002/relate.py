#!/usr/bin/env python3
"""Experiment 002 Phase 3 — connect already-registered identities from their own evidence.

For each root person package in a registry this builds a *reuse-only* fixture bundle:
  * every source is a `registry://<pkg>/source-corpus/<sourceId>.txt` locator, so the Java core
    swaps in the immutable package bytes (SemanticDelta custody) and counts them as reused;
  * candidate identities, claims and evidence are restated verbatim, so every identity re-resolves
    to its existing uid with an identical semantic-variant digest (a re-observation, not a variant);
  * the only new material is the operator-proposed typed relationships, each citing the existing
    claims whose statements evidence it.
No research provider is consulted. Relationship proposals are the founder's Phase 3 list; the
Foundry validates them against the declared edition and refuses anything it cannot type or bind.
"""
from __future__ import annotations
import argparse, json, pathlib, re, subprocess, sys

# (root external key fragment) -> [(component key fragment, type name, evidence keywords)]
PROPOSALS = {
    "Q178577":  [("Q16953441", "author-of", ["Cybernetics"])],                                 # Wiener
    "Q181529":  [("Q4683452", "author-of", ["Administrative Behavior"]),                        # Simon
                 ("Q4391896", "co-created", ["Logic Theorist"])],
    "Q92824":   [("9781558604797", "author-of", ["Probabilistic Reasoning"]),                    # Pearl
                 ("Q28453533", "author-of", ["Causality"])],
    "Q92614":   [("augmenting-human-intellect", "author-of", ["Augmenting Human Intellect"]),  # Engelbart
                 ("Q1050365", "developed", ["NLS"]),
                 ("Q3521932", "presented", ["Mother of All Demos"]),
                 ("us-3541541a", "created", ["3,541,541", "3541541"])],
    "Q796226":  [("Q1147211", "architect-of", ["Cybersyn"]),                                    # Beer
                 ("9780471276876", "author-of", ["Brain of the Firm"]),
                 ("Q624501", "created", ["Viable System Model"])],
    "Q153761":  [("Q27976738", "author-of", ["Governing the Commons"])],                        # Ostrom
}
SUBJECT_ROLE = {"author-of": ("author", "work"), "co-created": ("creator", "creation"), "created": ("creator", "creation"),
                "developed": ("developer", "artefact"), "presented": ("presenter", "presentation"), "architect-of": ("architect", "project")}
NS = "asa:type:foundry.exp002/"

def load(p): return json.loads(pathlib.Path(p).read_text())

def slug(s): return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

def build_bundle(registry: pathlib.Path, pkg: dict) -> tuple[dict, list[str]]:
    pdir = registry / "packages" / pkg["packageId"]
    snapshot = load(pdir / "provider-snapshot.json")
    cands = load(pdir / "candidate-identities.json")
    claims = load(pdir / "candidate-claims.json")
    evidence = load(pdir / "candidate-evidence.json")
    sources = load(pdir / "source-registry.json")["sources"]
    root = next(c for c in cands if c["root"])
    notes = []
    relationships = []
    proposals = next((v for k, v in PROPOSALS.items() if k in root["resolutionKey"]), [])
    for frag, tname, keywords in proposals:
        comp = next((c for c in cands if frag in c["resolutionKey"]), None)
        if comp is None:
            notes.append(f"no candidate identity matching {frag} — proposal {tname} skipped"); continue
        evid = [c for c in claims if c["subjectIdentityRef"] in (root["candidateId"], comp["candidateId"])
                and any(k.lower() in c["statement"].lower() for k in keywords)]
        refs = sorted({r for c in evid for r in c["sourceRefs"]}) or sorted(comp["sourceRefs"])
        basis = "EXPLICIT" if evid else "INFERRED"
        subj, obj = SUBJECT_ROLE[tname]
        relationships.append({
            "candidateId": f"rel-{slug(tname)}-{slug(comp['label'])[:40]}",
            "typeVersion": NS + tname + "@1",
            "participants": [{"role": subj, "candidateIdentityRef": root["candidateId"]},
                             {"role": obj, "candidateIdentityRef": comp["candidateId"]}],
            "identityLiterals": {}, "contextualBindings": [], "sourceRefs": refs, "basis": basis,
        })
        if not evid: notes.append(f"{tname} → {comp['label']}: no claim statement names it; basis INFERRED from identity sources")
    seed = f"{root['label']} (relationships)"
    bundle = {
        "fixtureVersion": "0.1.0", "identitySeed": seed,
        "fixedClock": snapshot["fixedClock"], "knowledgeHorizon": snapshot["knowledgeHorizon"],
        "interpretations": [{"candidateId": "int-relationships", "label": root["label"],
                             "definition": f"Typed relationships between the registered identity '{root['label']}' and the component identities manufactured alongside it, derived from that package's own evidence.",
                             "semanticTypeProposal": "RegisteredIdentityRelationships", "confidence": 1, "status": "SELECTED",
                             "references": [f"registry://{pkg['packageId']}/canonical-identities.json"]}],
        "scopeResolution": {"selectedInterpretation": "int-relationships", "scopeStatus": "FIXTURE_SELECTED",
                            "canonicalWorkingLabel": root["label"],
                            "includedBoundaries": ["identities already registered in the originating package", "typed relationships among them"],
                            "excludedBoundaries": ["new research", "new identities", "new assertions"],
                            "excludedInterpretations": [], "unresolvedQuestions": []},
        "manufacturingPlan": {"planVersion": "0.1.0", "selectedIdentity": root["label"], "dimensions": ["typed-relationships"],
                              "neighbouringIdentities": [c["label"] for c in cands if not c["root"]],
                              "anticipatedSourceClasses": ["foundry-registry"], "risks": ["relationship type not admitted by ASA (proposed edition only)"],
                              "completionQuestions": [{"questionId": "q-relationships-evidenced", "prompt": "Is every proposed relationship supported by a registered assertion?", "required": True}],
                              "exclusions": ["research provider calls"]},
        "sourceStrategy": {"strategyVersion": "0.1.0",
                           "sourceClasses": [{"classId": "foundry-registry", "purpose": "immutable registry package bytes reused as evidence", "priority": 1}],
                           "authorityNotes": [f"Reuse-only manufacture from registry package {pkg['packageId']}; no research provider consulted (Experiment 002 Phase 3).",
                                              "Relationship proposals are operator-authored from the founder's Phase 3 list; each cites the registered claims evidencing it."],
                           "safetyConstraints": ["No network access.", "No new assertions about any identity."]},
        "sources": [{"sourceId": s["sourceId"], "locator": f"registry://{pkg['packageId']}/{s['snapshotPath']}",
                     "sourceClass": "foundry-registry", "retrievedAt": snapshot["fixedClock"],
                     "license": "UAO-FOUNDRY-REGISTRY-SNAPSHOT", "content": "registry evidence; exact bytes restored by the Foundry"} for s in sources],
        "candidates": {"identities": cands, "claims": claims, "relationships": relationships, "evidence": evidence},
        "coverageAnswers": {"q-relationships-evidenced": "covered" if all(r["basis"] == "EXPLICIT" for r in relationships) else "partial"},
    }
    return bundle, notes

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", required=True); ap.add_argument("--edition", required=True)
    ap.add_argument("--out", required=True); ap.add_argument("--jar", default="target/uao-foundry-0.1.0.jar")
    ap.add_argument("--run", action="store_true", help="manufacture + register each bundle")
    ap.add_argument("--repository-commit", default="local")
    a = ap.parse_args()
    registry = pathlib.Path(a.registry); out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
    index = load(registry / "index.json")
    reports = []
    for pkg in index["packages"]:
        if any(o["packageId"] == pkg["packageId"] for r in index.get("relationships", []) for o in r["occurrences"]):
            continue  # already a relationship package
        pdir = registry / "packages" / pkg["packageId"]
        if (pdir / "experimental-relationships.json").exists():
            continue
        bundle, notes = build_bundle(registry, pkg)
        if not bundle["candidates"]["relationships"]:
            print(f"-- {pkg['packageId']}: no proposals"); continue
        path = out / f"{slug(bundle['identitySeed'])}.json"
        path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
        print(f"-- {bundle['identitySeed']}: {len(bundle['candidates']['relationships'])} proposals, {len(bundle['sources'])} registry sources → {path}")
        for n in notes: print("   note:", n)
        if a.run:
            cmd = ["java", "-cp", a.jar, "org.seventeenthsecond.uaofoundry.console.OperatorConsole", "manufacture", bundle["identitySeed"],
                   "--registry", str(registry), "--fixture", str(path), "--relationship-edition", a.edition, "--register", "--json",
                   "--work-dir", str(out / "work"), "--dist-dir", str(out / "dist"), "--repository-commit", a.repository_commit]
            r = subprocess.run(cmd, capture_output=True, text=True)
            line = (r.stdout.strip().splitlines() or [""])[-1]
            try: rep = json.loads(line)
            except Exception: rep = {"error": (r.stderr or r.stdout)[-1500:], "exit": r.returncode}
            rep["_seed"] = bundle["identitySeed"]; rep["_exit"] = r.returncode
            reports.append(rep)
            print("   →", rep.get("publicationStatus"), rep.get("registryAdmission"), rep.get("counts"), rep.get("error", ""))
    (out / "phase3-reports.json").write_text(json.dumps(reports, indent=2) + "\n")

if __name__ == "__main__":
    main()
