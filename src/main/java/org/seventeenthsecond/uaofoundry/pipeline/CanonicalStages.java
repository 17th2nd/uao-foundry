package org.seventeenthsecond.uaofoundry.pipeline;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.provider.FoundryProvider;
import org.seventeenthsecond.uaofoundry.util.FileOps;
import org.seventeenthsecond.uaofoundry.util.Hashes;
import org.seventeenthsecond.uaofoundry.validation.SchemaValidator;
import org.seventeenthsecond.uaofoundry.validation.ValidationResult;
import org.seventeenthsecond.uaofoundry.verifier.PackageVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Canonical UAO assembly, completeness, verification and publication-decision stages. */
class CanonicalStages extends AcquisitionStages {
    protected CanonicalStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit) { super(schemaDir, workDir, distDir, repositoryCommit); }
    protected CanonicalStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit, Path registryRoot, Map<String,Object> registryIndex) { super(schemaDir, workDir, distDir, repositoryCommit, registryRoot, registryIndex); }

    protected Map<String, Object> canonicalBuild(
            ManufacturingRequest request, FoundryProvider provider, Map<String, Object> sourceRegistry,
            Map<String, Object> candidateValidation, Map<String, Object> resolution, Map<String, Object> relationships) {
        Map<String, Object> valid = map(candidateValidation.get("valid"));
        Map<String, Object> candidateToUao = map(resolution.get("candidateToUao"));
        Map<String, List<Map<String, Object>>> claimsByUao = new TreeMap<>();
        for (Object raw : list(valid.get("claims"), "claims")) {
            Map<String, Object> claim = map(raw);
            Object mapped = candidateToUao.get(string(claim.get("subjectIdentityRef"), "subjectIdentityRef"));
            if (mapped == null) throw new IllegalArgumentException("Candidate claim references unresolved identity: " + claim.get("subjectIdentityRef"));
            claimsByUao.computeIfAbsent(string(mapped, "mapped uao"), ignored -> new ArrayList<>()).add(claim);
        }
        Map<String, List<Map<String, Object>>> evidenceByCandidate = new TreeMap<>();
        for (Object raw : list(valid.get("evidence"), "evidence")) {
            Map<String, Object> evidence = map(raw);
            evidenceByCandidate.computeIfAbsent(string(evidence.get("supportsCandidateRef"), "supportsCandidateRef"), ignored -> new ArrayList<>()).add(evidence);
        }

        List<Object> uaos = new ArrayList<>();
        List<Object> ledger = new ArrayList<>();
        Set<String> sourceIds = sourceIds(sourceRegistry);
        for (Object raw : list(resolution.get("resolvedIdentities"), "resolvedIdentities")) {
            Map<String, Object> identity = map(raw);
            String uid = string(identity.get("uaoId"), "uaoId");
            Map<String, Object> foundryIdentity = new LinkedHashMap<>();
            foundryIdentity.put("canonical_label", identity.get("label"));
            foundryIdentity.put("aliases", identity.get("aliases"));
            foundryIdentity.put("resolution_key", identity.get("resolutionKey"));
            foundryIdentity.put("source_refs", identity.get("sourceRefs"));
            Map<String, Object> internal = new LinkedHashMap<>();
            internal.put("foundry_identity", foundryIdentity);

            List<Map<String, Object>> claims = claimsByUao.getOrDefault(uid, List.of());
            claims = new ArrayList<>(claims);
            claims.sort(Comparator.comparing(v -> string(v.get("candidateId"), "candidateId")));
            List<Object> assertions = new ArrayList<>();
            for (Map<String, Object> claim : claims) {
                Map<String, Object> assertion = new LinkedHashMap<>();
                assertion.put("statement", claim.get("statement"));
                assertion.put("epistemic_class", "DEFERRED_ON_RECORD");
                List<Object> channels = listOrEmpty(claim.get("channels"));
                assertion.put("channels", channels.isEmpty() ? List.of("foundry") : new ArrayList<>(channels));
                assertions.add(assertion);

                Map<String, Object> provenanceEntry = new LinkedHashMap<>();
                provenanceEntry.put("candidateId", claim.get("candidateId"));
                provenanceEntry.put("uaoId", uid);
                provenanceEntry.put("statement", claim.get("statement"));
                provenanceEntry.put("sourceRefs", claim.get("sourceRefs"));
                List<Object> evidenceIds = new ArrayList<>();
                for (Map<String, Object> ev : evidenceByCandidate.getOrDefault(string(claim.get("candidateId"), "candidateId"), List.of())) {
                    evidenceIds.add(ev.get("evidenceId"));
                }
                provenanceEntry.put("evidenceRefs", evidenceIds);
                ledger.add(provenanceEntry);
            }

            for (Object src : list(identity.get("sourceRefs"), "identity source refs")) {
                if (!sourceIds.contains(string(src, "source ref"))) throw new IllegalArgumentException("Resolved identity references unknown source: " + src);
            }
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("knowledge_horizon", provider.knowledgeHorizon());
            provenance.put("assertion_time", provider.fixedClock());
            provenance.put("proposer_profile", "uao-foundry/" + request.manufacturingProfile());
            Map<String, Object> uao = new LinkedHashMap<>();
            uao.put("uid", uid);
            uao.put("lifecycle_status", "Registered");
            uao.put("internal_state", internal);
            uao.put("assertions", assertions);
            uao.put("relationship_references", List.of());
            uao.put("provenance", provenance);
            uao.put("disclaimer", "AUTHORITY_SNAPSHOT_ONLY");
            validate(uao, "canonical-uao.schema.json", "Canonical UAO " + uid);
            rejectForbiddenFields(uao, "$uao[" + uid + "]");
            uaos.add(uao);
        }
        uaos.sort(Comparator.comparing(v -> string(map(v).get("uid"), "uid")));
        ledger.sort(Comparator.comparing(v -> string(map(v).get("candidateId"), "candidateId")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rootUaoId", resolution.get("rootUaoId"));
        out.put("uaos", uaos);
        out.put("uros", relationships.get("canonicalUros"));
        out.put("provenanceLedger", ledger);
        out.put("unresolvedItems", relationships.get("unresolvedRelationships"));
        return out;
    }

    protected Map<String, Object> completeness(Map<String, Object> plan, FoundryProvider provider) {
        Map<String, Object> answers = new TreeMap<>(provider.coverageAnswers());
        int required = 0, covered = 0, partial = 0, unresolved = 0;
        Set<String> knownQuestions = new LinkedHashSet<>();
        for (Object raw : list(plan.get("completionQuestions"), "completionQuestions")) {
            Map<String, Object> question = map(raw);
            String id = string(question.get("questionId"), "questionId");
            knownQuestions.add(id);
            boolean req = bool(question.get("required"));
            if (req) required++;
            String answer = answers.containsKey(id) ? string(answers.get(id), "coverage answer") : "unresolved";
            if ("covered".equals(answer)) covered++;
            else if ("partial".equals(answer)) partial++;
            else unresolved++;
            if (!answers.containsKey(id)) answers.put(id, "unresolved");
        }
        for (String key : answers.keySet()) if (!knownQuestions.contains(key)) throw new IllegalArgumentException("Coverage answer references unknown completion question: " + key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requiredQuestions", new BigDecimal(required));
        out.put("covered", new BigDecimal(covered));
        out.put("partial", new BigDecimal(partial));
        out.put("unresolved", new BigDecimal(unresolved));
        out.put("answers", answers);
        out.put("complete", unresolved == 0);
        validate(out, "coverage-report.schema.json", "Coverage report");
        return out;
    }

    protected Map<String, Object> verification(
            Map<String, Object> scope, Map<String, Object> sourceRegistry, Map<String, Object> candidateValidation,
            Map<String, Object> resolution, Map<String, Object> relationships, Map<String, Object> canonical) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        checks.add("JSON_SCHEMA_CONFORMANCE");
        checks.add("UNIQUE_CANONICAL_IDENTIFIERS");
        checks.add("PROVENANCE_SOURCE_TRACEABILITY");
        checks.add("SOURCE_SNAPSHOT_HASHES");
        checks.add("FORBIDDEN_FIELD_REJECTION");
        checks.add("EXPLICIT_SCOPE_RESOLUTION");
        checks.add("URO_FAIL_CLOSED_TYPE_AUTHORITY");

        if ("REQUIRES_SELECTION".equals(scope.get("scopeStatus"))) errors.add("Semantic scope remains unresolved.");
        Set<String> uaoIds = new LinkedHashSet<>();
        for (Object raw : list(canonical.get("uaos"), "uaos")) {
            Map<String, Object> uao = map(raw);
            ValidationResult result = validator.validate(uao, schemaDir.resolve("canonical-uao.schema.json"));
            errors.addAll(prefix(result.errors(), "UAO " + uao.get("uid") + ": "));
            String id = string(uao.get("uid"), "uid");
            if (!uaoIds.add(id)) errors.add("Duplicate canonical UAO id: " + id);
            collectForbiddenFields(uao, "$uao[" + id + "]", errors);
        }
        if (!uaoIds.contains(string(resolution.get("rootUaoId"), "rootUaoId"))) errors.add("Root UAO is absent from canonical identity set.");

        Set<String> sourceIds = new LinkedHashSet<>();
        for (Object raw : list(sourceRegistry.get("sources"), "source registry")) {
            Map<String, Object> source = map(raw);
            ValidationResult result = validator.validate(source, schemaDir.resolve("source-record.schema.json"));
            errors.addAll(prefix(result.errors(), "Source " + source.get("sourceId") + ": "));
            String id = string(source.get("sourceId"), "sourceId");
            if (!sourceIds.add(id)) errors.add("Duplicate source id: " + id);
            Path snapshot = jobDir.resolve(string(source.get("snapshotPath"), "snapshotPath"));
            if (!Files.isRegularFile(snapshot)) errors.add("Missing source snapshot: " + source.get("snapshotPath"));
            else {
                String actual = Hashes.sha256(FileOps.readText(snapshot).getBytes(StandardCharsets.UTF_8));
                if (!actual.equals(source.get("sha256"))) errors.add("Source snapshot hash mismatch: " + id);
            }
        }
        Map<String, Object> valid = map(candidateValidation.get("valid"));
        for (String cat : List.of("identities", "claims", "relationships")) {
            for (Object raw : list(valid.get(cat), cat)) {
                Map<String, Object> record = map(raw);
                for (Object ref : listOrEmpty(record.get("sourceRefs"))) {
                    if (!sourceIds.contains(string(ref, "source ref"))) errors.add("Candidate " + cat + " references unknown source: " + ref);
                }
            }
        }
        for (Object raw : list(valid.get("evidence"), "evidence")) {
            Map<String, Object> ev = map(raw);
            if (!sourceIds.contains(string(ev.get("sourceRef"), "sourceRef"))) errors.add("Evidence references unknown source: " + ev.get("sourceRef"));
        }
        int unresolvedRelationshipCount = list(relationships.get("unresolvedRelationships"), "unresolved relationships").size();
        if (unresolvedRelationshipCount > 0) warnings.add(unresolvedRelationshipCount + " relationship candidate(s) excluded because current Relationship Type role authority is unavailable.");
        int quarantineCount = list(candidateValidation.get("quarantined"), "quarantined").size();
        if (quarantineCount > 0) warnings.add(quarantineCount + " candidate record(s) quarantined.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", errors.isEmpty());
        out.put("errors", new ArrayList<>(errors));
        out.put("warnings", new ArrayList<>(warnings));
        out.put("checks", checks);
        validate(out, "verification-report.schema.json", "Verification report");
        return out;
    }

    protected Map<String, Object> publicationDecision(
            Map<String, Object> scope, Map<String, Object> candidateValidation, Map<String, Object> relationships,
            Map<String, Object> coverage, Map<String, Object> verification) {
        String status;
        boolean eligible;
        List<Object> reasons = new ArrayList<>();
        if ("REQUIRES_SELECTION".equals(scope.get("scopeStatus"))) {
            status = "INTERPRETATION_UNRESOLVED"; eligible = false; reasons.add("Semantic scope requires explicit selection.");
        } else if (!bool(verification.get("passed"))) {
            status = "FAILED"; eligible = false; reasons.add("Universal verification gates did not pass.");
        } else if (!list(candidateValidation.get("quarantined"), "quarantined").isEmpty()) {
            status = "QUARANTINED"; eligible = false; reasons.add("One or more extracted candidate records were quarantined.");
        } else if (!list(relationships.get("unresolvedRelationships"), "unresolved relationships").isEmpty()) {
            status = "EVIDENCE_INCOMPLETE"; eligible = false; reasons.add("Relationship candidates await authoritative Relationship Type role declarations.");
        } else if (!bool(coverage.get("complete"))) {
            status = "EVIDENCE_INCOMPLETE"; eligible = false; reasons.add("Generated completion questions remain unresolved.");
        } else {
            status = "EXPERIMENTAL"; eligible = true; reasons.add("All v0.1 structural gates pass in deterministic fixture mode; release remains experimental.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("eligible", eligible);
        out.put("reasons", reasons);
        validate(out, "publication-decision.schema.json", "Publication decision");
        return out;
    }
}
