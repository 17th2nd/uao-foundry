# F-6 Closure Pass — Claude Code CLI/Tool Containment

**Auditor role:** independent assurance operator. No repository modification, no branch, no patch.
**Base audited:** `cb20687d0b0790622e5b20dd2a530fc9c03aa2cb` (accepted `main`)
**Remediation branch:** `sprint/2026-08-10-audit-remediation-r1` — **not inspected as a candidate**, per instruction (charter only, implementation not yet published).
**Adapter file:** `adapters/claude-code/claude_provider.py` @ base SHA.

---

## 1. Exact adapter argv assessment

### 1.1 Constructed command (`_invoke_claude`, line 286)

Built as a Python list — no shell interpolation, so no quoting or word-splitting hazards. Order as emitted:

| # | argv element | Notes |
|---|---|---|
| 0 | `<binary>` | resolved via `UAO_FOUNDRY_CLAUDE_BIN` (must be regular file + executable) else `shutil.which("claude")` |
| 1 | `-p` | print mode |
| 2 | `Produce the UAO Foundry intermediate provider bundle from the supplied protocol payload.` | fixed prompt string |
| 3 | `--bare` | |
| 4 | `--no-session-persistence` | |
| 5 | `--no-chrome` | |
| 6-7 | `--tools` `WebSearch,WebFetch` | **comma-separated single value** |
| 8-9 | `--permission-mode` `dontAsk` | |
| 10-12 | `--allowedTools` `WebSearch` `WebFetch` | **space-separated repeats** |
| 13-21 | `--disallowedTools` `Bash` `Read` `Write` `Edit` `Glob` `Grep` `Agent` `Skill` | **space-separated repeats** |
| 22-23 | `--max-turns` `<N>` | N bounded 1–100, default 8 |
| 24-25 | `--model` `<M>` | `UAO_FOUNDRY_CLAUDE_MODEL`, default `sonnet` |
| 26-27 | `--output-format` `json` | |
| 28-29 | `--json-schema` `<compact JSON>` | `schemas/fixture-bundle.schema.json`, `sort_keys=True`, separators `(",",":")` |
| 30-31 | `--max-budget-usd` `<value>` | **appended only if** `UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD` is non-blank |

No positional arguments other than the prompt at index 2. Ordering is not otherwise significant to the CLI, but the fake must not assume fixed indices — a remediated fake should parse, not index.

### 1.2 Process handling

- **stdin:** the full prompt (system framing + `BEGIN_FOUNDRY_ENVELOPE_JSON` + `BEGIN_REGISTRY_EVIDENCE_JSON`) piped via `subprocess.run(input=prompt, text=True)`.
- **stdout:** captured, parsed as the CLI JSON wrapper. Adapter's own stdout carries protocol data only.
- **stderr:** captured; on non-zero exit, truncated to 4000 chars and surfaced in the failure message.
- **timeout:** `subprocess.run(timeout=...)`, bounded 1–3600s (default 240). `TimeoutExpired` → `_die`.
- **exit code:** any non-zero → `_die`. Fail-closed.
- **environment:** `os.environ.copy()` plus `CLAUDE_CODE_SKIP_PROMPT_HISTORY=1` and `CLAUDE_CODE_DISABLE_FEEDBACK_SURVEY=1`. See §3.
- **`_claude_version`:** separate `[binary, "--version"]` call, 10s timeout, failures swallowed to the literal string `unavailable`. Result is recorded in `authorityNotes` and **never gated on**. See F-9.

---

## 2. CLI syntax verification against current documentation

Verified against the official Claude Code CLI reference and headless documentation.

### 2.1 My prior concern was wrong — I withdraw it

My earlier audit flagged the mixed comma/space encoding as a probable defect. **It is correct.** The documentation distinguishes the two option families deliberately:

- <cite index="20-1">`--tools` restricts which built-in tools Claude can use, with tool names given like `"Bash,Edit,Read"`</cite> — comma-separated. Adapter matches.
- <cite index="20-1">`--allowedTools` and `--disallowedTools` take space-separated quoted rule entries, documented with the example `"Bash(git log *)" "Bash(git diff *)" "Read"`</cite> — repeated values. Adapter matches.

The adapter's inconsistency mirrors a real inconsistency in the CLI. **No syntax defect.**

### 2.2 Flag-by-flag

