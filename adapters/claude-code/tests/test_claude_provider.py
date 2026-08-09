#!/usr/bin/env python3
from __future__ import annotations

import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest

REPO_ROOT = Path(__file__).resolve().parents[3]
ADAPTER = REPO_ROOT / "adapters" / "claude-code" / "claude_provider.py"


FAKE_CLAUDE = r'''#!/usr/bin/env python3
import json, os, re, sys
if "--version" in sys.argv:
    print("2.1.999-test")
    raise SystemExit(0)
mode = os.environ.get("FAKE_CLAUDE_MODE", "ok")
if mode == "command-error":
    print("synthetic provider failure", file=sys.stderr)
    raise SystemExit(7)
prompt = sys.stdin.read()
match = re.search(r"BEGIN_FOUNDRY_ENVELOPE_JSON\n(.*?)\nEND_FOUNDRY_ENVELOPE_JSON", prompt, re.S)
if not match:
    print(json.dumps({"type":"result","result":"missing envelope"}))
    raise SystemExit(0)
envelope = json.loads(match.group(1))
seed = envelope["request"]["identitySeed"]
slug = re.sub(r"[^a-z0-9]+", "-", seed.lower()).strip("-") or "identity"
ctx = envelope.get("registryContext") or {}
matches = ctx.get("matches") or []
if matches:
    identity = matches[0]["identity"]
    resolution = identity["resolutionKey"]
    occurrence = identity["occurrences"][0]
    prefix = f"packages/{occurrence['packageId']}/"
    relative = occurrence["canonicalPath"][len(prefix):]
    locator = f"registry://{occurrence['packageId']}/{relative}"
    source_class = "foundry-registry"
    source_content = "MODEL MUST NOT CONTROL THESE REGISTRY BYTES"
    license_value = "MODEL-GUESSED"
else:
    resolution = f"foundry:v0.1:test:{slug}"
    locator = "https://example.invalid/fake-source"
    source_class = "synthetic-web-reference"
    source_content = f"Synthetic fake Claude evidence for {seed}."
    license_value = "TEST-SYNTHETIC"
if mode == "ephemeral-key":
    resolution = "foundry:v0.1:test:claude-session-2026-08-10T01"
relationships = []
if mode == "relationship":
    relationships = [{"candidateId":"rel-test","typeVersion":"asa.core/example@1","participants":[],"identityLiterals":{},"contextualBindings":[],"sourceRefs":["src-root"]}]
bundle = {
  "fixtureVersion":"0.1.0",
  "identitySeed":seed,
  "fixedClock":"2000-01-01T00:00:00Z",
  "knowledgeHorizon":"2000-01-01T00:00:00Z",
  "interpretations":[{
    "candidateId":"int-root","label":seed,"definition":f"Selected test interpretation for {seed}.",
    "semanticTypeProposal":"TestIdentity","confidence":0.9,"status":"SELECTED","references":[locator]
  }],
  "scopeResolution":{
    "selectedInterpretation":"int-root","scopeStatus":"MACHINE_SELECTED_EXPERIMENTAL","canonicalWorkingLabel":seed,
    "includedBoundaries":["test scope"],"excludedBoundaries":[],"excludedInterpretations":[],"unresolvedQuestions":[]
  },
  "manufacturingPlan":{
    "planVersion":"0.1.0","selectedIdentity":seed,"dimensions":["identity"],"neighbouringIdentities":[],
    "anticipatedSourceClasses":[source_class],"risks":[],
    "completionQuestions":[{"questionId":"q-identity","prompt":"Is the selected identity evidenced?","required":True}],
    "exclusions":[]
  },
  "sourceStrategy":{
    "strategyVersion":"0.1.0",
    "sourceClasses":[{"classId":source_class,"purpose":"test evidence","priority":1}],
    "authorityNotes":["Fake Claude test provider only."],"safetyConstraints":["No authority claim."]
  },
  "sources":[{
    "sourceId":"src-root","locator":locator,"sourceClass":source_class,"retrievedAt":"2000-01-01T00:00:00Z",
    "license":license_value,"content":source_content
  }],
  "candidates":{
    "identities":[{
      "candidateId":"cid-root","label":seed,"resolutionKey":resolution,"root":True,"aliases":[],
      "sourceRefs":["src-root"],"externalIdentifiers":{}
    }],
    "claims":[{
      "candidateId":"clm-root","subjectIdentityRef":"cid-root","statement":f"Test claim for {seed}.",
      "channels":["foundry"],"sourceRefs":["src-root"]
    }],
    "relationships":relationships,
    "evidence":[{
      "evidenceId":"ev-root","sourceRef":"src-root","supportsCandidateRef":"clm-root",
      "extract":"Synthetic test evidence.","locatorWithinSource":"test"
    }],
    "states":[],"events":[],"languageMappings":[]
  },
  "coverageAnswers":{"q-identity":"covered"}
}
if mode == "malformed":
    bundle = {"fixtureVersion":"0.1.0","identitySeed":seed}
print(json.dumps({"type":"result","structured_output":bundle}, separators=(",",":")))
'''


