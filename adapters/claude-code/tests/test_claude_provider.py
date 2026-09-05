#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parents[3]
ADAPTER = REPO_ROOT / "adapters" / "claude-code" / "claude_provider.py"
SCHEMA = json.loads((REPO_ROOT / "schemas" / "fixture-bundle.schema.json").read_text())


def load_adapter_module():
    spec = importlib.util.spec_from_file_location("uao_claude_provider_test", ADAPTER)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


FAKE_CLAUDE = r'''#!/usr/bin/env python3
import json, os, re, sys

if "--version" in sys.argv:
    print(os.environ.get("UAO_FOUNDRY_FAKE_CLAUDE_VERSION", "2.1.999-test"))
    raise SystemExit(0)

def fail(msg):
    print("FAKE_CONTAINMENT_REJECT: " + msg, file=sys.stderr)
    raise SystemExit(97)

def parse(argv):
    singles = {"--bare", "--no-session-persistence", "--no-chrome", "--strict-mcp-config"}
    valued = {"-p", "--tools", "--permission-mode", "--max-turns", "--model", "--output-format", "--json-schema", "--max-budget-usd"}
    multi = {"--allowedTools", "--disallowedTools"}
    out = {}; i = 0
    while i < len(argv):
        token = argv[i]
        if token in singles:
            if token in out: fail("duplicate " + token)
            out[token] = True; i += 1; continue
        if token in valued:
            if i + 1 >= len(argv): fail("missing value " + token)
            out[token] = argv[i+1]; i += 2; continue
        if token in multi:
            values=[]; i += 1
            while i < len(argv) and not argv[i].startswith("--"):
                values.append(argv[i]); i += 1
            if not values: fail("empty values " + token)
            out[token] = values; continue
        fail("unexpected positional/flag " + token)
    return out

a = parse(sys.argv[1:])
for flag in ("--bare","--no-session-persistence","--no-chrome","--strict-mcp-config"):
    if not a.get(flag): fail("missing " + flag)
if a.get("-p") != "Produce the UAO Foundry intermediate provider bundle from the supplied protocol payload.": fail("unexpected prompt positional")
if a.get("--tools") != "WebSearch,WebFetch": fail("tool restriction")
if a.get("--permission-mode") != "dontAsk": fail("permission mode")
if a.get("--allowedTools") != ["WebSearch","WebFetch"]: fail("allowed tools")
required_denied={"Bash","Read","Write","Edit","Glob","Grep","Agent","Skill","mcp__*"}
if not required_denied.issubset(set(a.get("--disallowedTools", []))): fail("disallowed tools")
try:
    turns=int(a.get("--max-turns", ""))
except ValueError: fail("max turns not int")
if not 1 <= turns <= 100: fail("max turns out of bounds")
if a.get("--output-format") != "json": fail("output format")
try:
    schema=json.loads(a.get("--json-schema", ""))
except Exception: fail("json schema not JSON")
expected=json.loads((os.environ["UAO_FOUNDRY_TEST_SCHEMA_PATH"] and open(os.environ["UAO_FOUNDRY_TEST_SCHEMA_PATH"]).read()))
if schema != expected: fail("json schema mismatch")
for secret in ("GITHUB_TOKEN","AWS_SECRET_ACCESS_KEY","NPM_TOKEN"):
    if secret in os.environ: fail("secret leaked: " + secret)
if os.environ.get("CLAUDE_CODE_SKIP_PROMPT_HISTORY") != "1": fail("prompt history not disabled")

mode = os.environ.get("UAO_FOUNDRY_FAKE_CLAUDE_MODE", "ok")
if mode == "command-error":
    print("synthetic provider failure", file=sys.stderr); raise SystemExit(7)
prompt = sys.stdin.read()
match = re.search(r"BEGIN_FOUNDRY_ENVELOPE_JSON\n(.*?)\nEND_FOUNDRY_ENVELOPE_JSON", prompt, re.S)
if not match: fail("missing envelope")
envelope=json.loads(match.group(1)); seed=envelope["request"]["identitySeed"]
slug=re.sub(r"[^a-z0-9]+","-",seed.lower()).strip("-") or "identity"
ctx=envelope.get("registryContext") or {}; matches=ctx.get("matches") or []
if matches:
    identity=matches[0]["identity"]; resolution=identity["resolutionKey"]; occurrence=identity["occurrences"][0]
    prefix=f"packages/{occurrence['packageId']}/"; relative=occurrence["canonicalPath"][len(prefix):]
    locator=f"registry://{occurrence['packageId']}/{relative}"; source_class="foundry-registry"
    source_content="MODEL MUST NOT CONTROL THESE REGISTRY BYTES"; license_value="MODEL-GUESSED"
else:
    resolution=f"foundry:v0.1:test:{slug}"; locator="https://example.invalid/fake-source"
    source_class="synthetic-web-reference"; source_content=f"Synthetic fake Claude evidence for {seed}."; license_value="TEST-SYNTHETIC"
if mode == "ephemeral-key": resolution="foundry:v0.1:test:claude-session-2026-08-10T01"
relationships=[]
if mode == "relationship": relationships=[{"candidateId":"rel-test","typeVersion":"asa.core/example@1","participants":[],"identityLiterals":{},"contextualBindings":[],"sourceRefs":["src-root"]}]
bundle={
 "fixtureVersion":"0.1.0","identitySeed":seed,"fixedClock":"2000-01-01T00:00:00Z","knowledgeHorizon":"2000-01-01T00:00:00Z",
 "interpretations":[{"candidateId":"int-root","label":seed,"definition":f"Selected test interpretation for {seed}.","semanticTypeProposal":"TestIdentity","confidence":0.9,"status":"SELECTED","references":[locator]}],
 "scopeResolution":{"selectedInterpretation":"int-root","scopeStatus":"MACHINE_SELECTED_EXPERIMENTAL","canonicalWorkingLabel":seed,"includedBoundaries":["test scope"],"excludedBoundaries":[],"excludedInterpretations":[],"unresolvedQuestions":[]},
 "manufacturingPlan":{"planVersion":"0.1.0","selectedIdentity":seed,"dimensions":["identity"],"neighbouringIdentities":[],"anticipatedSourceClasses":[source_class],"risks":[],"completionQuestions":[{"questionId":"q-identity","prompt":"Is the selected identity evidenced?","required":True}],"exclusions":[]},
 "sourceStrategy":{"strategyVersion":"0.1.0","sourceClasses":[{"classId":source_class,"purpose":"test evidence","priority":1}],"authorityNotes":["Fake Claude test provider only."],"safetyConstraints":["No authority claim."]},
 "sources":[{"sourceId":"src-root","locator":locator,"sourceClass":source_class,"retrievedAt":"2000-01-01T00:00:00Z","license":license_value,"content":source_content}],
 "candidates":{"identities":[{"candidateId":"cid-root","label":seed,"resolutionKey":resolution,"root":True,"aliases":[],"sourceRefs":["src-root"],"externalIdentifiers":{}}],"claims":[{"candidateId":"clm-root","subjectIdentityRef":"cid-root","statement":f"Test claim for {seed}.","channels":["foundry"],"sourceRefs":["src-root"]}],"relationships":relationships,"evidence":[{"evidenceId":"ev-root","sourceRef":"src-root","supportsCandidateRef":"clm-root","extract":"Synthetic test evidence.","locatorWithinSource":"test"}],"states":[],"events":[],"languageMappings":[]},
 "coverageAnswers":{"q-identity":"covered"}}
if mode == "malformed": bundle={"fixtureVersion":"0.1.0","identitySeed":seed}
if mode == "top-level":
    print(json.dumps(bundle,separators=(",",":"))); raise SystemExit(0)
if mode == "result-fallback":
    print(json.dumps({"type":"result","result":json.dumps(bundle,separators=(",",":"))},separators=(",",":"))); raise SystemExit(0)
print(json.dumps({"type":"result","structured_output":bundle},separators=(",",":")))
'''


