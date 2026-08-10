# Claude Code CLI containment evidence — 2026-08-10

**Purpose:** version-pinned evidence for `CLAUDE-CONTAINMENT-REMEDIATION-GATE` C-3/C-11/C-12.
**Implementation branch:** `sprint/2026-08-10-audit-remediation-r1`
**No semantic authority is created by this document.**

Official current references checked on 2026-08-10:

- Claude Code CLI reference: `https://code.claude.com/docs/en/cli-usage`
- Claude Code release feed / v2.1.205: `https://github.com/anthropics/claude-code/releases/tag/v2.1.205`

Verified interface facts used by the adapter:

- `--tools "WebSearch,WebFetch"` is the documented comma-separated built-in tool restriction form.
- `--allowedTools` / `--disallowedTools` accept separate rule entries.
- a bare disallowed tool name removes that tool from model context.
- `--tools` does not govern MCP tools; the reference explicitly recommends `--disallowedTools "mcp__*"` or `--strict-mcp-config` without `--mcp-config`.
- `--no-session-persistence`, `--no-chrome`, `--permission-mode dontAsk`, `--output-format json`, `--json-schema`, and `--max-turns` are documented print-mode controls.
- `--bare` skips customization discovery but does not itself remove Bash/read/edit; therefore it is not treated as the tool-containment control.
- Claude Code v2.1.205 fixed `--json-schema` silently returning unstructured output for invalid schemas and fixed rejection of schemas containing the `format` keyword. The Foundry schema uses `format: date-time`, therefore v2.1.205 is the minimum accepted adapter version.

The implementation additionally uses both `--strict-mcp-config` and `mcp__*` denial as defence in depth.

A live workstation `claude --help` capture remains a field-validation artefact rather than a CI prerequisite because GitHub Actions intentionally uses a fake executable. The adapter itself fails closed below v2.1.205 or on an unparseable version.