def protocol(seed: str, registry_context=None):
    envelope = {
        "protocolVersion": "0.1.0",
        "request": {
            "schemaVersion": "0.1.0",
            "requestId": "req-test",
            "identitySeed": seed,
            "inputLanguage": "en",
            "manufacturingProfile": "experimental",
            "executionMode": "live",
            "requestedVersion": "0.1.0",
        },
        "constraints": {
            "canonicalWriteAllowed": False,
            "responseSchema": "fixture-bundle.schema.json",
            "responseRole": "INTERMEDIATE_PROVIDER_BUNDLE_ONLY",
        },
    }
    if registry_context is not None:
        envelope["registryContext"] = registry_context
        envelope["constraints"]["reusePreference"] = "REUSE_VERIFIED_REGISTRY_IDENTITIES_BEFORE_NEW_ACQUISITION"
    return envelope


class ClaudeProviderAdapterTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.fake = self.root / "fake-claude"
        self.fake.write_text(FAKE_CLAUDE, encoding="utf-8")
        self.fake.chmod(self.fake.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    def tearDown(self):
        self.temp.cleanup()

    def run_adapter(self, envelope, mode="ok", extra_env=None):
        env = os.environ.copy()
        env.update({
            "UAO_FOUNDRY_CLAUDE_BIN": str(self.fake),
            "UAO_FOUNDRY_FIXED_CLOCK": "2026-08-10T00:00:00Z",
            "FAKE_CLAUDE_MODE": mode,
        })
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            [sys.executable, str(ADAPTER)],
            input=json.dumps(envelope),
            text=True,
            capture_output=True,
            cwd=REPO_ROOT,
            env=env,
            check=False,
        )

    def test_new_identity_emits_clean_intermediate_bundle(self):
        proc = self.run_adapter(protocol("alpha object"))
        self.assertEqual(0, proc.returncode, proc.stderr)
        bundle = json.loads(proc.stdout)
        self.assertEqual("alpha object", bundle["identitySeed"])
        self.assertEqual("2026-08-10T00:00:00Z", bundle["fixedClock"])
        self.assertEqual("foundry:v0.1:test:alpha-object", bundle["candidates"]["identities"][0]["resolutionKey"])
        self.assertEqual([], bundle["candidates"]["relationships"])
        self.assertEqual("2026-08-10T00:00:00Z", bundle["sources"][0]["retrievedAt"])
        notes = bundle["sourceStrategy"]["authorityNotes"]
        self.assertTrue(any("adapter=0.1.0" in note for note in notes))
        self.assertTrue(any("non-authoritative" in note for note in notes))
        self.assertEqual("", proc.stderr)

    def test_registry_source_bytes_are_restored_exactly(self):
        package_id = "pkg-test1234567890"
        canonical_path = f"packages/{package_id}/canonical-identities.json"
        package_file = self.root / canonical_path
        package_file.parent.mkdir(parents=True)
        exact = '[{"uid":"uao-123456abcdef","internal_state":{"foundry_identity":{"resolution_key":"foundry:v0.1:test:alpha-object"}}}]\n'
        package_file.write_text(exact, encoding="utf-8")
        context = {
            "registryVersion":"0.1.0","query":"alpha object","totalIdentities":1,"catalogTruncated":False,
            "matches":[{
                "matchKinds":["LABEL"],
                "identity":{
                    "uid":"uao-123456abcdef","resolutionKey":"foundry:v0.1:test:alpha-object",
                    "canonicalLabels":["alpha object"],"aliases":[],
                    "occurrences":[{"packageId":package_id,"canonicalPath":canonical_path}]
                }
            }],
            "catalog":[{
                "uid":"uao-123456abcdef","resolutionKey":"foundry:v0.1:test:alpha-object",
                "canonicalLabels":["alpha object"],"aliases":[],
                "occurrences":[{"packageId":package_id,"canonicalPath":canonical_path}]
            }]
        }
        proc = self.run_adapter(protocol("alpha object", context), extra_env={"UAO_FOUNDRY_REGISTRY_ROOT": str(self.root)})
        self.assertEqual(0, proc.returncode, proc.stderr)
        bundle = json.loads(proc.stdout)
        source = bundle["sources"][0]
        self.assertEqual(f"registry://{package_id}/canonical-identities.json", source["locator"])
        self.assertEqual(exact, source["content"])
        self.assertEqual("UAO-FOUNDRY-REGISTRY-SNAPSHOT", source["license"])
        self.assertEqual("foundry-registry", source["sourceClass"])
        self.assertEqual("foundry:v0.1:test:alpha-object", bundle["candidates"]["identities"][0]["resolutionKey"])

    def test_ephemeral_resolution_key_fails_closed(self):
        proc = self.run_adapter(protocol("alpha object"), mode="ephemeral-key")
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("ephemeral", proc.stderr.lower())
        self.assertEqual("", proc.stdout)

    def test_relationship_candidate_fails_closed(self):
        proc = self.run_adapter(protocol("alpha object"), mode="relationship")
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("relationship", proc.stderr.lower())
        self.assertEqual("", proc.stdout)

    def test_malformed_structured_output_fails_closed(self):
        proc = self.run_adapter(protocol("alpha object"), mode="malformed")
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("missing required bundle field", proc.stderr.lower())
        self.assertEqual("", proc.stdout)

    def test_claude_command_failure_is_bounded_and_fails_closed(self):
        proc = self.run_adapter(protocol("alpha object"), mode="command-error")
        self.assertNotEqual(0, proc.returncode)
        self.assertIn("exited 7", proc.stderr)
        self.assertIn("synthetic provider failure", proc.stderr)
        self.assertEqual("", proc.stdout)


if __name__ == "__main__":
    unittest.main()