def protocol(seed: str, registry_context=None):
    envelope={"protocolVersion":"0.1.0","request":{"schemaVersion":"0.1.0","requestId":"req-test","identitySeed":seed,"inputLanguage":"en","manufacturingProfile":"experimental","executionMode":"live","requestedVersion":"0.1.0"},"constraints":{"canonicalWriteAllowed":False,"responseSchema":"fixture-bundle.schema.json","responseRole":"INTERMEDIATE_PROVIDER_BUNDLE_ONLY"}}
    if registry_context is not None:
        envelope["registryContext"]=registry_context
        envelope["constraints"]["reusePreference"]="REUSE_VERIFIED_REGISTRY_IDENTITIES_BEFORE_NEW_ACQUISITION"
    return envelope


class ClaudeProviderAdapterTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name); self.fake=self.root/"fake-claude"
        self.fake.write_text(FAKE_CLAUDE,encoding="utf-8"); self.fake.chmod(self.fake.stat().st_mode|stat.S_IXUSR|stat.S_IXGRP|stat.S_IXOTH)
        # The CLI receives the composed, $schema-free contract; the fake asserts byte-equality against it.
        self.cli_schema=self.root/"cli-schema.json"; self.cli_schema.write_text(json.dumps(load_adapter_module()._cli_schema(SCHEMA),sort_keys=True),encoding="utf-8")

    def tearDown(self): self.temp.cleanup()

    def env(self, mode="ok", extra=None):
        env=os.environ.copy(); env.update({"UAO_FOUNDRY_CLAUDE_BIN":str(self.fake),"UAO_FOUNDRY_FIXED_CLOCK":"2026-08-10T00:00:00Z","UAO_FOUNDRY_FAKE_CLAUDE_MODE":mode,"UAO_FOUNDRY_TEST_SCHEMA_PATH":str(self.cli_schema)})
        if extra: env.update(extra)
        return env

    def run_adapter(self,envelope,mode="ok",extra_env=None):
        return subprocess.run([sys.executable,str(ADAPTER)],input=json.dumps(envelope),text=True,capture_output=True,cwd=REPO_ROOT,env=self.env(mode,extra_env),check=False)

    def test_new_identity_emits_clean_intermediate_bundle(self):
        proc=self.run_adapter(protocol("alpha object")); self.assertEqual(0,proc.returncode,proc.stderr); bundle=json.loads(proc.stdout)
        self.assertEqual("foundry:v0.1:test:alpha-object",bundle["candidates"]["identities"][0]["resolutionKey"])
        self.assertEqual([],bundle["candidates"]["relationships"]); self.assertEqual("",proc.stderr)
        notes=bundle["sourceStrategy"]["authorityNotes"]
        self.assertTrue(any("output_path=structured_output" in n for n in notes))

    def test_registry_source_bytes_are_restored_exactly(self):
        package_id="pkg-test1234567890"; canonical_path=f"packages/{package_id}/canonical-identities.json"; package_file=self.root/canonical_path
        package_file.parent.mkdir(parents=True); exact='[{"uid":"uao-123456abcdef","internal_state":{"foundry_identity":{"resolution_key":"foundry:v0.1:test:alpha-object"}}}]\n'; package_file.write_text(exact)
        identity={"uid":"uao-123456abcdef","resolutionKey":"foundry:v0.1:test:alpha-object","canonicalLabels":["alpha object"],"aliases":[],"occurrences":[{"packageId":package_id,"canonicalPath":canonical_path}]}
        context={"registryVersion":"0.1.0","query":"alpha object","totalIdentities":1,"catalogTruncated":False,"matches":[{"matchKinds":["LABEL"],"identity":identity}],"catalog":[identity]}
        proc=self.run_adapter(protocol("alpha object",context),extra_env={"UAO_FOUNDRY_REGISTRY_ROOT":str(self.root)})
        self.assertEqual(0,proc.returncode,proc.stderr); source=json.loads(proc.stdout)["sources"][0]
        self.assertEqual(exact,source["content"]); self.assertEqual("UAO-FOUNDRY-REGISTRY-SNAPSHOT",source["license"])

    def test_ephemeral_resolution_key_fails_closed(self):
        proc=self.run_adapter(protocol("alpha object"),"ephemeral-key"); self.assertNotEqual(0,proc.returncode); self.assertIn("ephemeral",proc.stderr.lower())

    def test_relationship_candidate_fails_closed(self):
        proc=self.run_adapter(protocol("alpha object"),"relationship"); self.assertNotEqual(0,proc.returncode); self.assertIn("relationship",proc.stderr.lower())

    def test_malformed_structured_output_fails_closed(self):
        proc=self.run_adapter(protocol("alpha object"),"malformed"); self.assertNotEqual(0,proc.returncode); self.assertIn("missing required bundle field",proc.stderr.lower())

    def test_top_level_bundle_fallback_is_rejected(self):
        proc=self.run_adapter(protocol("alpha object"),"top-level"); self.assertNotEqual(0,proc.returncode); self.assertIn("structured_output",proc.stderr)

    def test_result_json_fallback_is_marked(self):
        proc=self.run_adapter(protocol("alpha object"),"result-fallback"); self.assertEqual(0,proc.returncode,proc.stderr)
        notes=json.loads(proc.stdout)["sourceStrategy"]["authorityNotes"]
        self.assertTrue(any("output_path=result-json-fallback" in n for n in notes))

    def test_claude_command_failure_is_bounded_and_fails_closed(self):
        proc=self.run_adapter(protocol("alpha object"),"command-error"); self.assertNotEqual(0,proc.returncode); self.assertIn("exited 7",proc.stderr)

    def test_minimum_version_gate(self):
        for version,ok in (("2.1.204",False),("2.1.205",True),("Claude Code 2.2.0",True),("unavailable",False)):
            with self.subTest(version=version):
                proc=self.run_adapter(protocol("alpha object"),extra_env={"UAO_FOUNDRY_FAKE_CLAUDE_VERSION":version})
                self.assertEqual(ok,proc.returncode==0,proc.stderr)

    def test_environment_allowlist_and_endpoint_provenance(self):
        proc=self.run_adapter(protocol("alpha object"),extra_env={"GITHUB_TOKEN":"CANARY","AWS_SECRET_ACCESS_KEY":"CANARY","NPM_TOKEN":"CANARY","ANTHROPIC_BASE_URL":"https://provider.example.invalid","ANTHROPIC_AUTH_TOKEN":"provider-secret"})
        self.assertEqual(0,proc.returncode,proc.stderr); notes=json.loads(proc.stdout)["sourceStrategy"]["authorityNotes"]
        self.assertTrue(any("endpoint override=https://provider.example.invalid" in n for n in notes)); self.assertFalse(any("provider-secret" in n for n in notes))

    def test_budget_validation(self):
        for value in ("nope","0","-1","1000.01","NaN"):
            with self.subTest(value=value):
                proc=self.run_adapter(protocol("alpha object"),extra_env={"UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD":value})
                self.assertNotEqual(0,proc.returncode); self.assertIn("BUDGET",proc.stderr.upper())
        proc=self.run_adapter(protocol("alpha object"),extra_env={"UAO_FOUNDRY_CLAUDE_MAX_BUDGET_USD":"3.50"})
        self.assertEqual(0,proc.returncode,proc.stderr)

    def test_hostile_argv_mutations_are_rejected_before_bundle(self):
        module=load_adapter_module(); base=module._claude_command(str(self.fake),SCHEMA,"sonnet",8,"3.5")
        def remove_flag(args,flag,values=0):
            out=list(args); i=out.index(flag); del out[i:i+1+values]; return out
        mutations=[]
        a=list(base); i=a.index("--allowedTools")+1; a.insert(i,"Bash"); mutations.append(a)
        mutations.append(remove_flag(base,"--disallowedTools",9))
        a=list(base); a.remove("Skill"); mutations.append(a)
        a=list(base); a[a.index("dontAsk")]="bypassPermissions"; mutations.append(a)
        a=list(base); a[a.index("WebSearch,WebFetch")]="default"; mutations.append(a)
        mutations.append(remove_flag(base,"--tools",1))
        mutations.append(remove_flag(base,"--no-session-persistence"))
        mutations.append(remove_flag(base,"--no-chrome"))
        mutations.append(remove_flag(base,"--json-schema",1))
        mutations.append(remove_flag(base,"--max-turns",1))
        mutations.append(remove_flag(base,"--bare"))
        mutations.append(remove_flag(base,"--strict-mcp-config"))
        prompt="BEGIN_FOUNDRY_ENVELOPE_JSON\n"+json.dumps(protocol("alpha object"))+"\nEND_FOUNDRY_ENVELOPE_JSON\n"
        for idx,command in enumerate(mutations):
            with self.subTest(mutation=idx):
                proc=subprocess.run(command,input=prompt,text=True,capture_output=True,env=module._claude_environment(),check=False)
                self.assertNotEqual(0,proc.returncode); self.assertIn("FAKE_CONTAINMENT_REJECT",proc.stderr); self.assertEqual("",proc.stdout)


if __name__ == "__main__": unittest.main()
