#!/usr/bin/env python3
"""Build the UAO Foundry experimental Relationship Type edition for Experiment 002.

Format: ASA-SPEC-0006 Relationship Type Registry (RTR) facet, edition-tagged so a Foundry
validator can fail closed on RTR-EDITION-MISMATCH / RTR-DIGEST-MISMATCH exactly as it would for
an ASA edition. Authority: NONE. Every domain type is `proposed` (decision_ref null) and the
$meta provenance anchor is PROPOSED; the five asa.core meta-types are carried verbatim from the
ASA edition 2026.2 (D-012 / D-040) because RTR conformance requires the core set.

Hashing uses the ASA kernel's JCS implementation so definition_hash / digest are produced by the
reference implementation, and the Java RelationshipTypeEdition then has to recompute them
independently (cross-implementation agreement is a test).
"""
from __future__ import annotations
import json, sys, pathlib
ASA = pathlib.Path.home() / "ASA-canonical"
sys.path.insert(0, str(ASA / "kernel"))
from asa_kernel import jcs, registry  # noqa: E402

NS = "foundry.exp002"
OWNER = f"asa:uao:{NS}/operator-v1"
TRACE = ["UAO Foundry Experiment 002 §2 (founder programme, 2026-09-05)", "ASA-SPEC-0006 §5, §8"]

def binary(name, a, b, a_max=1):
    return name, [
        {"name": a, "kind": "participant", "binds": ["uao"], "min": 1, "max": a_max, "identity": True},
        {"name": b, "kind": "participant", "binds": ["uao"], "min": 1, "max": 1, "identity": True},
    ], False

def symmetric(name):
    return name, [{"name": "party", "kind": "participant", "binds": ["uao"], "min": 2, "max": 2, "identity": True}], True

TYPES = [
    binary("author-of", "author", "work", a_max=8),
    binary("co-created", "creator", "creation", a_max=8),
    binary("created", "creator", "creation"),
    binary("developed", "developer", "artefact", a_max=8),
    binary("architect-of", "architect", "project"),
    binary("presented", "presenter", "presentation"),
    symmetric("associated-with"),
    binary("contributed-to", "contributor", "contribution", a_max=8),
    binary("member-of", "member", "group"),
    binary("influenced", "influencer", "influenced-party"),
    binary("influenced-by", "influenced-party", "influencer"),
    binary("about", "work", "subject"),
    binary("instance-of", "instance", "class"),
    binary("subclass-of", "subclass", "superclass"),
    binary("part-of", "part", "whole"),
    symmetric("related-to"),
    binary("precursor-to", "precursor", "successor"),
]

def record(name, roles, sym):
    tid = f"asa:type:{NS}/{name}@1"
    definition = {"id": tid, "meta": False, "symmetric": sym, "evidence": "supported",
                  "roles": sorted(roles, key=lambda r: r["name"]), "literals": [], "provenance_roles": []}
    return {
        "id": tid, "namespace": NS, "owner": OWNER,
        "admission": {"state": "proposed", "decision_ref": None, "admitted_registry_version": None,
                       "deprecated_registry_version": None, "withdrawn_registry_version": None},
        "definition": definition,
        "definition_hash": jcs.hash_of(definition),
        "semantics": registry.derive_semantics(definition),
        "evolution": {"supersedes": None, "superseded_by": None,
                       "compatibility": {"class": "unclassified", "authority_track": "SPEC-0017"},
                       "migration_contract_ref": None},
        "traceability": TRACE,
    }

def main(out: pathlib.Path):
    asa = json.loads((ASA / "specification/registry/relationship_types.json").read_text())
    core = [t for t in asa["types"] if t["namespace"] == "asa.core"]
    types = core + [record(*t) for t in TYPES]
    types.sort(key=lambda r: r["id"].encode("utf-8"))
    doc = {
        "$meta": {
            "title": "UAO Foundry Experimental Relationship Type Edition — Experiment 002 (NOT ASA-ADMITTED)",
            "registry_version": "2026.902",
            "schema": "schemas/asa/relationship_type_registry.schema.json",
            "subordination_clause_ack": True,
            "provenance_anchor": "PROPOSED",
            "css_schema_version": "2026.2",
            "spec_authority": "ASA-SPEC-0006",
        },
        "participant_kinds": ["perspective", "uao", "uro"],
        "types": types,
        "digest": "sha256:" + "0" * 64,
    }
    doc["digest"] = registry.registry_digest(doc)
    diags = registry.validate_registry_document(doc)
    if diags:
        for d in diags: print(d, file=sys.stderr)
        raise SystemExit("facet does not validate under the ASA kernel")
    reg = registry.TypeRegistry.from_doc(doc, "built")
    out.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {out} · edition {doc['$meta']['registry_version']} · {len(types)} records · {doc['digest']}")
    print("core carried:", [t["id"] for t in core])

if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "config/relationship-types/foundry-exp002.json"))
