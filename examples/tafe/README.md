# TAFE Demonstration — Cumulative UAO Manufacture

**Status:** non-authoritative demonstration guide  
**Core dependency:** generic UAO Foundry only; no TAFE-specific production code

This guide demonstrates the commercial/educational point that governed knowledge can be manufactured once and then reused in later manufacturing runs.

The demonstration deliberately uses the same generic Foundry executable and Claude provider used for any other identity.

## 1. Prepare

From the repository root:

```bash
scripts/preflight-live.sh
rm -rf demo-work demo-dist demo-registry
```

For a persistent demonstration registry, omit the `rm -rf demo-registry` line on later runs.

## 2. Manufacture the first knowledge package

```bash
scripts/manufacture-claude.sh \
  "Certificate III Electrotechnology" \
  --registry demo-registry \
  --work-dir demo-work \
  --dist-dir demo-dist \
  --register
```

The command runs:

```text
identity seed
   ↓
Claude research/provider bundle
   ↓
Foundry validation
   ↓
identity resolution
   ↓
canonical UAO package
   ↓
verification
   ↓
registry admission
```

Inspect the response and package. In particular:

- `verificationPassed` must be `true`;
- `publicationStatus` must be publication-eligible for registry admission;
- `reuse-report.json` records what was reused and what was newly manufactured;
- `provider-snapshot.json` records the exact intermediate provider bundle that drove the job;
- `checksums.sha256` covers the package evidence.

## 3. Manufacture a related topic

Then run:

```bash
scripts/manufacture-claude.sh \
  "Electric Vehicle Maintenance" \
  --registry demo-registry \
  --work-dir demo-work \
  --dist-dir demo-dist \
  --register
```

Before Claude performs new acquisition, the Foundry verifies the existing registry and supplies bounded discovery context.

If previously manufactured identities are genuinely reusable, the provider should reuse their exact registered `resolutionKey` values rather than inventing new identities. The Foundry then computes the reuse delta itself; the model does not get to declare canonical reuse.

Conceptually:

```text
Certificate III Electrotechnology
  ├─ electricity
  ├─ electrical safety
  ├─ circuits
  ├─ measurement
  ├─ conductors
  └─ regulations

later request: Electric Vehicle Maintenance
  ├─ discover reusable registered identities
  ├─ preserve prior package provenance
  └─ manufacture only genuinely new semantic identities/evidence
```

The exact reuse observed is evidence-dependent. The demonstration must show the actual `reuse-report.json`; do not claim reuse that the report does not record.

## 4. Explain the value

A useful meeting explanation is:

> The first request manufactures a governed knowledge package. When a later request overlaps with knowledge the Foundry has already verified and registered, the new job begins by discovering that existing knowledge. It can reuse the stable identities and their immutable provenance, then concentrate acquisition on the semantic delta instead of starting from zero.

This is stronger than simply caching generated text:

- reused items retain stable UAO identity;
- prior evidence remains in its original immutable package;
- registry evidence is content-hash checked;
- the new package records exactly what was reused versus newly manufactured;
- provider output remains intermediate and non-authoritative;
- package verification is independent of the provider call.

## 5. Current relationship limitation

The current ASA authority surface does not yet provide the machine-readable arbitrary-domain Relationship Type registry required for canonical URO role validation.

Therefore the Claude adapter intentionally returns no relationship candidates in this demonstration. The identity/evidence/reuse pipeline is real; arbitrary URO publication remains fail-closed until `17th2nd/ASA#29` supplies the missing governed source edition.

Do not hide this limitation in a meeting. It is a useful demonstration of the architecture's authority discipline: the Foundry refuses to invent a missing semantic contract merely to make a demo look more complete.

## 6. Optional ALA handoff

Once a manufactured package has passed verification, an ALA prototype can consume the package as knowledge input. ALA-specific learner modelling, instructional transformation and personalised sequencing remain separate downstream concerns and are not performed by this example.
