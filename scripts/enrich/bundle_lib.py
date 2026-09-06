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
Codex pass C (temp/codex-uaofoundry-adr0007-ratification-pass-c-001.md) tightened all three:
  F-C1  ``current_occurrence`` also refuses any identity whose ``semanticVariantStatus`` is not SINGLE_VARIANT;
  F-C3  lexical overlap cannot prove semantic novelty, so ``split_paraphrases`` is a TRIAGE AID only — the bundle
        builder admits a new assertion only when the operator names it (``--accept``);
  F-C4  ``SourcePool.install`` rewrites ``sourceRefs``/``sourceRef`` in EVERY record handed to it (identities,
        claims, evidence, relationships) and re-installing an already-renamed origin is idempotent.
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
    status = identity.get("semanticVariantStatus")
    if status is not None and status != "SINGLE_VARIANT":
        raise ValueError(f"{uid} is {status}; the registry names no single current state, reconcile before restating it")
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

def validate_threshold(value) -> float:
    """A paraphrase threshold is a Jaccard score: a finite number in [0, 1]. Anything else would disable triage silently."""
    try: f = float(value)
    except (TypeError, ValueError): raise ValueError(f"paraphrase threshold must be a number in [0, 1], got {value!r}")
    if not (0.0 <= f <= 1.0): raise ValueError(f"paraphrase threshold must be in [0, 1], got {value!r}")   # also rejects NaN
    return f

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
    renamed ``<id>--<origin>`` and every ``sourceRefs`` list / ``sourceRef`` field in the records supplied with it is
    rewritten -- identities, claims, evidence and relationships alike. An existing id is reused only for the SAME
    BYTES (equal registry sha256, or an identical source record): re-installing the same source is idempotent, two
    registry snapshots of one document are shared, and everything else gets a distinct, deterministic id.
    """
    def __init__(self):
        self.sources: dict[str, dict] = {}; self._origin: dict[str, str] = {}; self._sha: dict[str, str | None] = {}
        self._content: dict[str, str] = {}; self.renames: list[tuple[str, str, str]] = []

    @staticmethod
    def rewrite(records, old: str, new: str) -> int:
        """Rewrite every reference to ``old`` in ``records`` (any dict carrying ``sourceRefs`` or ``sourceRef``). Returns the count."""
        n = 0
        for r in records:
            refs = r.get("sourceRefs")
            if isinstance(refs, list) and old in refs: r["sourceRefs"] = [new if x == old else x for x in refs]; n += 1
            if r.get("sourceRef") == old: r["sourceRef"] = new; n += 1
        return n

    @staticmethod
    def _content_key(source: dict, sha256: str | None) -> str:
        """What makes two sources 'the same bytes': the registry's sha256 when it has one, else the whole source record."""
        import json
        return "sha:" + sha256 if sha256 else "rec:" + json.dumps({k: v for k, v in source.items() if k != "sourceId"}, sort_keys=True, ensure_ascii=False)

    def _store(self, sid: str, source: dict, origin: str, sha256: str | None, key: str):
        self.sources[sid] = source; self._origin[sid] = origin; self._sha[sid] = sha256; self._content[sid] = key

    def install(self, source: dict, origin: str, records, sha256: str | None = None) -> str:
        sid = source["sourceId"]; records = list(records); key = self._content_key(source, sha256)
        # Codex pass D F-D1: "same origin" is never enough on its own -- a live package may legitimately carry both
        # src-x and src-x--live-<suffix> as DISTINCT sources. An id is reused only for the same bytes.
        def same_bytes(existing: str) -> bool:
            if self._content[existing] == key: return True
            return sha256 is not None and self._sha[existing] is not None and sha256 == self._sha[existing]
        if sid not in self.sources:
            self._store(sid, source, origin, sha256, key); return sid
        if same_bytes(sid): return sid
        new_id = f"{sid}--{origin}"; n = 1
        while new_id in self.sources:
            if same_bytes(new_id): self.rewrite(records, sid, new_id); return new_id
            n += 1; new_id = f"{sid}--{origin}-{n}"      # different bytes under the renamed id too: keep it distinct, deterministically
        renamed = dict(source); renamed["sourceId"] = new_id
        self._store(new_id, renamed, origin, sha256, key)
        self.rewrite(records, sid, new_id)
        self.renames.append((sid, new_id, origin)); return new_id

    def notes(self) -> list[str]:
        return [f"source id collision: {old} from {origin} renamed {new}; the id stays bound to the bytes first installed under it" for old, new, origin in self.renames]

def registry_source(source: dict, package_id: str, fixed_clock: str) -> dict:
    return {"sourceId": source["sourceId"], "locator": f"registry://{package_id}/{source['snapshotPath']}", "sourceClass": "foundry-registry",
            "retrievedAt": fixed_clock, "license": "UAO-FOUNDRY-REGISTRY-SNAPSHOT", "content": "registry evidence; exact bytes restored by the Foundry"}

# ----------------------------------------------------------------------------------------------- claim / evidence ids

def disambiguate_ids(pkg_claims: list[dict], pkg_evidence: list[dict], taken_claim_ids: set, taken_evidence_ids: set, origin: str) -> list[str]:
    """Restated claims/evidence copied from a registry package may reuse candidate ids the live package (or another
    registry package) already uses (Macleay reconcile runs 16/23: ``clm-location``). Rename the copied side
    ``<id>--<origin>`` and rewrite ``supportsCandidateRef`` in the copied evidence; returns notes. The sets are updated."""
    notes = []
    for cl in pkg_claims:
        cid = cl["candidateId"]
        if cid in taken_claim_ids:
            new = f"{cid}--{origin}"
            if new in taken_claim_ids: raise ValueError(f"claim id {new} already present; cannot disambiguate {cid} from {origin}")
            cl["candidateId"] = new
            for ev in pkg_evidence:
                if ev.get("supportsCandidateRef") == cid: ev["supportsCandidateRef"] = new
            notes.append(f"claim id collision: {cid} from {origin} renamed {new}")
        taken_claim_ids.add(cl["candidateId"])
    for ev in pkg_evidence:
        eid = ev["evidenceId"]
        if eid in taken_evidence_ids:
            new = f"{eid}--{origin}"
            if new in taken_evidence_ids: raise ValueError(f"evidence id {new} already present; cannot disambiguate {eid} from {origin}")
            ev["evidenceId"] = new; notes.append(f"evidence id collision: {eid} from {origin} renamed {new}")
        taken_evidence_ids.add(ev["evidenceId"])
    return notes
