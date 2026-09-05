package org.seventeenthsecond.uaofoundry.verifier;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.identity.ExternalIdentifiers;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.identity.IdentityKernel;
import org.seventeenthsecond.uaofoundry.identity.IdentityProjections;
import org.seventeenthsecond.uaofoundry.identifiers.ResolutionKeys;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.significance.SignificanceBoundary;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;
import org.seventeenthsecond.uaofoundry.validation.ValidationResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class PackageVerifier {
    private final Path schemaDir;
    private final SchemaValidator validator = new SchemaValidator();

    public PackageVerifier(Path schemaDir) {
        this.schemaDir = schemaDir.toAbsolutePath().normalize();
    }

    public Result verify(Path packageDir) {
        packageDir = packageDir.toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        if (!Files.isDirectory(packageDir)) return new Result(false, List.of("Package directory does not exist: " + packageDir), List.of());

        Path checksumFile = packageDir.resolve("checksums.sha256");
        if (!Files.isRegularFile(checksumFile)) errors.add("checksums.sha256 is missing");
        else {
            checks.add("CHECKSUM_FILE_PRESENT");
            verifyChecksums(packageDir, checksumFile, errors);
        }

        Map<String, Object> manifest = readObject(packageDir.resolve("manifest.json"), "manifest", errors);
        Map<String, Object> manufactured = readObject(packageDir.resolve("manufactured-package.json"), "manufactured-package", errors);
        Object canonicalIdentities = readValue(packageDir.resolve("canonical-identities.json"), "canonical-identities", errors);
        Object canonicalRelationships = readValue(packageDir.resolve("canonical-relationships.json"), "canonical-relationships", errors);
        Map<String, Object> request = readObject(packageDir.resolve("manufacturing-request.json"), "manufacturing-request", errors);
        Map<String, Object> publication = readObject(packageDir.resolve("publication-decision.json"), "publication-decision", errors);
        Map<String, Object> verification = readObject(packageDir.resolve("verification-report.json"), "verification-report", errors);
        Map<String, Object> scope = readObject(packageDir.resolve("scope-resolution.json"), "scope-resolution", errors);
        Map<String, Object> resolution = readObject(packageDir.resolve("identity-resolution.json"), "identity-resolution", errors);
        Map<String, Object> coverage = readObject(packageDir.resolve("coverage-report.json"), "coverage-report", errors);
        Object candidateIdentities = readValue(packageDir.resolve("candidate-identities.json"), "candidate-identities", errors);
        Object candidateClaims = readValue(packageDir.resolve("candidate-claims.json"), "candidate-claims", errors);
        Object candidateRelationships = readValue(packageDir.resolve("candidate-relationships.json"), "candidate-relationships", errors);
        Object candidateEvidence = readValue(packageDir.resolve("candidate-evidence.json"), "candidate-evidence", errors);
        Object candidateStates = readValue(packageDir.resolve("candidate-states.json"), "candidate-states", errors);
        Object candidateEvents = readValue(packageDir.resolve("candidate-events.json"), "candidate-events", errors);
        Object candidateLanguageMappings = readValue(packageDir.resolve("candidate-language-mappings.json"), "candidate-language-mappings", errors);
        Object quarantine = readValue(packageDir.resolve("candidate-quarantine.json"), "candidate-quarantine", errors);
        Object provenanceLedger = readValue(packageDir.resolve("provenance-ledger.json"), "provenance-ledger", errors);
        Object unresolvedItems = readValue(packageDir.resolve("unresolved-items.json"), "unresolved-items", errors);
        Object providerSnapshot = readValue(packageDir.resolve("provider-snapshot.json"), "provider-snapshot", errors);
        Object experimentalRelationships = Files.isRegularFile(packageDir.resolve("experimental-relationships.json"))
                ? readValue(packageDir.resolve("experimental-relationships.json"), "experimental-relationships", errors) : null;
        Object editionDocument = Files.isRegularFile(packageDir.resolve("relationship-type-edition.json"))
                ? readValue(packageDir.resolve("relationship-type-edition.json"), "relationship-type-edition", errors) : null;

        if (manifest != null) {
            errors.addAll(prefix(validator.validate(manifest, schemaDir.resolve("release-manifest.schema.json")).errors(), "manifest: "));
            checks.add("MANIFEST_SCHEMA");
            verifyManifestInventory(packageDir, manifest, errors);
        }
        if (manufactured != null) {
            errors.addAll(prefix(validator.validate(manufactured, schemaDir.resolve("manufactured-package.schema.json")).errors(), "manufactured-package: "));
            checks.add("MANUFACTURED_PACKAGE_SCHEMA");
            collectForbidden(manufactured, "$", errors);
            checks.add("ASA_FORBIDDEN_FIELDS");
        }

        verifyCrossFileConsistency(manifest, manufactured, canonicalIdentities, canonicalRelationships, request, publication, verification, errors);
        checks.add("PACKAGE_CROSS_FILE_CONSISTENCY");
        verifyCanonicalIdentityDerivations(manifest, canonicalIdentities, errors);
        checks.add("UAO_IDENTITY_DERIVATION");

        if (providerSnapshot != null) {
            errors.addAll(prefix(validator.validate(providerSnapshot, schemaDir.resolve("fixture-bundle.schema.json")).errors(), "provider-snapshot: "));
        }
        checks.add("PROVIDER_SNAPSHOT_SCHEMA");
        Map<String,Object> packagedCandidates = new LinkedHashMap<>();
        packagedCandidates.put("identities", candidateIdentities);
        packagedCandidates.put("claims", candidateClaims);
        packagedCandidates.put("relationships", candidateRelationships);
        packagedCandidates.put("evidence", candidateEvidence);
        packagedCandidates.put("states", candidateStates);
        packagedCandidates.put("events", candidateEvents);
        packagedCandidates.put("languageMappings", candidateLanguageMappings);
        verifyProviderProjectionReconciliation(providerSnapshot, packagedCandidates, quarantine, errors);
        checks.add("PROVIDER_PROJECTION_RECONCILIATION");

        verifySemanticProjections(canonicalIdentities, canonicalRelationships, candidateIdentities, candidateClaims,
                candidateRelationships, candidateEvidence, quarantine, provenanceLedger, unresolvedItems,
                resolution, scope, coverage, verification, publication, experimentalRelationships, editionDocument, errors);
        checks.add("SEMANTIC_PROJECTION_RECONSTRUCTION");
        verifyContentAddress(packageDir, manifest, errors);
        checks.add("CONTENT_ADDRESSED_PACKAGE_ID");

        verifySourceSnapshots(packageDir, errors);
        checks.add("SOURCE_SNAPSHOT_HASHES");
        return new Result(errors.isEmpty(), List.copyOf(errors), List.copyOf(checks));
    }

    /**
     * Reconstructs the exact Foundry candidate-validation projection from the recorded provider input.
     * Accepted records are byte-semantically unchanged canonical JSON. The only quarantine transform is
     * the deterministic category/index/errors envelope around the exact provider record.
     */
    private void verifyProviderProjectionReconciliation(
            Object rawProviderSnapshot, Map<String,Object> packagedCandidates, Object rawQuarantine, List<String> errors) {
        Map<String,Object> snapshot = object(rawProviderSnapshot, "provider-snapshot", errors);
        if (snapshot == null) return;
        Map<String,Object> providerCandidates = object(snapshot.get("candidates"), "provider-snapshot.candidates", errors);
        if (providerCandidates == null) return;

        Map<String,String> validationSchemas = new LinkedHashMap<>();
        validationSchemas.put("identities", "candidate-identity.schema.json");
        validationSchemas.put("claims", "candidate-claim.schema.json");
        validationSchemas.put("relationships", "candidate-relationship.schema.json");
        validationSchemas.put("evidence", "candidate-evidence.schema.json");

        List<Object> expectedQuarantine = new ArrayList<>();
        for (Map.Entry<String,String> entry : validationSchemas.entrySet()) {
            String category = entry.getKey();
            Object providerValue = providerCandidates.get(category);
            if (!(providerValue instanceof List<?> providerRecords)) {
                errors.add("Provider projection reconciliation cannot read provider candidate category: " + category + ".");
                continue;
            }
            List<Object> expectedAccepted = new ArrayList<>();
            for (int index = 0; index < providerRecords.size(); index++) {
                Object providerRecord = providerRecords.get(index);
                ValidationResult validation = validator.validate(providerRecord, schemaDir.resolve(entry.getValue()));
                if (validation.valid()) {
                    expectedAccepted.add(Json.parse(Json.canonical(providerRecord)));
                } else {
                    Map<String,Object> quarantine = new LinkedHashMap<>();
                    quarantine.put("category", category);
                    quarantine.put("index", BigDecimal.valueOf(index));
                    quarantine.put("errors", new ArrayList<>(validation.errors()));
                    quarantine.put("record", Json.parse(Json.canonical(providerRecord)));
                    expectedQuarantine.add(quarantine);
                }
            }
            if (!canonicalEquals(expectedAccepted, packagedCandidates.get(category))) {
                errors.add("Provider projection reconciliation failed for " + category
                        + ": packaged accepted candidates are not the exact validated projection of provider-snapshot input.");
            }
        }

        for (String category : List.of("states", "events", "languageMappings")) {
            Object providerValue = providerCandidates.get(category);
            Object expected = providerValue == null ? List.of() : providerValue;
            if (!canonicalEquals(expected, packagedCandidates.get(category))) {
                errors.add("Provider projection reconciliation failed for " + category
                        + ": packaged candidates are not the exact provider-snapshot projection.");
            }
        }
        if (!canonicalEquals(expectedQuarantine, rawQuarantine)) {
            errors.add("Provider projection reconciliation failed for candidate-quarantine.json: category, index, validation errors, or exact provider record differs.");
        }
    }

    private boolean canonicalEquals(Object left, Object right) {
        if (left == null || right == null) return left == right;
        try { return Json.canonical(left).equals(Json.canonical(right)); }
        catch (IllegalArgumentException ex) { return false; }
    }

    private void verifyCrossFileConsistency(
            Map<String, Object> manifest,
            Map<String, Object> manufactured,
            Object canonicalIdentities,
            Object canonicalRelationships,
            Map<String, Object> request,
            Map<String, Object> publication,
            Map<String, Object> verification,
            List<String> errors) {
        if (manufactured == null) return;

        compareJson("manufactured-package.uaos", manufactured.get("uaos"), "canonical-identities.json", canonicalIdentities, errors);
        compareJson("manufactured-package.uros", manufactured.get("uros"), "canonical-relationships.json", canonicalRelationships, errors);
        compareJson("manufactured-package.request", manufactured.get("request"), "manufacturing-request.json", request, errors);
        compareJson("manufactured-package.publicationDecision", manufactured.get("publicationDecision"), "publication-decision.json", publication, errors);
        compareJson("manufactured-package.verification", manufactured.get("verification"), "verification-report.json", verification, errors);

        if (manifest != null) {
            Object manifestRoot = manifest.get("rootUaoId");
            Object manufacturedRoot = manufactured.get("rootUaoId");
            if (!java.util.Objects.equals(manifestRoot, manufacturedRoot)) errors.add("Manifest rootUaoId differs from manufactured package.");
            if (publication != null && !java.util.Objects.equals(manifest.get("publicationStatus"), publication.get("status"))) {
                errors.add("Manifest publication status differs from publication decision.");
            }
        }
    }

    private void compareJson(String leftLabel, Object left, String rightLabel, Object right, List<String> errors) {
        if (left == null || right == null) return;
        try {
            if (!Json.canonical(left).equals(Json.canonical(right))) {
                errors.add(leftLabel + " differs from " + rightLabel + ".");
            }
        } catch (IllegalArgumentException ex) {
            errors.add("Unable to compare " + leftLabel + " with " + rightLabel + ": " + ex.getMessage());
        }
    }

    private void verifyCanonicalIdentityDerivations(Map<String, Object> manifest, Object rawIdentities, List<String> errors) {
        if (!(rawIdentities instanceof List<?> identities)) {
            if (rawIdentities != null) errors.add("canonical-identities.json is not an array.");
            return;
        }
        Set<String> seenUids = new LinkedHashSet<>();
        Map<String, String> uidToResolutionKey = new LinkedHashMap<>();
        boolean rootPresent = false;
        String manifestRoot = manifest != null && manifest.get("rootUaoId") instanceof String s ? s : null;

        for (int i = 0; i < identities.size(); i++) {
            Object item = identities.get(i);
            if (!(item instanceof Map<?, ?> raw)) {
                errors.add("canonical-identities[" + i + "] is not an object.");
                continue;
            }
            @SuppressWarnings("unchecked") Map<String, Object> uao = (Map<String, Object>) raw;
            Object uidRaw = uao.get("uid");
            if (!(uidRaw instanceof String uid)) {
                errors.add("canonical-identities[" + i + "].uid is not a string.");
                continue;
            }
            if (!seenUids.add(uid)) errors.add("Duplicate canonical UAO uid: " + uid);
            if (uid.equals(manifestRoot)) rootPresent = true;

            Map<String, Object> internal = object(uao.get("internal_state"), "canonical-identities[" + i + "].internal_state", errors);
            if (internal == null) continue;
            Map<String, Object> foundryIdentity = object(internal.get("foundry_identity"), "canonical-identities[" + i + "].internal_state.foundry_identity", errors);
            if (foundryIdentity == null) continue;
            Object keyRaw = foundryIdentity.get("resolution_key");
            if (!(keyRaw instanceof String resolutionKey) || resolutionKey.isBlank()) {
                errors.add("canonical-identities[" + i + "] has no non-blank foundry resolution_key.");
                continue;
            }

            try { ResolutionKeys.requireCanonical(resolutionKey); }
            catch (IllegalArgumentException ex) { errors.add("Canonical UAO resolution_key is not canonical: " + ex.getMessage()); }
            String expected = StableIdentifiers.forText("uao", 12, resolutionKey);
            if (!uid.equals(expected)) {
                errors.add("Canonical UAO uid does not match deterministic resolution_key derivation: " + uid + " expected " + expected + ".");
            }
            String previous = uidToResolutionKey.putIfAbsent(uid, resolutionKey);
            if (previous != null && !previous.equals(resolutionKey)) {
                errors.add("Canonical UAO uid collision maps different resolution keys: " + uid + ".");
            }
        }
        if (manifestRoot != null && !rootPresent) errors.add("Manifest rootUaoId is absent from canonical-identities.json.");
    }

    private void verifyContentAddress(Path packageDir, Map<String,Object> manifest, List<String> errors) {
        if (manifest == null) return;
        Object rawDigest = manifest.get("contentDigest");
        if (!(rawDigest instanceof String expected) || !expected.matches("[a-f0-9]{64}")) return;
        try {
            String actual = PackageContentDigest.compute(packageDir);
            if (!expected.equals(actual)) errors.add("Manifest contentDigest differs from meaning-bearing package content.");
            String packageId = manifest.get("packageId") instanceof String s ? s : "";
            String derived = StableIdentifiers.forText("pkg", 16, actual);
            if (!packageId.equals(derived)) errors.add("Manifest packageId does not match contentDigest derivation: expected " + derived + ".");
        } catch (IllegalArgumentException ex) {
            errors.add("Unable to compute package contentDigest: " + ex.getMessage());
        }
    }

    private void verifySemanticProjections(
            Object canonicalIdentities, Object canonicalRelationships, Object candidateIdentities, Object candidateClaims,
            Object candidateRelationships, Object candidateEvidence, Object quarantine, Object provenanceLedger,
            Object unresolvedItems, Map<String,Object> resolution,
            Map<String,Object> scope, Map<String,Object> coverage, Map<String,Object> verification,
            Map<String,Object> publication, Object experimentalRelationships, Object editionDocument, List<String> errors) {
        if (!(canonicalIdentities instanceof List<?> uaos) || !(candidateIdentities instanceof List<?> identities)
                || !(candidateClaims instanceof List<?> claims) || !(candidateEvidence instanceof List<?> evidence)
                || !(provenanceLedger instanceof List<?> ledger) || resolution == null) return;

        Map<String,Object> expectedResolution = reconstructIdentityResolution(identities, errors);
        if (expectedResolution == null) return;

        // Identity decisions are the one part of the resolution stage that a package cannot fully
        // re-derive from its own bytes: whether a registry held a matching identity depends on
        // registry state that is deliberately not copied into the package. They are therefore
        // separated from the strict reconstruction comparison and checked for internal consistency
        // against the reconstructed identities instead. Everything else must still reconstruct
        // exactly.
        Map<String,Object> resolutionCore = new LinkedHashMap<>(resolution);
        Object recordedDecisions = resolutionCore.remove("identityDecisions");
        verifyIdentityDecisions(recordedDecisions, expectedResolution, errors);
        resolution = resolutionCore;
        if (!canonicalEquals(expectedResolution, resolution)) {
            errors.add("Identity resolution does not reconstruct exactly from accepted candidate identities.");
        }
        Map<String,Object> candidateToUao = object(expectedResolution.get("candidateToUao"), "reconstructed candidateToUao", errors);
        if (candidateToUao == null) return;

        Map<String,List<Map<String,Object>>> claimsByUao = new TreeMap<>();
        for (Object raw : claims) {
            Map<String,Object> claim = object(raw, "candidate claim", errors); if (claim == null) continue;
            Object cidRaw = claim.get("candidateId"), subjectRaw = claim.get("subjectIdentityRef");
            if (!(cidRaw instanceof String cid) || !(subjectRaw instanceof String subject)) continue;
            Object uidRaw = candidateToUao.get(subject);
            if (!(uidRaw instanceof String uid)) { errors.add("Candidate claim maps to no reconstructed UAO: " + cid); continue; }
            claimsByUao.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(claim);
        }
        claimsByUao.values().forEach(v -> v.sort(Comparator.comparing(c -> String.valueOf(c.get("candidateId")))));

        Map<String,List<Map<String,Object>>> evidenceByCandidate = new TreeMap<>();
        for (Object raw : evidence) {
            Map<String,Object> item = object(raw, "candidate evidence", errors); if (item == null) continue;
            if (item.get("supportsCandidateRef") instanceof String candidateRef) {
                evidenceByCandidate.computeIfAbsent(candidateRef, ignored -> new ArrayList<>()).add(item);
            }
        }

        Map<String,Map<String,Object>> actualByUid = new LinkedHashMap<>();
        for (Object raw : uaos) {
            Map<String,Object> uao = object(raw, "canonical UAO", errors); if (uao == null) continue;
            if (uao.get("uid") instanceof String uid && actualByUid.putIfAbsent(uid, uao) != null) {
                errors.add("Duplicate canonical UAO during semantic reconstruction: " + uid);
            }
        }

        List<Object> expectedLedger = new ArrayList<>();
        Set<String> expectedUids = new LinkedHashSet<>();
        for (Object raw : Json.array(expectedResolution.get("resolvedIdentities"), "reconstructed resolved identities")) {
            Map<String,Object> identity = object(raw, "reconstructed resolved identity", errors); if (identity == null) continue;
            String uid = String.valueOf(identity.get("uaoId"));
            expectedUids.add(uid);
            Map<String,Object> actual = actualByUid.get(uid);
            if (actual == null) { errors.add("Reconstructed UAO is absent from canonical identities: " + uid); continue; }

            Map<String,Object> expectedFoundryIdentity = IdentityKernel.build(identity);
            Map<String,Object> actualInternal = object(actual.get("internal_state"), "canonical UAO internal_state", errors);
            Map<String,Object> actualFoundryIdentity = actualInternal == null ? null
                    : object(actualInternal.get("foundry_identity"), "canonical UAO foundry_identity", errors);

            List<Object> expectedAssertions = new ArrayList<>();
            for (Map<String,Object> claim : claimsByUao.getOrDefault(uid, List.of())) {
                Map<String,Object> assertion = new LinkedHashMap<>();
                assertion.put("statement", claim.get("statement"));
                assertion.put("epistemic_class", "DEFERRED_ON_RECORD");
                Object rawChannels = claim.get("channels");
                List<?> channels = rawChannels instanceof List<?> list ? list : List.of();
                assertion.put("channels", channels.isEmpty() ? List.of("foundry") : Json.parse(Json.canonical(channels)));
                expectedAssertions.add(assertion);

                String candidateId = String.valueOf(claim.get("candidateId"));
                Map<String,Object> provenance = new LinkedHashMap<>();
                provenance.put("candidateId", candidateId);
                provenance.put("uaoId", uid);
                provenance.put("statement", claim.get("statement"));
                provenance.put("sourceRefs", claim.get("sourceRefs"));
                List<Object> evidenceRefs = new ArrayList<>();
                for (Map<String,Object> item : evidenceByCandidate.getOrDefault(candidateId, List.of())) {
                    evidenceRefs.add(item.get("evidenceId"));
                }
                provenance.put("evidenceRefs", evidenceRefs);
                expectedLedger.add(provenance);
            }
            if (!canonicalEquals(expectedAssertions, actual.get("assertions"))) {
                errors.add("Canonical assertions do not reconstruct exactly from candidate claims for UAO " + uid + ".");
            }
            if (!canonicalEquals(List.of(), actual.get("relationship_references"))) {
                errors.add("Canonical UAO relationship references are non-empty while Relationship Type role authority remains unavailable: " + uid + ".");
            }

            // The identity kernel's two digests are derived, never authored. Re-derive both from
            // the independently reconstructed identity and state projections so a package cannot
            // carry an identity_digest or state_version that its own content does not support.
            expectedFoundryIdentity.put("state_version", IdentityProjections.stateVersion(
                    actual.get("lifecycle_status"), actual.get("successor_identity_ref"),
                    expectedAssertions, List.of()));
            if (!canonicalEquals(expectedFoundryIdentity, actualFoundryIdentity)) {
                errors.add("Canonical Foundry identity kernel does not reconstruct from candidate identities for UAO " + uid + ".");
            }
        }
        if (!actualByUid.keySet().equals(expectedUids)) errors.add("Canonical UAO set differs from reconstructed candidate identities.");
        expectedLedger.sort(Comparator.comparing(v -> String.valueOf(object(v, "expected provenance", errors).get("candidateId"))));
        if (!canonicalEquals(expectedLedger, ledger)) {
            errors.add("Provenance ledger does not reconstruct exactly from candidate claims and evidence.");
        }

        if (candidateRelationships instanceof List<?> relationships && unresolvedItems instanceof List<?> unresolved && editionDocument != null) {
            // Edition-aware reconstruction (Experiment 002): every typed record and every unresolved
            // finding must re-derive from the package's candidates and its embedded edition copy.
            org.seventeenthsecond.uaofoundry.relationship.RelationshipTypeEdition edition = null;
            try {
                Map<String,Object> doc = object(editionDocument, "relationship-type-edition", errors);
                if (doc != null) edition = org.seventeenthsecond.uaofoundry.relationship.RelationshipTypeEdition.fromDocument(doc, "package");
            } catch (IllegalArgumentException ex) {
                errors.add("Embedded relationship type edition fails closed: " + ex.getMessage());
            }
            if (edition != null) {
                Map<String,String> labelsByUid = new TreeMap<>();
                if (expectedResolution.get("resolvedIdentities") instanceof List<?> resolvedList) {
                    for (Object raw : resolvedList) {
                        Map<String,Object> identity = object(raw, "resolved identity", errors);
                        if (identity != null) labelsByUid.put(String.valueOf(identity.get("uaoId")), String.valueOf(identity.get("label")));
                    }
                }
                List<Object> expectedUnresolved = new ArrayList<>();
                List<Object> expectedExperimental = new ArrayList<>();
                for (Object raw : relationships) {
                    Map<String,Object> rel = object(raw, "candidate relationship", errors);
                    if (rel == null) continue;
                    try {
                        var outcome = org.seventeenthsecond.uaofoundry.relationship.ExperimentalRelationships.build(rel, candidateToUao, labelsByUid, edition);
                        if (outcome.record() != null) expectedExperimental.add(outcome.record()); else expectedUnresolved.add(outcome.unresolved());
                    } catch (IllegalArgumentException ex) {
                        errors.add("Relationship candidate " + rel.get("candidateId") + " cannot be reconstructed: " + ex.getMessage());
                    }
                }
                expectedExperimental.sort(Comparator.comparing(v -> String.valueOf(((Map<?,?>) v).get("relationshipId"))));
                if (!canonicalEquals(expectedUnresolved, unresolved)) errors.add("Unresolved relationship projection does not reconstruct exactly from candidate relationships under the embedded relationship type edition.");
                if (!(experimentalRelationships instanceof List<?>) || !canonicalEquals(expectedExperimental, experimentalRelationships)) {
                    errors.add("Experimental relationship records do not reconstruct exactly from candidate relationships under the embedded relationship type edition.");
                }
                if (experimentalRelationships instanceof List<?> records) {
                    for (Object raw : records) {
                        Map<String,Object> record = object(raw, "experimental relationship", errors);
                        if (record == null) continue;
                        errors.addAll(prefix(validator.validate(record, schemaDir.resolve("experimental-relationship.schema.json")).errors(), "Relationship " + record.get("relationshipId") + ": "));
                        if (record.get("stateVersion") instanceof String stateVersion
                                && !stateVersion.equals(Hashes.canonicalJson(org.seventeenthsecond.uaofoundry.relationship.ExperimentalRelationships.stateProjection(record)))) {
                            errors.add("Relationship " + record.get("relationshipId") + " stateVersion does not re-derive from its own record.");
                        }
                    }
                }
            }
            if (canonicalRelationships instanceof List<?> uros && !uros.isEmpty()) errors.add("Canonical UROs are present while Relationship Type role authority remains unavailable.");
        } else if (candidateRelationships instanceof List<?> relationships && unresolvedItems instanceof List<?> unresolved) {
            if (experimentalRelationships instanceof List<?> records && !records.isEmpty()) {
                errors.add("Experimental relationship records are present without an embedded relationship type edition.");
            }
            List<Object> expectedUnresolved = new ArrayList<>();
            for (Object raw : relationships) {
                Map<String,Object> rel = object(raw, "candidate relationship", errors);
                if (rel == null) continue;
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("candidateId", rel.get("candidateId"));
                item.put("code", "URO_TYPE_AUTHORITY_UNAVAILABLE");
                item.put("description", "Current ASA CSS defines URO structure but the Foundry has no current authoritative domain Relationship Type role registry to validate this candidate. Publication of this URO is fail-closed.");
                item.put("typeVersion", rel.get("typeVersion"));
                List<Object> participants = new ArrayList<>();
                int bound = 0;
                if (rel.get("participants") instanceof List<?> rawParticipants) {
                    for (Object rawParticipant : rawParticipants) {
                        Map<String,Object> participant = object(rawParticipant, "candidate relationship participant", errors);
                        if (participant == null) continue;
                        String ref = String.valueOf(participant.get("candidateIdentityRef"));
                        Object uaoId = candidateToUao.get(ref);
                        Map<String,Object> record = new LinkedHashMap<>();
                        record.put("role", participant.get("role"));
                        record.put("candidateIdentityRef", ref);
                        record.put("binding", uaoId == null ? "UNRESOLVED" : "RESOLVED");
                        if (uaoId != null) { record.put("uaoId", uaoId); bound++; }
                        participants.add(record);
                    }
                }
                item.put("participants", participants);
                item.put("identityBindingStatus", bound == 0 ? "UNBOUND"
                        : bound == participants.size() ? "ALL_PARTICIPANTS_BOUND" : "PARTIALLY_BOUND");
                item.put("identityLiterals", rel.get("identityLiterals"));
                item.put("contextualBindings", rel.get("contextualBindings"));
                item.put("sourceRefs", rel.get("sourceRefs"));
                expectedUnresolved.add(item);
            }
            if (!canonicalEquals(expectedUnresolved, unresolved)) errors.add("Unresolved relationship projection does not reconstruct exactly from candidate relationships while ASA type-role authority is unavailable.");
            if (canonicalRelationships instanceof List<?> uros && !uros.isEmpty()) errors.add("Canonical UROs are present while Relationship Type role authority remains unavailable.");
        }

        if (publication != null && scope != null && coverage != null && verification != null && quarantine instanceof List<?> q && unresolvedItems instanceof List<?> unresolved) {
            String expectedStatus; boolean expectedEligible;
            if ("REQUIRES_SELECTION".equals(scope.get("scopeStatus"))) { expectedStatus="INTERPRETATION_UNRESOLVED"; expectedEligible=false; }
            else if (!Boolean.TRUE.equals(verification.get("passed"))) { expectedStatus="FAILED"; expectedEligible=false; }
            else if (!q.isEmpty()) { expectedStatus="QUARANTINED"; expectedEligible=false; }
            else if (!unresolved.isEmpty()) { expectedStatus="EVIDENCE_INCOMPLETE"; expectedEligible=false; }
            else if (!Boolean.TRUE.equals(coverage.get("complete"))) { expectedStatus="EVIDENCE_INCOMPLETE"; expectedEligible=false; }
            else { expectedStatus="EXPERIMENTAL"; expectedEligible=true; }
            if (!expectedStatus.equals(publication.get("status")) || !java.util.Objects.equals(expectedEligible, publication.get("eligible"))) {
                errors.add("Publication decision cannot be reconstructed from scope/quarantine/URO/coverage/verification state; expected " + expectedStatus + "/" + expectedEligible + ".");
            }
        }
    }

    /**
     * Checks what a package can prove about its own identity decisions.
     *
     * <p>Verifiable here: exactly one decision per resolved identity, each naming that identity's
     * reconstructed resolution key and candidate/source refs, and a {@code SAME} decision binding
     * the uid that the key actually derives. That last check matters — it means a package cannot
     * claim to have reused some other registered identity while carrying this one.
     *
     * <p>Not verifiable here, and deliberately not claimed: whether the registry genuinely held a
     * match at manufacture time. That requires the registry, and an auditor holding it can
     * re-derive the decision from the recorded key and external identifiers.
     */
    private void verifyIdentityDecisions(Object recordedDecisions, Map<String,Object> expectedResolution, List<String> errors) {
        if (!(recordedDecisions instanceof List<?> decisions)) {
            errors.add("identity-resolution.json does not carry an identityDecisions array.");
            return;
        }
        Map<String,Map<String,Object>> expectedByUid = new LinkedHashMap<>();
        for (Object raw : Json.array(expectedResolution.get("resolvedIdentities"), "reconstructed resolved identities")) {
            Map<String,Object> identity = object(raw, "reconstructed resolved identity", errors);
            if (identity != null) expectedByUid.put(String.valueOf(identity.get("uaoId")), identity);
        }

        Set<String> seen = new LinkedHashSet<>();
        for (Object raw : decisions) {
            Map<String,Object> decision = object(raw, "identity decision", errors);
            if (decision == null) continue;
            ValidationResult schema = validator.validate(decision, schemaDir.resolve("identity-decision.schema.json"));
            errors.addAll(prefix(schema.errors(), "Identity decision: "));

            String uaoId = String.valueOf(decision.get("uaoId"));
            if (!seen.add(uaoId)) errors.add("Duplicate identity decision for UAO " + uaoId + ".");
            Map<String,Object> identity = expectedByUid.get(uaoId);
            if (identity == null) {
                errors.add("Identity decision references an unreconstructed UAO: " + uaoId + ".");
                continue;
            }
            Object expectedKey = identity.get("resolutionKey");
            if (!canonicalEquals(expectedKey, decision.get("resolutionKey"))) {
                errors.add("Identity decision resolutionKey does not match the reconstructed identity for " + uaoId + ".");
            }
            if (!canonicalEquals(IdentityReference.resolutionKey(String.valueOf(expectedKey)).toMap(), decision.get("reference"))) {
                errors.add("Identity decision reference does not match the reconstructed identity for " + uaoId + ".");
            }
            if (!canonicalEquals(identity.get("candidateRefs"), decision.get("candidateRefs"))) {
                errors.add("Identity decision candidateRefs do not reconstruct for " + uaoId + ".");
            }
            if (!canonicalEquals(identity.get("sourceRefs"), decision.get("sourceRefs"))) {
                errors.add("Identity decision sourceRefs do not reconstruct for " + uaoId + ".");
            }
            if ("SAME".equals(decision.get("decision")) && !uaoId.equals(decision.get("uid"))) {
                errors.add("Identity decision claims reuse of a different registered identity than the one manufactured: " + uaoId + ".");
            }
        }
        for (String uaoId : expectedByUid.keySet()) {
            if (!seen.contains(uaoId)) errors.add("No identity decision was recorded for UAO " + uaoId + ".");
        }
    }

    /** Mirrors the manufacture-side alias provenance assembly from independently grouped candidates. */
    private static List<Object> reconstructAliasProvenance(Map<String,Set<String>> nameCandidates, Map<String,Set<String>> nameSources) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String,Set<String>> entry : nameCandidates.entrySet()) {
            Map<String,Object> record = new LinkedHashMap<>();
            record.put("alias", entry.getKey());
            record.put("candidateRefs", new ArrayList<>(entry.getValue()));
            record.put("sourceRefs", new ArrayList<>(nameSources.getOrDefault(entry.getKey(), Set.of())));
            out.add(record);
        }
        return out;
    }

    private Map<String,Object> reconstructIdentityResolution(List<?> identities, List<String> errors) {
        Map<String,List<Map<String,Object>>> groups = new TreeMap<>();
        for (Object raw : identities) {
            Map<String,Object> candidate = object(raw, "candidate identity", errors); if (candidate == null) continue;
            Object keyRaw = candidate.get("resolutionKey");
            if (!(keyRaw instanceof String key)) continue;
            try { ResolutionKeys.requireCanonical(key); }
            catch (IllegalArgumentException ex) { errors.add("Candidate identity resolutionKey is not canonical during reconstruction: " + ex.getMessage()); continue; }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }
        if (groups.isEmpty()) { errors.add("No candidate identities are available for identity-resolution reconstruction."); return null; }

        Map<String,Object> candidateToUao = new TreeMap<>();
        List<Object> resolved = new ArrayList<>();
        Set<String> issued = new LinkedHashSet<>();
        int roots = 0;
        String rootUao = null;
        for (Map.Entry<String,List<Map<String,Object>>> entry : groups.entrySet()) {
            List<Map<String,Object>> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(v -> String.valueOf(v.get("candidateId"))));
            String uaoId = StableIdentifiers.forText("uao", 12, entry.getKey());
            if (!issued.add(uaoId)) errors.add("Stable UAO identifier collision during identity-resolution reconstruction: " + uaoId);
            boolean root = group.stream().anyMatch(v -> Boolean.TRUE.equals(v.get("root")));
            if (root) { roots++; rootUao = uaoId; }

            Set<String> aliases = new LinkedHashSet<>();
            Set<String> sources = new LinkedHashSet<>();
            List<Object> candidateRefs = new ArrayList<>();
            Map<String,String> externalIdentifiers = new TreeMap<>();
            Map<String,Set<String>> nameCandidates = new TreeMap<>();
            Map<String,Set<String>> nameSources = new TreeMap<>();
            for (Map<String,Object> candidate : group) {
                Object candidateId = candidate.get("candidateId");
                candidateRefs.add(candidateId);
                candidateToUao.put(String.valueOf(candidateId), uaoId);
                List<String> candidateSources = new ArrayList<>();
                if (candidate.get("sourceRefs") instanceof List<?> values) for (Object value : values) candidateSources.add(String.valueOf(value));
                sources.addAll(candidateSources);
                List<String> names = new ArrayList<>();
                if (candidate.get("label") instanceof String label) names.add(label);
                if (candidate.get("aliases") instanceof List<?> values) for (Object value : values) names.add(String.valueOf(value));
                for (String name : names) {
                    aliases.add(name);
                    nameCandidates.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(String.valueOf(candidateId));
                    nameSources.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).addAll(candidateSources);
                }
                try {
                    Map<String,String> declared = ExternalIdentifiers.requireCanonical(
                            candidate.get("externalIdentifiers"), "Candidate " + candidateId + " externalIdentifiers");
                    for (Map.Entry<String,String> external : declared.entrySet()) {
                        String previous = externalIdentifiers.putIfAbsent(external.getKey(), external.getValue());
                        if (previous != null && !previous.equals(external.getValue())) {
                            errors.add("Candidates sharing resolutionKey " + entry.getKey()
                                    + " declare conflicting " + external.getKey() + " identifiers during reconstruction.");
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    errors.add("Candidate external identifier is not canonical during reconstruction: " + ex.getMessage());
                }
            }
            try { ExternalIdentifiers.requireConsistentWithResolutionKey(entry.getKey(), externalIdentifiers); }
            catch (IllegalArgumentException ex) { errors.add(ex.getMessage()); }
            String label = String.valueOf(group.getFirst().get("label"));
            aliases.remove(label);
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("uaoId", uaoId);
            item.put("candidateRefs", candidateRefs);
            item.put("label", label);
            item.put("resolutionKey", entry.getKey());
            item.put("semanticType", ResolutionKeys.semanticType(entry.getKey()));
            item.put("root", root);
            item.put("aliases", new ArrayList<>(aliases));
            item.put("aliasProvenance", reconstructAliasProvenance(nameCandidates, nameSources));
            item.put("externalIdentifiers", ExternalIdentifiers.toCanonicalMap(externalIdentifiers));
            item.put("sourceRefs", new ArrayList<>(sources));
            resolved.add(item);
        }
        if (roots != 1) errors.add("Identity-resolution reconstruction requires exactly one resolved root; found " + roots + ".");
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("rootUaoId", rootUao);
        out.put("candidateToUao", candidateToUao);
        out.put("resolvedIdentities", resolved);
        return out;
    }

    private void verifyChecksums(Path packageDir, Path checksumFile, List<String> errors) {
        Set<String> listed = new LinkedHashSet<>();
        for (String line : FileOps.readText(checksumFile).split("\\R")) {
            if (line.isBlank()) continue;
            if (line.length() < 67 || !line.substring(0, 64).matches("[a-f0-9]{64}") || !line.substring(64).startsWith("  ")) {
                errors.add("Malformed checksum line: " + line); continue;
            }
            String expected = line.substring(0,64);
            String relative = line.substring(66);
            if (!listed.add(relative)) { errors.add("Duplicate checksum path: " + relative); continue; }
            Path file = packageDir.resolve(relative).normalize();
            if (!file.startsWith(packageDir)) { errors.add("Checksum path escapes package: " + relative); continue; }
            if (!Files.isRegularFile(file)) { errors.add("Checksummed file missing: " + relative); continue; }
            try {
                String actual = Hashes.sha256(Files.readAllBytes(file));
                if (!actual.equals(expected)) errors.add("Checksum mismatch: " + relative);
            } catch (Exception ex) { errors.add("Unable to checksum " + relative + ": " + ex.getMessage()); }
        }
        Set<String> actualFiles = new LinkedHashSet<>(inventory(packageDir));
        actualFiles.remove("checksums.sha256");
        if (!actualFiles.equals(listed)) {
            Set<String> missing = new LinkedHashSet<>(actualFiles); missing.removeAll(listed);
            Set<String> extra = new LinkedHashSet<>(listed); extra.removeAll(actualFiles);
            if (!missing.isEmpty()) errors.add("Files missing from checksum inventory: " + missing);
            if (!extra.isEmpty()) errors.add("Checksum entries without package files: " + extra);
        }
    }

    private void verifyManifestInventory(Path packageDir, Map<String, Object> manifest, List<String> errors) {
        Object raw = manifest.get("files");
        if (!(raw instanceof List<?> list)) { errors.add("Manifest files is not an array."); return; }
        Set<String> expected = new LinkedHashSet<>();
        for (Object item : list) if (item instanceof String s) expected.add(s); else errors.add("Manifest file entry is not a string.");
        Set<String> actual = new LinkedHashSet<>(inventory(packageDir));
        actual.remove("checksums.sha256");
        if (!expected.equals(actual)) {
            Set<String> missing = new LinkedHashSet<>(actual); missing.removeAll(expected);
            Set<String> extra = new LinkedHashSet<>(expected); extra.removeAll(actual);
            if (!missing.isEmpty()) errors.add("Manifest omits package files: " + missing);
            if (!extra.isEmpty()) errors.add("Manifest names missing files: " + extra);
        }
    }

    private void verifySourceSnapshots(Path packageDir, List<String> errors) {
        Path registryPath = packageDir.resolve("source-registry.json");
        if (!Files.isRegularFile(registryPath)) { errors.add("source-registry.json is missing"); return; }
        Object parsed = FileOps.readJson(registryPath);
        Map<String,Object> registry;
        try { registry = Json.object(parsed, "source registry"); }
        catch (IllegalArgumentException ex) { errors.add(ex.getMessage()); return; }
        Object raw = registry.get("sources");
        if (!(raw instanceof List<?> sources)) { errors.add("source registry sources is not an array"); return; }
        for (Object item : sources) {
            if (!(item instanceof Map<?,?>)) { errors.add("source registry entry is not an object"); continue; }
            @SuppressWarnings("unchecked") Map<String,Object> source = (Map<String,Object>) item;
            errors.addAll(prefix(validator.validate(source, schemaDir.resolve("source-record.schema.json")).errors(), "source: "));
            Object pathValue = source.get("snapshotPath"), hashValue = source.get("sha256");
            if (!(pathValue instanceof String relative) || !(hashValue instanceof String expected)) continue;
            Path snapshot = packageDir.resolve(relative).normalize();
            if (!snapshot.startsWith(packageDir)) { errors.add("Source snapshot escapes package: " + relative); continue; }
            if (!Files.isRegularFile(snapshot)) { errors.add("Source snapshot missing: " + relative); continue; }
            try {
                String actual = Hashes.sha256(Files.readAllBytes(snapshot));
                if (!actual.equals(expected)) errors.add("Source snapshot content hash mismatch: " + relative);
            } catch (Exception ex) { errors.add("Unable to hash source snapshot " + relative + ": " + ex.getMessage()); }
        }
    }

    private Object readValue(Path path, String label, List<String> errors) {
        if (!Files.isRegularFile(path)) { errors.add(label + " file is missing: " + path.getFileName()); return null; }
        try { return FileOps.readJson(path); }
        catch (IllegalArgumentException ex) { errors.add(label + ": " + ex.getMessage()); return null; }
    }

    private Map<String,Object> readObject(Path path, String label, List<String> errors) {
        Object value = readValue(path, label, errors);
        if (value == null) return null;
        return object(value, label, errors);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> object(Object value, String label, List<String> errors) {
        if (!(value instanceof Map<?,?> m)) { errors.add(label + " is not an object"); return null; }
        return (Map<String,Object>) m;
    }

    private List<String> inventory(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).map(root::relativize).map(Path::toString)
                    .map(v -> v.replace('\\','/')).sorted().toList();
        } catch (Exception ex) { throw new IllegalArgumentException("Unable to inventory package: " + ex.getMessage(), ex); }
    }

    private void collectForbidden(Object value, String path, List<String> errors) {
        SignificanceBoundary.collect(value, path, errors);
    }

    private static List<String> prefix(List<String> errors, String prefix) { return errors.stream().map(e -> prefix + e).toList(); }

    public record Result(boolean passed, List<String> errors, List<String> checks) {
        public Map<String,Object> toMap() {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("passed", passed); out.put("errors", new ArrayList<>(errors)); out.put("checks", new ArrayList<>(checks));
            return out;
        }
    }
}
