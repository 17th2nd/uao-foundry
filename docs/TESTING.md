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

## Test semantics

Fixture facts are synthetic, explicitly non-authoritative data used to exercise the manufacturing mechanism. Passing fixture tests proves pipeline behaviour and identity independence; it does not prove the truth or completeness of a real-world UAO.