| Flag | Valid? | Verified semantics |
|---|---|---|
| `--bare` | ✅ | <cite index="20-1">Skips auto-discovery of hooks, skills, plugins, MCP servers, auto memory and CLAUDE.md. Claude retains access to Bash, file read and file edit tools.</cite> **Note:** `--bare` itself grants those tools; restriction must come from `--tools`/`--disallowedTools`, and it does. |
| `--no-session-persistence` | ✅ | <cite index="20-1">Sessions are not saved to disk and cannot be resumed. Print mode only.</cite> `-p` is present. Adapter also sets the equivalent env var — belt and braces. |
| `--no-chrome` | ✅ | <cite index="20-1">Disables Chrome browser integration for the session.</cite> |
| `--tools WebSearch,WebFetch` | ✅ | Correct restriction mechanism. **But see F-10 on MCP.** |
| `--permission-mode dontAsk` | ✅ | <cite index="20-1">`dontAsk` is an accepted value.</cite> Correct choice — the SDK guidance is to <cite index="15-1">pair allowedTools with permissionMode "dontAsk" for a locked-down agent</cite>. Notably **not** `bypassPermissions`, which is right: a known CLI issue reports `--allowedTools` being ignored under `bypassPermissions`. |
| `--allowedTools` | ✅ | Semantically a *pre-approval* list, not a restriction: <cite index="20-1">it names tools that execute without prompting, and the reference explicitly directs you to `--tools` to restrict availability</cite>. The adapter uses both correctly. |
| `--disallowedTools` | ✅ | <cite index="20-1">A bare tool name removes matching tools from Claude's context</cite> — so the eight bare names do remove those tools, not merely deny calls. |
| `--json-schema` | ✅ syntax, ⚠️ version | See F-9. |
| `--output-format json` | ✅ | Print mode. |
| `--max-turns` | ✅ | <cite index="20-1">Print mode only; exits with an error when the limit is reached.</cite> Combined with the adapter's non-zero-exit rule this is fail-closed — a run that exhausts turns fails rather than returning partial evidence. Correct, but it means legitimate long research fails hard rather than degrading. |
| `--max-budget-usd` | ✅ | Print mode only. Value passed through unvalidated — see F-12. |

### 2.3 Documentation vs installed CLI

**No installed Claude Code executable was available in the audit environment**, so no `--help` cross-check was possible. All §2 conclusions rest on documentation alone. If the remediation environment has a real binary, `claude --help` should be captured and diffed against this table — noting that <cite index="20-1">`claude --help` does not list every flag, so absence from `--help` does not mean a flag is unavailable</cite>.

---

## 3. Environment inheritance assessment

`_claude_environment()` copies the entire parent environment.

