# Stable Semantic Identity Discipline — Foundry v0.1

**Status:** Foundry implementation discipline; subordinate to ASA authority  
**Applies to:** live provider candidate identity `resolutionKey` values  
**Does not create:** a new ASA primitive, identity ontology, namespace authority or URO type authority

## 1. Purpose

A live research/model provider may describe the same semantic identity differently across runs. The Foundry needs a stable candidate key so repeated manufacture can discover and reuse previously registered UAO identities instead of manufacturing duplicate identities from conversational variation.

The `resolutionKey` is the provider/Foundry identity-resolution input used to derive stable UAO IDs. It is therefore kept independent of transient execution details.

## 2. Priority order

For each candidate identity, a live provider should resolve the key using this order:

### 2.1 Reuse an existing registered identity

If the verified Foundry registry contains the same semantic identity and reuse is justified, use the registry's exact existing `resolutionKey`.

Do not derive a “similar” key from the current wording. Exact reuse is what allows the Foundry to recognize the same UAO identity.

### 2.2 Use a durable external identifier

When an appropriate stable external identifier already exists, encode:

```text
ext:<scheme>:<identifier>
```

The scheme/identifier pair must come from the external identity system, not from the model's current session.

Examples of possible *shapes* include a governed qualification identifier, standard identifier, chemical identifier or other domain identifier. Whether a specific external scheme is authoritative/appropriate remains a domain/evidence question; this document does not approve external registries by category.

### 2.3 Manufacture a Foundry-local semantic key

When no suitable registered or external identity exists, use:

```text
foundry:v0.1:<semantic-type-slug>:<canonical-label-slug>
```

The semantic type and canonical label should be normalized descriptions of the intended identity, not values copied from execution metadata.

## 3. Forbidden identity inputs

The following MUST NOT determine semantic identity:

- provider/model name;
- Claude/Codex/Gemini/etc. session ID;
- conversation ID;
- turn number;
- request timestamp;
- random UUID;
- process ID;
- temporary file name/path;
- source retrieval ordering;
- confidence value;
- model-generated candidate ranking;
- token count;
- current job ID;
- wording variations that do not change the intended identity.

The Sprint 2026-08-10 Claude adapter rejects new live keys outside `ext:*` or `foundry:v0.1:*`, and rejects obvious UUID/model/session/timestamp material.

## 4. Candidate IDs are not semantic IDs

Fields such as:

```text
cid-root
clm-root
ev-root
src-root
```

are local handles within one provider bundle. They may be deterministic and readable, but they do not define cross-package semantic identity.

Stable UAO reuse depends on the semantic `resolutionKey`, not on those local handles.

## 5. Registry authority boundary

The Foundry registry indexes verified package occurrences and stable UAO identities. It does not decide by heuristic that two different identities are equivalent.

The provider may use registry discovery context to identify a possible match. If it chooses exact reuse, the Foundry later computes the resulting reused/new delta from stable UAO IDs. Provider text cannot override the registry or force canonical reuse by assertion.

A future identity-equivalence/convergence mechanism, if needed, must be separately governed and tested rather than implemented as fuzzy registry matching that silently merges identities.

## 6. Collisions and disagreement

If two genuinely distinct semantic identities would receive the same proposed key, they must not be silently merged. The provider should refine the semantic type/label or use a more authoritative external identifier.

If available evidence cannot determine whether two candidates are the same identity, preserve the ambiguity/unresolved state rather than forcing reuse.

## 7. Relationship to UAO → USI review

This discipline does not pre-adopt **USI — Universal Semantic Identity**. It is evidence that UAO manufacturing already requires portable semantic identity continuity, but the terminology decision remains governed by `ADR-0001-UAO-TERMINOLOGY.md` and is deferred to a clean future migration/review.

## 8. Test obligations

Live adapters should prove at least:

- exact registered-key reuse;
- stable new-key generation independent of model/session metadata;
- rejection of an obviously ephemeral key;
- preservation of ambiguity instead of forced key reuse;
- semantic-delta reporting based on Foundry identity results rather than model self-report.
