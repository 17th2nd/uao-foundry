"""Shared registry-reading helpers for the 0-provider-call bundle builders
(scripts/exp002/reconcile_reuse.py, scripts/enrich/build_enrichment_bundle.py).

Written for the Codex refusals of ADR-0007 (temp/codex-uaofoundry-adr0007-ratification-pass-{b,c,d,e}-001.md):
  F-B2/F-C1  ``current_occurrence`` selects the CURRENT variant by ``currentVariant`` and refuses any identity whose
             ``semanticVariantStatus`` is not SINGLE_VARIANT (``occurrences`` order is package-id order, never succession);
  F-B5/F-C3  lexical overlap cannot prove semantic novelty, so ``split_paraphrases`` is a TRIAGE AID only -- the bundle
             builder admits a new assertion only when the operator names it (``--accept``);
  F-B6/F-C4/F-D1/F-E1  ``SourcePool.install_origin`` is TWO-PHASE: every source of one origin is planned first (an id is
             reused only for the same bytes; a colliding id gets a distinct deterministic name that also avoids the
             origin's own ids), and only then is every reference in the origin's records rewritten exactly once through
             that plan -- so chained rewrites cannot move a reference twice, whatever the installation order;
  F-D2/F-E2/F-E3  ``restate_identity`` copies a registered identity from ALL the registry package's candidates that share
             its resolution key, once per identity, so neither builder drops or duplicates registered assertions.
No provider calls, no registry writes.
"""
from __future__ import annotations
import json, pathlib, re

def load(p): return json.loads(pathlib.Path(p).read_text())

# ----------------------------------------------------------------------------------------------- current variant

def current_occurrence(identity: dict) -> dict:
    """The occurrence carrying the identity's CURRENT semantic variant.

    ``currentVariant`` is the only succession pointer the registry exposes (ADR-0007); ``occurrences`` is ordered by
    package id, which says nothing about which state is current. The registry's own verdict on the identity
    (``semanticVariantStatus``) outranks any pointer. Without a pointer the identity must carry one digest, in which
    case every occurrence is the same state and the first is as good as any.
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

# ----------------------------------------------------------------------------------------------- paraphrase triage

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
    """Partition provider claims into (genuine, paraphrases) for REVIEW ONLY; ``paraphrases`` items are
    ``(claim, nearest_restated_statement, score)``. This is a triage aid: lexical overlap proves nothing about novelty."""
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

# ----------------------------------------------------------------------------------------------- sources

class SourcePool:
    """Source ids from different origins (the live package, each registry package) merged without substitution.

    ``install_origin`` takes EVERY source of one origin at once. Phase 1 plans an id for each: an existing id is
    reused only for the same bytes (equal registry sha256, or an identical source record); otherwise the source gets
    ``<id>--<origin>`` (then ``-2``, ``-3``…) skipping ids already in the pool with different bytes AND ids the origin
    itself declares, so a package legitimately carrying both ``src-x`` and ``src-x--live-abc`` keeps both. Phase 2
    rewrites every ``sourceRefs`` list / ``sourceRef`` field in the origin's records exactly once through that plan,
    so no reference can be moved twice whatever the installation order. Re-installing an origin is idempotent.
    """
    def __init__(self):
        self.sources: dict[str, dict] = {}; self._origin: dict[str, str] = {}; self._sha: dict[str, str | None] = {}
        self._content: dict[str, str] = {}; self.renames: list[tuple[str, str, str]] = []

    @staticmethod
    def _content_key(source: dict, sha256: str | None) -> str:
        """What makes two sources 'the same bytes': the registry's sha256 when it has one, else the whole source record."""
        return "sha:" + sha256 if sha256 else "rec:" + json.dumps({k: v for k, v in source.items() if k != "sourceId"}, sort_keys=True, ensure_ascii=False)

    def _same_bytes(self, existing: str, key: str, sha256: str | None) -> bool:
        if self._content[existing] == key: return True
        return sha256 is not None and self._sha[existing] is not None and sha256 == self._sha[existing]

    def _store(self, sid: str, source: dict, origin: str, sha256: str | None, key: str):
        self.sources[sid] = source; self._origin[sid] = origin; self._sha[sid] = sha256; self._content[sid] = key

    @staticmethod
    def apply(records, mapping: dict[str, str]) -> int:
        """Rewrite each reference once: ``ref -> mapping.get(ref, ref)``. Returns the number of records touched."""
        n = 0
        for r in records:
            refs = r.get("sourceRefs")
            if isinstance(refs, list) and any(x in mapping for x in refs): r["sourceRefs"] = [mapping.get(x, x) for x in refs]; n += 1
            if r.get("sourceRef") in mapping: r["sourceRef"] = mapping[r["sourceRef"]]; n += 1
        return n

    def install_origin(self, origin: str, sources: list[dict], records, sha256_of: dict[str, str] | None = None) -> dict[str, str]:
        sha256_of = sha256_of or {}; declared = {s["sourceId"] for s in sources}
        if len(declared) != len(sources): raise ValueError(f"origin {origin} declares a duplicate source id")
        mapping: dict[str, str] = {}
        for source in sources:                                       # phase 1: plan every id before touching a reference
            sid = source["sourceId"]; sha = sha256_of.get(sid) or source.get("sha256"); key = self._content_key(source, sha)
            if sid not in self.sources: self._store(sid, source, origin, sha, key); mapping[sid] = sid; continue
            if self._same_bytes(sid, key, sha): mapping[sid] = sid; continue
            cand = f"{sid}--{origin}"; n = 1
            while True:
                if cand in self.sources:
                    if self._same_bytes(cand, key, sha): break                     # the same bytes already live here
                elif cand not in declared:                                         # free, and not one of the origin's own ids
                    renamed = dict(source); renamed["sourceId"] = cand; self._store(cand, renamed, origin, sha, key)
                    self.renames.append((sid, cand, origin)); break
                n += 1; cand = f"{sid}--{origin}-{n}"
            mapping[sid] = cand
        self.apply(list(records), {k: v for k, v in mapping.items() if k != v})   # phase 2: one pass, no chaining
        return mapping

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