| Class | Variables | Assessment |
|---|---|---|
| **Required for Claude operation** | `ANTHROPIC_API_KEY` **or** `CLAUDE_CODE_OAUTH_TOKEN` (whichever the workstation uses), `PATH`, `HOME`, `USER`, locale vars, `TMPDIR` | Must be inherited. Stripping these breaks authentication — the instruction not to over-sanitise is correct. |
| **Harmless operational context** | `TERM`, `LANG`, `LC_*`, `TZ`, `CLAUDE_CODE_SKIP_PROMPT_HISTORY`, `CLAUDE_CODE_DISABLE_FEEDBACK_SURVEY`, `UAO_FOUNDRY_*` | Retain. |
| **Behaviour-altering — the real risk** | `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, `ANTHROPIC_MODEL`, `ANTHROPIC_DEFAULT_*_MODEL`, `ANTHROPIC_CUSTOM_HEADERS`, `HTTP_PROXY`/`HTTPS_PROXY`, `CLAUDE_CODE_HOST_AUTH_ENV_VAR` | **See F-11.** These do not leak secrets outward; they silently redirect *where the research comes from*. |
| **Unrelated secrets** | `GITHUB_TOKEN`, `AWS_*`, `GCP_*`, `NPM_TOKEN`, `SSH_AUTH_SOCK`, CI secrets, `.env`-sourced app credentials | Should not be inherited. Residual risk only, given tool containment — but containment is exactly what is unproven. |

**Recommendation:** bounded allowlist rather than denylist. Pass through `PATH`, `HOME`, `USER`, `TMPDIR`, locale/TZ, the two `CLAUDE_CODE_*` flags the adapter sets, `UAO_FOUNDRY_*`, and exactly one authentication variable resolved explicitly. Any `ANTHROPIC_BASE_URL` / `ANTHROPIC_AUTH_TOKEN` in effect must be **recorded in `authorityNotes`**, not silently honoured.

---

## 4. Structured-output fallback assessment

Current chain in `_invoke_claude`:

1. `wrapper["structured_output"]` — object, or string that parses to one.
2. else `wrapper["result"]` if it parses as JSON.
3. else — **if all 11 `REQUIRED_BUNDLE_FIELDS` appear at the wrapper top level, accept the entire wrapper as the bundle.**

**Recommendation: remove step 3; narrow step 2.**

Step 3's security consequence is specific, not theoretical. The whole point of `--json-schema` is that the bundle arrives in a schema-constrained channel. Step 3 accepts a bundle that arrived through *no* constrained channel at all — free-form model text that happens to carry the right top-level keys. It is reachable precisely when the schema constraint failed, which is exactly the condition under which output should be rejected rather than salvaged. It also interacts badly with F-9: on a pre-v2.1.205 CLI, the schema is silently ignored and unstructured output returned — step 3 is the path that could let that through.

`_normalize_bundle` re-validates required fields, and the Java side re-validates against the schema, so a step-3 bundle is not unvalidated. But it is *unconstrained at generation*, and the adapter records no marker distinguishing the two. Fidelity, not just security: the package's `authorityNotes` would assert schema-constrained provenance that did not occur.

Step 2 should require the parsed value to be an object and be marked in `authorityNotes` as a fallback path.

---

## 5. Severity-ranked findings

### F-6a — Prior syntax concern withdrawn · **RESOLVED, NOT A DEFECT**
The comma/space split matches documented CLI behaviour (§2.1). My earlier audit was wrong on this point and the record should reflect that.

### F-6b — Containment argv asserted by nothing · **HIGH — STANDS**
The CI fake reads `sys.argv` only for `--version`; none of the six Python tests reference `allowedTools`, `disallowedTools`, `tools`, or `permission-mode`. Every control in §1.1 is unverified by any test. The design is now *documented-correct* (§2), which raises confidence — but correctness of intent is not evidence of transmission, and nothing detects a future regression.

### F-9 — `--json-schema` silently unenforced on pre-v2.1.205 CLI · **HIGH (new)**
`schemas/fixture-bundle.schema.json` contains three `"format": "date-time"` occurrences (`fixedClock`, `knowledgeHorizon`, `sources.items.retrievedAt`).

<cite index="23-1">Before v2.1.205, Claude Code silently ignored an invalid schema and returned unstructured text, and treated any schema containing `format` as invalid.</cite>

So on any Claude Code older than v2.1.205, the Foundry's schema is treated as invalid and the run proceeds **with no schema constraint and no error**. The adapter calls `--version`, stores the string, writes it into `authorityNotes` — and never compares it to anything. `_claude_version` even degrades a failed version probe to the literal `unavailable` and continues. `scripts/preflight-live.sh` prints the version but asserts nothing.

Downstream this is *probably* caught (missing `structured_output` → `_die`), but §4 step 3 is a live path around it, and "probably fails closed" is not a containment guarantee. A minimum-version gate is the fix.

### F-11 — Inherited environment can redirect the research provider · **MEDIUM–HIGH (new)**
`ANTHROPIC_BASE_URL` plus `ANTHROPIC_AUTH_TOKEN` route all Claude Code traffic to an arbitrary endpoint. Because the adapter inherits the full environment, anything that can set those variables in the invoking shell can substitute the research provider entirely — with no record anywhere in the manufactured package. `authorityNotes` records adapter version, model and CLI version; **not the endpoint**. The evidence would look identical to a genuine run.

This reframes environment inheritance: the sharper risk is not secrets leaking out, it is unrecorded substitution of the provenance source.

### F-10 — MCP tool surface denied only implicitly · **MEDIUM (new)**
<cite index="20-1">`--tools` does not affect MCP tools; to deny those, use `--disallowedTools "mcp__*"` or pass `--strict-mcp-config` without `--mcp-config` so no MCP servers load.</cite> The adapter does neither. It relies on `--bare` skipping MCP auto-discovery. That is currently sufficient, but the containment depends on a side effect of a performance flag. Removing or changing `--bare` would silently reopen the entire MCP tool surface. Make it explicit.

### F-12 — `--max-budget-usd` passed unvalidated · **LOW (new)**
`UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD` is `.strip()`ed and appended verbatim. Every other bound goes through `_bounded_int`. A malformed value reaches the CLI unchecked; the failure mode is a CLI error rather than an unbounded run, so severity is low — but it is the one bound not validated.

### F-13 — `EndConversation` cannot be removed · **INFORMATIONAL**
<cite index="20-1">A `--tools` list that omits `EndConversation` does not remove it, and a `--disallowedTools` rule naming it cannot remove it while any other tool remains.</cite> The gate below must therefore assert "no tools beyond the intended set **plus `EndConversation`**", or a correct implementation will fail C-1.

---

## 6. CLAUDE-CONTAINMENT-REMEDIATION-GATE

Binary and mechanically testable. Every gate must be enforced by a test that fails the build, not by inspection.

| ID | Gate | PASS criterion |
|---|---|---|
| **C-1** | Exact tool restriction | Fake asserts `--tools` present with value exactly `WebSearch,WebFetch` (comma-separated, single argv item). Effective tool set is that pair plus at most `EndConversation` (F-13). |
| **C-2** | Forbidden tool denial | Fake asserts `--disallowedTools` present and its value set is a superset of `{Bash, Read, Write, Edit, Glob, Grep, Agent, Skill}`, each as a **bare** name (no `Tool(...)` scoping, which would leave the tool available). |
| **C-3** | CLI argument syntax | Fake asserts encoding per §2.1: `--tools` comma-joined single value; `--allowedTools`/`--disallowedTools` space-separated repeats. Separately, a recorded `claude --help` (or version-pinned doc reference) is checked in as evidence. |
| **C-4** | Permission mode | Fake asserts `--permission-mode` present with value exactly `dontAsk`. Fails on `bypassPermissions`, `acceptEdits`, `auto`, `default`, `plan`, `manual`, or absence. |
| **C-5** | No session persistence | Fake asserts `--no-session-persistence` present **and** `CLAUDE_CODE_SKIP_PROMPT_HISTORY=1` in the received environment. |
| **C-6** | No Chrome | Fake asserts `--no-chrome` present. |
| **C-7** | Structured output contract | Fake asserts `--output-format json` and `--json-schema` present, and that the schema argument parses as JSON and equals `schemas/fixture-bundle.schema.json`. Fallback path per §4: step 3 removed; a test proves a wrapper carrying all 11 fields at top level with no `structured_output` is **rejected**. |
| **C-8** | Environment inheritance | Adapter passes a bounded allowlist. Test injects canaries (`GITHUB_TOKEN`, `AWS_SECRET_ACCESS_KEY`, `NPM_TOKEN`) into the parent and asserts the fake does **not** receive them. |
| **C-9** | Hostile argv mutation tests | For each mutation, CI must **fail**: (a) `--allowedTools Bash`; (b) `--disallowedTools` removed; (c) one name dropped from the disallow list; (d) `--permission-mode bypassPermissions`; (e) `--tools default` or `--tools` removed; (f) `--no-session-persistence` removed; (g) `--no-chrome` removed; (h) `--json-schema` removed; (i) `--max-turns` removed; (j) `--bare` removed. Each mutation is applied to the adapter under test and the job must go red. |
| **C-10** | No unexpected positional arguments | Fake asserts exactly one positional (the fixed prompt at index 2) and no unrecognised flags. |
| **C-11** | Minimum CLI version gate *(F-9)* | Adapter parses `--version` and **fails closed** below the minimum that enforces `--json-schema` with `format` (v2.1.205). A failed or `unavailable` version probe must abort, not proceed. Test covers below-minimum, at-minimum, and unparseable. |
| **C-12** | Explicit MCP denial *(F-10)* | Adapter passes `--strict-mcp-config` (without `--mcp-config`) or `--disallowedTools "mcp__*"`. Fake asserts it. Containment must not depend solely on `--bare`. |
| **C-13** | Endpoint provenance recorded *(F-11)* | If `ANTHROPIC_BASE_URL` / `ANTHROPIC_AUTH_TOKEN` are in effect, the base URL (never the token) is recorded in `sourceStrategy.authorityNotes`. Test asserts a redirected run is distinguishable in the package from a direct one. |
| **C-14** | All bounds validated *(F-12)* | `--max-budget-usd` validated like every other bound; malformed value fails closed before invocation. |
| **C-15** | Fake asserts *before* returning data | The fake must perform all assertions and exit non-zero **before** emitting any bundle JSON, so a weakened invocation cannot produce a usable provider response at all. |
| **C-16** | Bounds are passed and bounded | Fake asserts `--max-turns` present with an integer in `[1,100]`, and that `--max-budget-usd`, when configured, is present with the configured value. |
| **C-17** | URO fail-closed unchanged | Regression gate: adapter still refuses non-empty relationship candidates, Java still emits `URO_TYPE_AUTHORITY_UNAVAILABLE` for every candidate, and a relationship-bearing fixture still yields `EVIDENCE_INCOMPLETE`. **ASA#29 remains unresolved; remediation of Foundry integrity is not authority to enable arbitrary URO manufacture.** |

**Gate semantics:** C-1 through C-16 are PASS/FAIL on the remediation candidate. C-17 is a standing non-regression gate that must remain PASS on every future candidate.

---

## 7. Statement

**READY FOR REMEDIATION IMPLEMENTATION**

The F-6 evidential gap is now fully characterised. The adapter's intended containment is documented-correct — my prior syntax concern is withdrawn — and the remaining work is to make each control mechanically asserted rather than merely intended. Four new findings (F-9 through F-12) are folded into the gate above, of which F-9 (version-dependent silent loss of the schema constraint) and F-11 (unrecorded provider redirection) are the ones I would not ship without.

One scope limit stated plainly: **no installed Claude Code binary was available**, so §2 rests on documentation, not observed CLI behaviour. C-3 requires the implementation to capture real `--help` output as evidence. If documentation and the installed CLI disagree, that disagreement outranks this report.

No overall Foundry verdict is issued. That follows the fresh adversarial differential audit against the published remediation candidate, at which point the ten replay attacks will be re-run rather than checked off.
