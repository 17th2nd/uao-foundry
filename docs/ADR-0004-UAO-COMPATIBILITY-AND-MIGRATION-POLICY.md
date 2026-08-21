# ADR-0004 — UAO Compatibility and Migration Policy

**Status:** Accepted for the USI application programme
**Date:** 2026-08-21
**Depends on:** ADR-0003 (USI canonical terminology), ADR-0002 (ASA authority alignment)
**Authority created here:** Foundry migration policy only. No ASA authority.

## Context

ADR-0003 adopted **USI — Universal Semantic Identity** as the forward term and made UAO legacy
terminology. It deliberately did not say *how* the existing audited implementation reaches that
state without breaking.

`UAO` currently appears in materially different roles, each with a different migration risk:

| Role | Example | Breaking it costs |
|---|---|---|
| ASA-pinned identifier shape | `uao-<12 hex>` | **ASA authority violation** — see ADR-0005 |
| Serialised package/registry content | `canonical-identities.json`, `index.json` | every existing package and registry |
| Java package namespace | `org.seventeenthsecond.uaofoundry` | nothing external; churn only |
| Class and method names | `FoundryRegistry`, `uaoId` | nothing external; churn only |
| Historical governance | ADR-0001, audit records | the provenance record itself |
| Product language | README, UI, CLI output | nothing — this is what should change |

Treating these as one problem is how a rename destroys a working system.

## Decision

### 1. Three layers, migrated independently

```
PUBLIC PRODUCT LANGUAGE      → USI now
INTERNAL IMPLEMENTATION      → UAO retained; adapters, not forks
CANONICAL SERIALISED FORMAT  → UAO retained; changes only under ADR-0005
```

### 2. Product language changes first and alone

New operator-facing surfaces — application UI, application API field names, new documentation —
use USI terminology. This requires no change to any stored byte.

### 3. Legacy artefacts are never destructively converted

An existing package or registry manufactured before this programme MUST continue to open, verify,
search and reuse without conversion. Conversion-on-read is forbidden: it would rewrite immutable,
content-addressed evidence.

Enforced by test (`LegacyCompatibilityTest`), not merely asserted.

### 4. Adapters and facades, never forked cores

New application-facing code may live under `org.seventeenthsecond.usifoundry`, but only as a
facade over the audited core. **Copying a core algorithm into the facade is prohibited.** Two
implementations of identity resolution would diverge, and the audited one would not be the one
running.

The Java namespace `org.seventeenthsecond.uaofoundry` is retained. Renaming it would invalidate
every audit reference for no external benefit.

### 5. Historical governance is superseded, never rewritten

ADR-0001 is marked *Superseded*, with its original decision preserved verbatim. Audit records,
sprint reports and the Persistent Identity Alpha's findings keep their original terminology. A
governance record that has been edited to agree with a later decision is no longer evidence of
what was decided.

### 6. Where legacy terminology may still surface

Permitted: migration notes, compatibility documentation, developer diagnostics, historical package
metadata, and any field whose value is genuinely a legacy wire identifier.

When a legacy identifier is displayed to an operator it must be labelled as such rather than
silently presented as a USI identifier — see ADR-0005 §presentation.

### 7. Repository name unchanged

`17th2nd/uao-foundry` is not renamed by this programme. Product name, repository name and
wire-format compatibility are three separate concepts and are documented separately.

## Consequences

- The application can be fully USI-branded immediately, with zero risk to stored evidence.
- The audited core keeps its identity, its namespace and its audit trail.
- A future clean migration remains possible and is not made harder.
- Some internal inconsistency (USI product over UAO internals) persists deliberately. That is the
  price of not breaking working evidence, and it is cheaper than the alternative.
