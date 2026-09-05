package org.seventeenthsecond.uaofoundry.verifier;

import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable digest over meaning-bearing core package projections. Excludes manifest/checksums/derived reuse report. */
public final class PackageContentDigest {
    public static final List<String> CORE_FILES = List.of(
            "manufacturing-request.json", "provider-snapshot.json", "identity-seed.json",
            "interpretation-candidates.json", "scope-resolution.json", "manufacturing-plan.json",
            "source-strategy.json", "source-registry.json", "candidate-identities.json",
            "candidate-relationships.json", "candidate-claims.json", "candidate-evidence.json",
            "candidate-states.json", "candidate-events.json", "candidate-language-mappings.json",
            "candidate-quarantine.json", "identity-resolution.json", "canonical-identities.json",
            "canonical-relationships.json", "provenance-ledger.json", "coverage-report.json",
            "verification-report.json", "unresolved-items.json", "publication-decision.json",
            "manufactured-package.json"
    );
    /** Present only in edition-aware manufactures; meaning-bearing when present. */
    public static final List<String> OPTIONAL_FILES = List.of("experimental-relationships.json", "relationship-type-edition.json");
    private PackageContentDigest() {}

    public static String compute(Path packageDir) {
        Map<String,Object> content = new LinkedHashMap<>();
        for (String relative : CORE_FILES) {
            Path path = packageDir.resolve(relative).normalize();
            if (!path.startsWith(packageDir.toAbsolutePath().normalize()) || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Meaning-bearing package file missing for content digest: " + relative);
            }
            content.put(relative, FileOps.readJson(path));
        }
        for (String relative : OPTIONAL_FILES) {
            Path path = packageDir.resolve(relative).normalize();
            if (path.startsWith(packageDir.toAbsolutePath().normalize()) && Files.isRegularFile(path)) content.put(relative, FileOps.readJson(path));
        }
        // Source bytes are transitively bound by source-registry sha256 values; verifySourceSnapshots enforces them.
        return Hashes.canonicalJson(content);
    }
}
