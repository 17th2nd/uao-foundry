"""Shared registry-reading helpers for the 0-provider-call bundle builders
(scripts/exp002/reconcile_reuse.py, scripts/enrich/build_enrichment_bundle.py).

Written for the Codex pass-B refusal of ADR-0007 (temp/codex-uaofoundry-adr0007-ratification-pass-b-001.md):
  F-B2  a reader that takes ``occurrences[0]`` can restate a SUPERSEDED variant (occurrences are sorted by
        package id, not succession) → ``current_occurrence`` selects by ``currentVariant`` and fails closed;
  F-B5  exact-string novelty lets a paraphrase of a prior assertion count as enrichment → ``split_paraphrases``
        classifies near-duplicates by content-token overlap and the caller refuses them unless overridden;
  F-B6  a live source whose id collides with a registered source silently substitutes provenance →
        ``SourcePool`` renames the LIVE side on collision and rewrites its claim/evidence references,
        so historical claims keep binding to registry bytes.
No provider calls, no registry writes.
"""
from __future__ import annotations
import re

# ----------------------------------------------------------------------------------------------- F-B2

def current_occurrence(identity: dict) -> dict:
    """The occurrence carrying the identity's CURRENT semantic variant.

    ``currentVariant`` is the only succession pointer the registry exposes (ADR-0007); ``occurrences`` is ordered by
    package id, which says nothing about which state is current. Without ``currentVariant`` the identity must be
    single-variant, in which case every occurrence carries the same state and the first is as good as any.
    """
    uid = identity.get("uid", "?"); occurrences = identity.get("occurrences") or []
    if not occurrences: raise ValueError(f"{uid} has no occurrences")
    current = identity.get("currentVariant")
    if current:
        for o in occurrences:
            if o["semanticVariantDigest"] == current: return o
        raise ValueError(f"{uid}: currentVariant {current[:12]}… is not an occurrence (index inconsistent)")
    digests = {o["semanticVariantDigest"] for o in occurrences}
    if len(digests) != 1:
        raise ValueError(f"{uid} has {len(digests)} unreconciled semantic variants and no currentVariant; reconcile before restating it")
    return occurrences[0]

# ----------------------------------------------------------------------------------------------- F-B5

_STOP = {"a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "by", "with", "as", "is", "was", "were", "be",
         "been", "it", "its", "that", "this", "which", "who", "whom", "from", "into", "than", "then", "he", "she", "they",
         "his", "her", "their", "has", "had", "have", "not", "no", "also", "but", "are", "s"}
PARAPHRASE_THRESHOLD = 0.5

def content_tokens(statement: str) -> set[str]:
    return {t for t in re.findall(r"[a-z0-9]+", statement.lower()) if t not in _STOP}

def similarity(a: str, b: str) -> float:
    """Jaccard overlap of content tokens; 1.0 for statements that differ only in case, punctuation or stop words."""
    ta, tb = content_tokens(a), content_tokens(b)
    union = ta | tb
    return 1.0 if not union else len(ta & tb) / len(union)

def split_paraphrases(new_claims: list[dict], restated_statements, threshold: float = PARAPHRASE_THRESHOLD):
    """Partition provider claims into (genuine, paraphrases).

    A claim is a paraphrase when its content tokens overlap a restated statement at or above ``threshold`` (or
    equal it exactly). ``paraphrases`` items are ``(claim, nearest_restated_statement, score)``.
    """
    restated = list(restated_statements); genuine, paraphrases = [], []
    for cl in new_claims:
        stmt = cl["statement"]
        if stmt in restated: paraphrases.append((cl, stmt, 1.0)); continue
        best, score = None, -1.0
        for r in restated:
            s = similarity(stmt, r)
            if s > score: best, score = r, s
        if best is not None and score >= threshold: paraphrases.append((cl, best, score))
        else: genuine.append(cl)
    return genuine, paraphrases

# ----------------------------------------------------------------------------------------------- F-B6

class SourcePool:
    """Source ids from different origins (the live package, each registry package) merged without substitution.

    ``install`` keeps the first source under an id; a later source with the same id from a DIFFERENT origin is
    renamed ``<id>--<origin>`` and every ``sourceRefs``/``sourceRef`` in the claims and evidence supplied with it is
    rewritten, unless both are registry snapshots with the same sha256 (the same bytes, safely shared).
    """
    def __init__(self):
        self.sources: dict[str, dict] = {}; self._origin: dict[str, str] = {}; self._sha: dict[str, str | None] = {}; self.renames: list[tuple[str, str, str]] = []

    def install(self, source: dict, origin: str, claims: list[dict], evidence: list[dict], sha256: str | None = None) -> str:
        sid = source["sourceId"]
        if sid not in self.sources:
            self.sources[sid] = source; self._origin[sid] = origin; self._sha[sid] = sha256; return sid
        if self._origin[sid] == origin: return sid
        if sha256 is not None and self._sha[sid] is not None and sha256 == self._sha[sid]: return sid
        new_id = f"{sid}--{origin}"
        if new_id in self.sources: raise ValueError(f"source id {new_id} already present; cannot disambiguate {sid} from {origin}")
        renamed = dict(source); renamed["sourceId"] = new_id
        self.sources[new_id] = renamed; self._origin[new_id] = origin; self._sha[new_id] = sha256
        for cl in claims:
            if sid in cl.get("sourceRefs", []): cl["sourceRefs"] = [new_id if r == sid else r for r in cl["sourceRefs"]]
        for ev in evidence:
            if ev.get("sourceRef") == sid: ev["sourceRef"] = new_id
        self.renames.append((sid, new_id, origin)); return new_id

    def notes(self) -> list[str]:
        return [f"source id collision: {old} from {origin} renamed {new}; the id stays bound to the bytes first installed under it" for old, new, origin in self.renames]

def registry_source(source: dict, package_id: str, fixed_clock: str) -> dict:
    return {"sourceId": source["sourceId"], "locator": f"registry://{package_id}/{source['snapshotPath']}", "sourceClass": "foundry-registry",
            "retrievedAt": fixed_clock, "license": "UAO-FOUNDRY-REGISTRY-SNAPSHOT", "content": "registry evidence; exact bytes restored by the Foundry"}
