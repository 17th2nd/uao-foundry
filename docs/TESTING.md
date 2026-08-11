# Testing and Acceptance Gates

The Foundry is accepted only through executable evidence.

## Local / CI command

```bash
mvn -B -ntp clean verify
```

## Required CI gates

1. Java 21 compile and JUnit tests.
2. Executable JAR packaging.
3. Production-source demonstration-identity leak scan.
4. Full manufacture of three structurally different fixture domains through the same JAR.
5. Package verifier success for every cross-domain package.
6. Repeated identical manufacture produces a byte-identical package tree.
7. Resume reuses at least ten previously completed stages and preserves byte identity.
8. Source snapshot tampering must make `verify` fail.
9. Provider candidates reconstruct exactly into accepted plus quarantined projections; omission, injection, candidate-ID substitution, category substitution, duplication and altered quarantine records must fail standalone verification.
10. A provider relationship cannot be removed from candidate/unresolved projections and elevated to `EXPERIMENTAL`.
11. Registry occurrences expose deterministic semantic-variant digests; same variants repeat safely, differing variants remain preserved and unresolved.
12. Automatic discovery/reuse refuses unresolved or newly divergent variants without registry mutation.
13. `ReuseAnalyzer` independently rejects a direct registry-evidence hash mismatch.

## Test semantics

Fixture facts are synthetic, explicitly non-authoritative data used to exercise the manufacturing mechanism. Passing fixture tests proves the tested structural, reconciliation, content-addressing, custody and fail-closed behaviours. It does not prove external authenticity, factual correctness or completeness of a real-world UAO.