# ----------------------------------------------------------------------------------------------- restatement

def restate_identity(registry: pathlib.Path, ident: dict, occ: dict, c: dict, pool: SourcePool,
                     taken_claims: set, taken_evidence: set, restated_uids: set) -> tuple[list[dict], list[dict], list[str]]:
    """Restate the registered identity ``ident`` (current occurrence ``occ``) into live candidate ``c``, verbatim.

    The registry package may hold SEVERAL candidates with this resolution key (the pipeline maps them all to one
    UAO): the identity fields come from the first, the claims and evidence from ALL of them. Claims are copied once
    per identity (``restated_uids``), so a second live candidate for the same identity restates nothing again.
    The package's sources are installed under the package id as origin. Returns (claims, evidence, notes).
    """
    rp = registry / "packages" / occ["packageId"]
    rcs = [x for x in load(rp / "candidate-identities.json") if x["resolutionKey"] == c["resolutionKey"]]
    if not rcs: raise ValueError(f"{occ['packageId']} carries no candidate with resolution key {c['resolutionKey']}")
    c.update({k: rcs[0][k] for k in rcs[0] if k not in ("candidateId", "root")})
    rsnap = load(rp / "provider-snapshot.json"); pkg_claims, pkg_evidence, notes = [], [], []
    if ident["uid"] not in restated_uids:
        restated_uids.add(ident["uid"]); rc_ids = {x["candidateId"] for x in rcs}
        evidence = load(rp / "candidate-evidence.json")
        for cl in load(rp / "candidate-claims.json"):
            if cl["subjectIdentityRef"] not in rc_ids: continue
            cl2 = dict(cl); cl2["subjectIdentityRef"] = c["candidateId"]; pkg_claims.append(cl2)
            pkg_evidence += [dict(ev) for ev in evidence if ev["supportsCandidateRef"] == cl["candidateId"]]
    rsources = load(rp / "source-registry.json")["sources"]
    pool.install_origin(occ["packageId"], [registry_source(s, occ["packageId"], rsnap["fixedClock"]) for s in rsources],
                        [c] + pkg_claims + pkg_evidence, sha256_of={s["sourceId"]: s.get("sha256") for s in rsources})
    notes += disambiguate_ids(pkg_claims, pkg_evidence, taken_claims, taken_evidence, occ["packageId"])
    notes.append(f"{c['label']} ({c['resolutionKey']}) restated verbatim from {occ['packageId']} (variant {occ['semanticVariantDigest'][:12]}…, {len(rcs)} registry candidate(s), {len(pkg_claims)} assertion(s))")
    return pkg_claims, pkg_evidence, notes
