#!/usr/bin/env python3
"""Claude Code adapter for the UAO Foundry provider protocol.

The adapter is intentionally not a canonical writer. It receives one Foundry
provider-protocol envelope on stdin, asks Claude Code for a schema-constrained
intermediate provider bundle, applies deterministic custody rules, and writes
only that bundle to stdout. The Java Foundry remains responsible for schema
validation, identity resolution, canonicalisation, verification and publication.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from decimal import Decimal, InvalidOperation
from datetime import datetime, timezone
from typing import Any

ADAPTER_VERSION = "0.1.0"
PROTOCOL_VERSION = "0.1.0"
BUNDLE_VERSION = "0.1.0"
DEFAULT_MODEL = "sonnet"
DEFAULT_MAX_TURNS = 8
DEFAULT_TIMEOUT_SECONDS = 240
DEFAULT_REGISTRY_EVIDENCE_BYTES = 1_000_000
DEFAULT_REGISTRY_EVIDENCE_FILES = 8
MIN_CLAUDE_VERSION = (2, 1, 205)
MAX_BUDGET_USD = Decimal("1000")

REPO_ROOT = Path(__file__).resolve().parents[2]
FOUNDRY_BUNDLE_SCHEMA = REPO_ROOT / "schemas" / "fixture-bundle.schema.json"

REQUIRED_BUNDLE_FIELDS = (
    "fixtureVersion",
    "identitySeed",
    "fixedClock",
    "knowledgeHorizon",
    "interpretations",
    "scopeResolution",
    "manufacturingPlan",
    "sourceStrategy",
    "sources",
    "candidates",
    "coverageAnswers",
)

EPHEMERAL_KEY_PATTERN = re.compile(
    r"(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|"
    r"\b(?:claude|sonnet|opus|session|conversation|turn)[-_:]|"
    r"\b20\d{2}[-_]\d{2}[-_]\d{2}[tT _-]?\d{2})",
    re.IGNORECASE,
)


def _die(message: str, code: int = 2) -> "NoReturn":  # type: ignore[name-defined]
    print(f"uao-foundry Claude adapter: {message}", file=sys.stderr)
    raise SystemExit(code)


def _read_json_object(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # pragma: no cover - defensive boundary
        _die(f"unable to read JSON {path}: {exc}")
    if not isinstance(value, dict):
        _die(f"{path} must contain a JSON object")
    return value


def _read_protocol() -> dict[str, Any]:
    raw = sys.stdin.read()
    if not raw.strip():
        _die("provider protocol stdin is empty")
    try:
        envelope = json.loads(raw)
    except json.JSONDecodeError as exc:
        _die(f"provider protocol stdin is invalid JSON: {exc}")
    if not isinstance(envelope, dict):
        _die("provider protocol root must be a JSON object")
    if envelope.get("protocolVersion") != PROTOCOL_VERSION:
        _die(f"unsupported protocolVersion: {envelope.get('protocolVersion')!r}")
    request = envelope.get("request")
    if not isinstance(request, dict) or not isinstance(request.get("identitySeed"), str) or not request["identitySeed"].strip():
        _die("protocol request.identitySeed must be a non-blank string")
    constraints = envelope.get("constraints")
    if not isinstance(constraints, dict):
        _die("protocol constraints must be an object")
    if constraints.get("canonicalWriteAllowed") is not False:
        _die("protocol must explicitly deny provider canonical-write authority")
    if constraints.get("responseRole") != "INTERMEDIATE_PROVIDER_BUNDLE_ONLY":
        _die("protocol responseRole must be INTERMEDIATE_PROVIDER_BUNDLE_ONLY")
    return envelope


def _iso_clock() -> str:
    configured = os.environ.get("UAO_FOUNDRY_FIXED_CLOCK", "").strip()
    if configured:
        if not configured.endswith("Z"):
            _die("UAO_FOUNDRY_FIXED_CLOCK must be an RFC3339 UTC value ending in Z")
        return configured
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    try:
        value = int(raw)
    except ValueError:
        _die(f"{name} must be an integer")
    if value < minimum or value > maximum:
        _die(f"{name} must be between {minimum} and {maximum}")
    return value


def _claude_binary() -> str:
    configured = os.environ.get("UAO_FOUNDRY_CLAUDE_BIN", "").strip()
    if configured:
        path = Path(configured).expanduser().resolve()
        if not path.is_file():
            _die(f"UAO_FOUNDRY_CLAUDE_BIN is not a file: {path}")
        if not os.access(path, os.X_OK):
            _die(f"UAO_FOUNDRY_CLAUDE_BIN is not executable: {path}")
        return str(path)
    discovered = shutil.which("claude")
    if not discovered:
        _die("Claude Code executable not found; install Claude Code or set UAO_FOUNDRY_CLAUDE_BIN")
    return discovered


def _claude_environment() -> dict[str, str]:
    source = os.environ
    env: dict[str, str] = {}
    for key in ("PATH", "HOME", "USER", "LOGNAME", "SHELL", "TMPDIR", "LANG", "TZ", "SSL_CERT_FILE", "SSL_CERT_DIR"):
        value = source.get(key)
        if value:
            env[key] = value
    for key, value in source.items():
        if (key.startswith("LC_") or key.startswith("UAO_FOUNDRY_")) and value:
            env[key] = value

    # Resolve at most one direct Anthropic credential. Local Claude credential files remain
    # reachable through HOME and do not require secret environment inheritance.
    oauth = source.get("CLAUDE_CODE_OAUTH_TOKEN", "").strip()
    api_key = source.get("ANTHROPIC_API_KEY", "").strip()
    if oauth:
        env["CLAUDE_CODE_OAUTH_TOKEN"] = oauth
    elif api_key:
        env["ANTHROPIC_API_KEY"] = api_key

    # Explicit custom-endpoint operation is permitted but provenance-visible. Never copy
    # unrelated cloud/CI secrets into the provider process.
    base_url = source.get("ANTHROPIC_BASE_URL", "").strip()
    auth_token = source.get("ANTHROPIC_AUTH_TOKEN", "").strip()
    if base_url:
        env["ANTHROPIC_BASE_URL"] = base_url
        if auth_token:
            env["ANTHROPIC_AUTH_TOKEN"] = auth_token

    env["CLAUDE_CODE_SKIP_PROMPT_HISTORY"] = "1"
    env["CLAUDE_CODE_DISABLE_FEEDBACK_SURVEY"] = "1"
    return env


def _claude_version(binary: str) -> str:
    try:
        proc = subprocess.run(
            [binary, "--version"],
            text=True,
            capture_output=True,
            timeout=10,
            check=False,
            env=_claude_environment(),
        )
    except Exception as exc:
        _die(f"unable to determine Claude Code version: {exc}")
    if proc.returncode != 0:
        _die(f"Claude Code --version exited {proc.returncode}")
    lines = (proc.stdout or proc.stderr or "").strip().splitlines()
    if not lines:
        _die("Claude Code --version returned no version")
    raw = lines[0][:200]
    match = re.search(r"(?<!\d)(\d+)\.(\d+)\.(\d+)(?!\d)", raw)
    if not match:
        _die(f"Claude Code version is unparseable: {raw!r}")
    parsed = tuple(int(match.group(i)) for i in range(1, 4))
    if parsed < MIN_CLAUDE_VERSION:
        minimum = ".".join(str(v) for v in MIN_CLAUDE_VERSION)
        _die(f"Claude Code {raw!r} is below required minimum v{minimum} for enforced --json-schema format handling")
    return raw


def _budget_usd() -> str | None:
    raw = os.environ.get("UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD", "").strip()
    if not raw:
        return None
    try:
        value = Decimal(raw)
    except InvalidOperation:
        _die("UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD must be a decimal number")
    if not value.is_finite() or value <= 0 or value > MAX_BUDGET_USD:
        _die(f"UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD must be > 0 and <= {MAX_BUDGET_USD}")
    return format(value.normalize(), "f")


def _registry_evidence(envelope: dict[str, Any]) -> tuple[list[dict[str, Any]], bool]:
    context = envelope.get("registryContext")
    if context is None:
        return [], False
    if not isinstance(context, dict):
        _die("registryContext must be an object when supplied")

    root_text = os.environ.get("UAO_FOUNDRY_REGISTRY_ROOT", "").strip()
    if not root_text:
        _die("registryContext was supplied but UAO_FOUNDRY_REGISTRY_ROOT is unavailable")
    root = Path(root_text).expanduser().resolve()
    if not root.is_dir():
        _die(f"UAO_FOUNDRY_REGISTRY_ROOT is not a directory: {root}")

    max_bytes = _bounded_int(
        "UAO_FOUNDRY_REGISTRY_EVIDENCE_BYTES",
        DEFAULT_REGISTRY_EVIDENCE_BYTES,
        1,
        8_000_000,
    )
    max_files = _bounded_int(
        "UAO_FOUNDRY_REGISTRY_EVIDENCE_FILES",
        DEFAULT_REGISTRY_EVIDENCE_FILES,
        1,
        100,
    )

    matches = context.get("matches", [])
    if not isinstance(matches, list):
        _die("registryContext.matches must be an array")

    records: list[dict[str, Any]] = []
    seen: set[str] = set()
    total = 0
    truncated = False

    for hit in matches:
        if not isinstance(hit, dict):
            continue
        identity = hit.get("identity")
        if not isinstance(identity, dict):
            continue
        occurrences = identity.get("occurrences", [])
        if not isinstance(occurrences, list):
            continue
        for occurrence in occurrences:
            if not isinstance(occurrence, dict):
                continue
            package_id = occurrence.get("packageId")
            canonical_path = occurrence.get("canonicalPath")
            if not isinstance(package_id, str) or not isinstance(canonical_path, str):
                continue
            expected_prefix = f"packages/{package_id}/"
            if not canonical_path.startswith(expected_prefix):
                _die(f"registry occurrence path is outside package {package_id}: {canonical_path}")
            relative = canonical_path[len(expected_prefix) :]
            locator = f"registry://{package_id}/{relative}"
            if locator in seen:
                continue
            path = (root / canonical_path).resolve()
            if root not in path.parents:
                _die(f"registry occurrence escapes registry root: {canonical_path}")
            if not path.is_file():
                _die(f"registry occurrence is missing: {path}")
            data = path.read_bytes()
            if len(records) >= max_files or total + len(data) > max_bytes:
                truncated = True
                continue
            try:
                content = data.decode("utf-8")
            except UnicodeDecodeError:
                _die(f"registry evidence is not UTF-8 text: {path}")
            records.append(
                {
                    "locator": locator,
                    "sha256": hashlib.sha256(data).hexdigest(),
                    "content": content,
                }
            )
            seen.add(locator)
            total += len(data)
    return records, truncated


def _build_prompt(envelope: dict[str, Any], evidence: list[dict[str, Any]], truncated: bool) -> str:
    return "\n".join(
        [
            "UAO FOUNDRY CLAUDE PROVIDER ADAPTER",
            "",
            "You are operating only as an evidence/research provider for the UAO Foundry.",
            "You have NO authority to create canonical UAOs, canonical UROs, publication decisions, or governance authority.",
            "Return only the structured provider bundle requested by the JSON schema supplied to Claude Code.",
            "",
            "Required behaviour:",
            "- Interpret the identity seed explicitly; preserve ambiguity instead of silently guessing.",
            "- Use WebSearch/WebFetch for current external research when needed.",
            "- Prefer primary/authoritative sources where practical and record the real locator.",
            "- `license` must be a known licence only when explicit; otherwise use `UNKNOWN` rather than inventing one.",
            "- Keep source content concise and sufficient for provenance; do not copy long copyrighted passages.",
            "- Every candidate identity and claim must cite one or more supplied sourceIds.",
            "- When evidence supports separable reusable component identities materially inside the requested scope, include them as non-root candidate identities rather than flattening everything into the root; do not enumerate speculative concepts merely to increase reuse.",
            "- Relationship candidates MUST be [] while the protocol lacks an authoritative Relationship Type role registry. Do not smuggle relationship semantics into a fake UAO relationship field.",
            "- If the registry contains the same semantic identity and reuse is justified, reuse its exact resolutionKey.",
            "- For a new identity with a durable external identifier use resolutionKey `ext:<scheme>:<identifier>`.",
            "- Otherwise use a stable `foundry:v0.1:<semantic-type-slug>:<canonical-label-slug>` resolutionKey.",
            "- Never use a UUID, timestamp, model/session/turn identifier, source ordering, confidence value, or conversational wording as semantic identity.",
            "- Candidate IDs/source IDs are local bundle handles, not semantic identity; keep them deterministic and readable.",
            "- Mark required completion questions unresolved when evidence is insufficient. Do not fabricate coverage.",
            "- completionQuestions are the plan's own definition of done for the selected scope (e.g. 'is the principal identity evidenced by a durable external identifier', 'are birth/death dates evidenced', 'is the principal contribution evidenced'); pose only questions you then research within this run, and answer each from the acquired sources: covered, partial (evidenced but not exhaustive), or unresolved. Do not pose open-ended survey questions (e.g. 'are there other people with this name') as completion questions; record such open matters in scopeResolution.unresolvedQuestions instead. Any unresolved completion question makes the package non-publishable, which is correct when evidence is genuinely missing.",
            "- Cross-field rules the Foundry enforces: scopeResolution.selectedInterpretation must be one of interpretations[].candidateId; manufacturingPlan.selectedIdentity must equal scopeResolution.canonicalWorkingLabel exactly; every coverageAnswers key must be a manufacturingPlan.completionQuestions[].questionId; every claim subjectIdentityRef must be a candidate identity candidateId; every evidence supportsCandidateRef must be a candidate claim or identity candidateId; exactly one identity candidate has root=true; use scopeStatus MACHINE_SELECTED_EXPERIMENTAL when you selected the interpretation yourself, REQUIRES_SELECTION when genuinely ambiguous.",
            "- retrievedAt and fixedClock/knowledgeHorizon are RFC3339 UTC timestamps ending in Z.",
            "- externalIdentifiers keys are lower-case scheme slugs (wikidata, viaf, isni, orcid, mathgenealogy, loc, gnd, doi, isbn) and values contain no whitespace.",
            "",
            "Registry evidence below is immutable Foundry evidence. If you cite a registry:// locator, reproduce its locator exactly. The adapter will restore the exact bytes after your response.",
            f"Registry evidence truncated: {str(truncated).lower()}",
            "",
            "BEGIN_FOUNDRY_ENVELOPE_JSON",
            json.dumps(envelope, sort_keys=True, separators=(",", ":"), ensure_ascii=False),
            "END_FOUNDRY_ENVELOPE_JSON",
            "",
            "BEGIN_REGISTRY_EVIDENCE_JSON",
            json.dumps(evidence, sort_keys=True, separators=(",", ":"), ensure_ascii=False),
            "END_REGISTRY_EVIDENCE_JSON",
        ]
    )


def _bare_mode() -> bool:
    """`--bare` is the default containment mode. Claude Code >= 2.1.2xx skips keychain and
    credential-file reads under --bare, so an operator authenticated only through the local
    credential file must opt out explicitly with UAO_FOUNDRY_CLAUDE_BARE=0. The choice is
    recorded in sourceStrategy.authorityNotes; it is never inferred."""
    raw = os.environ.get("UAO_FOUNDRY_CLAUDE_BARE", "").strip().lower()
    if raw in ("", "1", "true", "yes"):
        return True
    if raw in ("0", "false", "no"):
        return False
    _die("UAO_FOUNDRY_CLAUDE_BARE must be 0/1")


def _stage_schema(name: str) -> dict[str, Any]:
    stage = _read_json_object(REPO_ROOT / "schemas" / name)
    stage.pop("$schema", None)
    stage.pop("title", None)
    return stage


def _cli_schema(schema: dict[str, Any]) -> dict[str, Any]:
    """Build the schema handed to Claude Code's --json-schema.

    Two adjustments to the checked-in fixture-bundle contract, neither of which changes what the
    Java Foundry later validates:
    - the `$schema` keyword is removed, because the CLI validator has no draft-2020-12
      meta-schema registered and rejects the whole schema when it is present;
    - the loosely typed provider sections (interpretations, scopeResolution, manufacturingPlan,
      sourceStrategy, candidate identities/claims/evidence) are tightened to the exact stage
      schemas the Java pipeline enforces at stages 3-9, so a live model is constrained up front
      instead of failing at the first strict stage. Relationships are capped at zero to match the
      adapter's fail-closed relationship rule (ASA#29).
    """
    cli = json.loads(json.dumps(schema))
    cli.pop("$schema", None)
    props = cli["properties"]
    props["interpretations"]["items"] = _stage_schema("interpretation-candidates.schema.json")["properties"]["interpretations"]["items"]
    props["scopeResolution"] = _stage_schema("scope-resolution.schema.json")
    props["manufacturingPlan"] = _stage_schema("manufacturing-plan.schema.json")
    props["sourceStrategy"] = _stage_schema("source-strategy.schema.json")
    candidates = props["candidates"]["properties"]
    identity = _stage_schema("candidate-identity.schema.json")
    # ExternalIdentifiers.requireCanonical: lower-case scheme, whitespace-free identifier.
    identity["properties"]["externalIdentifiers"]["propertyNames"] = {"pattern": "^[a-z][a-z0-9._-]*$"}
    identity["properties"]["externalIdentifiers"]["additionalProperties"] = {"type": "string", "pattern": "^\\S+$"}
    candidates["identities"]["items"] = identity
    candidates["claims"]["items"] = _stage_schema("candidate-claim.schema.json")
    candidates["evidence"]["items"] = _stage_schema("candidate-evidence.schema.json")
    candidates["relationships"]["maxItems"] = 0
    return cli


def _claude_command(binary: str, schema: dict[str, Any], model: str, max_turns: int, budget: str | None) -> list[str]:
    command = [
        binary,
        "-p",
        "Produce the UAO Foundry intermediate provider bundle from the supplied protocol payload.",
    ]
    if _bare_mode():
        command.append("--bare")
    command += [
        "--no-session-persistence",
        "--no-chrome",
        "--strict-mcp-config",
        "--tools",
        "WebSearch,WebFetch",
        "--permission-mode",
        "dontAsk",
        "--allowedTools",
        "WebSearch",
        "WebFetch",
        "--disallowedTools",
        "Bash",
        "Read",
        "Write",
        "Edit",
        "Glob",
        "Grep",
        "Agent",
        "Skill",
        "mcp__*",
        "--max-turns",
        str(max_turns),
        "--model",
        model,
        "--output-format",
        "json",
        "--json-schema",
        json.dumps(_cli_schema(schema), sort_keys=True, separators=(",", ":")),
    ]
    if budget is not None:
        command.extend(["--max-budget-usd", budget])
    return command


def _invoke_claude(binary: str, schema: dict[str, Any], prompt: str) -> tuple[dict[str, Any], str, str, str]:
    model = os.environ.get("UAO_FOUNDRY_CLAUDE_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL
    max_turns = _bounded_int("UAO_FOUNDRY_CLAUDE_MAX_TURNS", DEFAULT_MAX_TURNS, 1, 100)
    timeout = _bounded_int("UAO_FOUNDRY_CLAUDE_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1, 3600)
    budget = _budget_usd()
    command = _claude_command(binary, schema, model, max_turns, budget)

    try:
        proc = subprocess.run(
            command,
            input=prompt,
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
            env=_claude_environment(),
        )
    except subprocess.TimeoutExpired:
        _die(f"Claude Code exceeded adapter timeout of {timeout} seconds")
    except OSError as exc:
        _die(f"unable to execute Claude Code: {exc}")

    if proc.returncode != 0:
        stderr = (proc.stderr or "").strip()
        if len(stderr) > 4000:
            stderr = stderr[:4000] + " …<truncated>"
        _die(f"Claude Code exited {proc.returncode}: {stderr or '<no stderr>'}")

    stdout = (proc.stdout or "").strip()
    if not stdout:
        _die("Claude Code returned empty stdout")
    try:
        wrapper = json.loads(stdout)
    except json.JSONDecodeError as exc:
        _die(f"Claude Code JSON wrapper is invalid: {exc}")
    if not isinstance(wrapper, dict):
        _die("Claude Code JSON wrapper must be an object")

    structured = wrapper.get("structured_output")
    output_path = "structured_output"
    if isinstance(structured, str):
        try:
            structured = json.loads(structured)
        except json.JSONDecodeError as exc:
            _die(f"Claude Code structured_output string is invalid JSON: {exc}")
    if structured is None and isinstance(wrapper.get("result"), str):
        try:
            candidate = json.loads(wrapper["result"])
        except json.JSONDecodeError:
            candidate = None
        if isinstance(candidate, dict):
            structured = candidate
            output_path = "result-json-fallback"
    if not isinstance(structured, dict):
        _die("Claude Code response did not contain an object structured_output")
    return structured, model, (proc.stderr or ""), output_path


def _registry_resolution_keys(envelope: dict[str, Any]) -> set[str]:
    context = envelope.get("registryContext")
    if not isinstance(context, dict):
        return set()
    catalog = context.get("catalog", [])
    if not isinstance(catalog, list):
        return set()
    keys: set[str] = set()
    for identity in catalog:
        if isinstance(identity, dict) and isinstance(identity.get("resolutionKey"), str):
            keys.add(identity["resolutionKey"])
    return keys


def _enforce_resolution_key_policy(bundle: dict[str, Any], envelope: dict[str, Any]) -> None:
    candidates = bundle.get("candidates")
    if not isinstance(candidates, dict):
        _die("bundle.candidates must be an object")
    identities = candidates.get("identities")
    if not isinstance(identities, list) or not identities:
        _die("bundle.candidates.identities must be a non-empty array")
    registry_keys = _registry_resolution_keys(envelope)
    for candidate in identities:
        if not isinstance(candidate, dict):
            _die("candidate identity must be an object")
        key = candidate.get("resolutionKey")
        if not isinstance(key, str) or not key.strip():
            _die("candidate identity resolutionKey must be non-blank")
        if key in registry_keys:
            continue
        if not (key.startswith("ext:") or key.startswith("foundry:v0.1:")):
            _die(f"new live resolutionKey must be ext:* or foundry:v0.1:*; got {key!r}")
        if EPHEMERAL_KEY_PATTERN.search(key):
            _die(f"resolutionKey appears to contain ephemeral/model/session material: {key!r}")


def _restore_registry_sources(bundle: dict[str, Any], evidence: list[dict[str, Any]], clock: str) -> None:
    exact = {item["locator"]: item for item in evidence}
    sources = bundle.get("sources")
    if not isinstance(sources, list) or not sources:
        _die("bundle.sources must be a non-empty array")
    for source in sources:
        if not isinstance(source, dict):
            _die("bundle source must be an object")
        source["retrievedAt"] = clock
        locator = source.get("locator")
        if isinstance(locator, str) and locator.startswith("registry://"):
            item = exact.get(locator)
            if item is None:
                _die(f"model returned registry source outside bounded verified evidence set: {locator}")
            source["content"] = item["content"]
            source["license"] = "UAO-FOUNDRY-REGISTRY-SNAPSHOT"
            source["sourceClass"] = "foundry-registry"


def _normalize_bundle(
    bundle: dict[str, Any],
    envelope: dict[str, Any],
    evidence: list[dict[str, Any]],
    clock: str,
    model: str,
    cli_version: str,
    output_path: str,
) -> dict[str, Any]:
    normalized = json.loads(json.dumps(bundle, ensure_ascii=False))
    request = envelope["request"]
    normalized["fixtureVersion"] = BUNDLE_VERSION
    normalized["identitySeed"] = request["identitySeed"]
    normalized["fixedClock"] = clock
    normalized["knowledgeHorizon"] = clock

    for field in REQUIRED_BUNDLE_FIELDS:
        if field not in normalized:
            _die(f"Claude structured output is missing required bundle field {field}")

    relationships = normalized.get("candidates", {}).get("relationships") if isinstance(normalized.get("candidates"), dict) else None
    if relationships not in ([], None):
        _die("Claude adapter refuses non-empty relationship candidates until ASA Relationship Type authority is available")
    if isinstance(normalized.get("candidates"), dict):
        normalized["candidates"]["relationships"] = []

    _enforce_resolution_key_policy(normalized, envelope)
    _restore_registry_sources(normalized, evidence, clock)

    strategy = normalized.get("sourceStrategy")
    if not isinstance(strategy, dict):
        _die("bundle.sourceStrategy must be an object")
    notes = strategy.get("authorityNotes")
    if notes is None:
        notes = []
    if not isinstance(notes, list) or not all(isinstance(item, str) for item in notes):
        _die("sourceStrategy.authorityNotes must be an array of strings when present")
    notes.extend(
        [
            f"Claude Code provider adapter={ADAPTER_VERSION}; model={model}; cli={cli_version}; output_path={output_path}; bare={str(_bare_mode()).lower()}",
            "Claude research output is non-authoritative intermediate evidence; UAO Foundry owns validation, canonicalisation, verification and publication.",
            "Relationship candidate emission disabled pending governed ASA Relationship Type role authority (ASA#29 / uao-foundry#3).",
        ]
    )
    base_url = os.environ.get("ANTHROPIC_BASE_URL", "").strip()
    if base_url:
        notes.append(f"Claude provider endpoint override={base_url}")
    strategy["authorityNotes"] = notes
    return normalized


def main() -> int:
    envelope = _read_protocol()
    schema = _read_json_object(FOUNDRY_BUNDLE_SCHEMA)
    evidence, truncated = _registry_evidence(envelope)
    prompt = _build_prompt(envelope, evidence, truncated)
    binary = _claude_binary()
    version = _claude_version(binary)
    clock = _iso_clock()
    bundle, model, _stderr, output_path = _invoke_claude(binary, schema, prompt)
    normalized = _normalize_bundle(bundle, envelope, evidence, clock, model, version, output_path)
    # stdout is protocol data only. All diagnostics belong on stderr.
    sys.stdout.write(json.dumps(normalized, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        raise SystemExit(2)
