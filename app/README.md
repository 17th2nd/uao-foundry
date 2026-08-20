# USI Foundry — Application Layer

**USI Foundry 0.1.0-alpha** · a local-first application for manufacturing Universal Semantic
Identities.

```
app/
├── frontend/    operator UI (static HTML/CSS/JS, bundled into the jar at /app/)
├── packaging/   install.sh, ui-evidence.py
└── README.md
```

Backend Java lives at `src/main/java/org/seventeenthsecond/usifoundry/` — the single-module Maven
build has one source root, and `app/frontend` is added as a resource directory so the UI is bundled
without a second build step. UI logic is not scattered through the audited core (directive §37).

## Install and run

```bash
./app/packaging/install.sh     # builds if needed; installs to ~/.local, no root, no system packages
usi-foundry                    # opens http://127.0.0.1:7717/
```

Or without installing:

```bash
mvn -B -ntp package
java -cp target/uao-foundry-0.1.0.jar org.seventeenthsecond.usifoundry.UsiFoundryApp
```

Options: `--home <path>` `--port <n>` `--no-open`. Environment: `USI_FOUNDRY_HOME`.

Uninstall is `rm -rf ~/.local/bin/usi-foundry ~/.local/lib/usi-foundry`. Your registry is untouched.

## Storage (§22)

```
~/.usi-foundry/
├── registry/               packages/ · identity-operations/ · index.json
├── runs/                   run evidence, beside the registry (ADR-0006)
├── staged-relationships/   non-canonical relationship candidate memory (docs/RELATIONSHIP-STAGING.md)
├── packages/               manufactured package output
├── cache/                  job working directories; safe to delete
├── config/                 config.json
└── logs/
```

Nothing is written into the directory you launched from.

## Architecture

```
  browser (loopback only)
        │  fetch
        ▼
  UsiApiServer          JDK com.sun.net.httpserver — zero new dependencies
        │               binds 127.0.0.1; refuses foreign Origin
        ▼
  UsiFoundryService     the facade: product terminology in, core calls out
        │               re-implements NOTHING (ADR-0004 §4)
        ▼
  org.seventeenthsecond.uaofoundry.*        the audited core, unchanged
        FoundryPipeline · FoundryRegistry · ReuseAnalyzer
        IdentityResolver · PackageVerifier · RunStore
```

**Zero runtime dependencies** are preserved. The core's supply-chain posture is a security
property, and a web framework to serve six screens would trade it for nothing.

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/status` | plant status, registry verification, counts, identity operations |
| `POST` | `/api/manufacture` | start a manufacture; returns a `jobToken` |
| `GET` | `/api/manufacture/{token}` | **real** stage progress, then result or classified failure |
| `GET` | `/api/registry/search?q=` | ranked discovery |
| `POST` | `/api/registry/verify` | rebuild-and-compare verification |
| `GET` | `/api/identity/{ref}` | exact addressing: uid · resolution key · `scheme:identifier` · alias |
| `GET` | `/api/package/{packageId}` | package inspector |
| `GET` | `/api/significance/{ref}` | `A_x` / `R_x` debugging view — never computes significance |
| `GET` | `/api/staged-relationships/{ref}` | staged candidate neighbourhood of an exactly resolved identity — non-canonical memory, never UROs |
| `GET` | `/api/runs` | manufacturing history |

Errors carry a classified `error` code and, where useful, `guidance` (§25). "Manufacture failed" is
not an answer an operator can act on.

## Stage progress is real (§24)

The pipeline writes `checkpoint.json` into its job directory as each of the sixteen stages
completes. `/api/manufacture/{token}` reads that file. A stage shows COMPLETE **because the core
recorded it**, so there is no progress bar inventing motion.

## Terminology (ADR-0003, ADR-0004, ADR-0005)

The product speaks **USI**. The canonical identifier remains `uao-<12 hex>`, because that shape is
pinned by ASA CSS and ADR-0002 forbids the Foundry reinterpreting an ASA primitive — so the UI
shows:

```
USI ID   uao-3faaf60ce5df   (legacy wire identifier)
```

No `usi-` string is minted. An identifier an operator can copy must be one the registry accepts
back. The reserved `uao- ⟷ usi-` mapping exists, is tested, and is called by nothing that mints.

## Security posture

- **Loopback only.** Local-first means the socket, not just the storage.
- **Same-origin only.** A request whose `Origin` is not this server's own is refused, so a page
  served elsewhere cannot drive the local API.
- **Strict CSP** on served assets; the UI loads only its own files.
- **No credentials anywhere.** Provider configuration names a command, never a secret.
- **No destructive controls.** The Alpha exposes no delete; `retire`, `supersede` and `merge` are
  append-preserving and remain CLI-side.

## UI evidence

`app/packaging/ui-evidence.py` drives a real headless Chrome against the running application over
CDP (standard library only) and captures every view into `temp/ui-evidence/`. The screenshots are
of the shipped UI reading a live registry — not mockups.
