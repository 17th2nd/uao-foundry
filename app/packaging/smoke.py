#!/usr/bin/env python3
"""End-to-end smoke test against a running USI Foundry.

Standard library only. Drives the real HTTP API so CI exercises the application an operator uses,
not a mock of it.

  smoke.py <base-url> <demonstration-dir> [--accumulate N]

Default: manufacture -> register -> rediscover by durable external identifier -> reuse -> prove an
unrelated domain reuses nothing.
--accumulate N: manufacture identical material N times and assert no package-id collision (P9-1).
"""
import json, sys, time, urllib.error, urllib.request


def call(base, path, payload=None):
    if payload is None:
        request = urllib.request.Request(base + path)
    else:
        request = urllib.request.Request(base + path, data=json.dumps(payload).encode(),
                                         headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def manufacture(base, demo, identity, fixture):
    started = call(base, "/api/manufacture", {
        "identity": identity, "provider": "fixture",
        "fixture": f"{demo}/{fixture}", "register": True})
    token = started["jobToken"]
    for _ in range(600):
        status = call(base, "/api/manufacture/" + token)
        if status["state"] != "RUNNING":
            break
        time.sleep(0.25)
    if status["state"] != "COMPLETE":
        raise SystemExit(f"manufacture failed: {json.dumps(status.get('failure'))}")
    return status["result"]


def expect(condition, message):
    if not condition:
        raise SystemExit("FAIL: " + message)
    print("  ok  " + message)


def main():
    base = sys.argv[1].rstrip("/")
    demo = sys.argv[2].rstrip("/")
    accumulate = 0
    if "--accumulate" in sys.argv:
        accumulate = int(sys.argv[sys.argv.index("--accumulate") + 1])

    if accumulate:
        # Count the delta, not the total: this step may run against a store that already holds
        # records from an earlier step. The property under test is that every attempt leaves its
        # own record, which is a statement about the increase.
        before = len(call(base, "/api/runs")["runs"])
        ids = set()
        for i in range(1, accumulate + 1):
            result = manufacture(base, demo, "electric motor", "electric-motor.json")
            expect(result["registryAdmission"] == "REGISTERED", f"accumulation {i} admitted")
            if i >= 3:
                ids.add(result["packageId"])
        expect(len(ids) == 1, f"identical material keeps one package id after the second run ({ids})")
        expect(call(base, "/api/status")["registryVerification"] == "PASS", "registry still verifies")
        added = len(call(base, "/api/runs")["runs"]) - before
        expect(added == accumulate,
               f"every attempt left its own run record ({added} added for {accumulate} attempts)")
        return 0

    first = manufacture(base, demo, "electric motor", "electric-motor.json")
    expect(first["verification"] == "PASS", "electric motor verifies")
    expect(first["registryAdmission"] == "REGISTERED", "electric motor is admitted")
    expect(first["identifierScheme"] == "legacy-uao", "the identifier scheme is declared")
    expect(first["usiId"].startswith("uao-"), "the canonical identifier keeps the ASA-pinned shape")
    expect(first["counts"]["newIdentitiesManufactured"] == 3, "three identities manufactured")

    found = call(base, "/api/identity/wikidata%3AQ53068")
    expect(found["decision"] == "SAME", "rediscovered by durable external identifier")
    expect(found["reasonCodes"] == ["EXTERNAL_IDENTIFIER_CONTINUITY"], "for the right reason")

    second = manufacture(base, demo, "EV traction motor", "ev-traction-motor.json")
    expect(second["counts"]["existingIdentitiesReused"] == 3, "prior identities reused")
    expect(second["counts"]["newIdentitiesManufactured"] == 2, "only new semantic material manufactured")

    other = manufacture(base, demo, "tidal barrage", "tidal-barrage.json")
    expect(other["counts"]["existingIdentitiesReused"] == 0, "an unrelated domain reuses nothing")

    alias = call(base, "/api/identity/electric%20motor")
    expect(alias["decision"] == "UNRESOLVED", "an alias never resolves on its own")

    runs = call(base, "/api/runs")
    # Three manufactures ran above, so three run records must exist. Asserting the exact number
    # rather than a floor means a silently-dropped record fails the check.
    expect(len(runs["runs"]) == 3, "each of the three manufactures left exactly one run record")
    expect(all(r.get("packageId") for r in runs["runs"]), "each run record names its package")

    status = call(base, "/api/status")
    expect(status["registryVerification"] == "PASS", "registry verifies")
    expect(status["relationshipAuthority"] == "URO_TYPE_AUTHORITY_UNAVAILABLE",
           "relationship authority is reported as unavailable")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
