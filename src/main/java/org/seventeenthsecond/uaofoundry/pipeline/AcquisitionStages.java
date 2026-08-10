package org.seventeenthsecond.uaofoundry.pipeline;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.identifiers.ResolutionKeys;
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

/** Interpretation, planning, evidence, validation, resolution and relationship-gate stages. */
class AcquisitionStages extends PipelineBase {
    protected AcquisitionStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit) { super(schemaDir, workDir, distDir, repositoryCommit); }
    protected AcquisitionStages(Path schemaDir, Path workDir, Path distDir, String repositoryCommit, Path registryRoot, Map<String,Object> registryIndex) { super(schemaDir, workDir, distDir, repositoryCommit, registryRoot, registryIndex); }

    protected Map<String, Object> jobInitialisation(ManufacturingRequest request, FoundryProvider provider) {
        Map<String, Object> stages = new LinkedHashMap<>();
        for (String stage : STAGES) stages.put(stage, "PENDING");
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("network", "disabled-in-fixture-mode");
        limits.put("provider", "fixture-only-v0.1");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("requestId", request.requestId());
        out.put("foundryVersion", VERSION);
        out.put("repositoryCommit", repositoryCommit);
        out.put("provider", provider.name());
        out.put("providerHash", provider.hash());
        out.put("startedAt", provider.fixedClock());
        out.put("stageStatus", stages);
        out.put("configurationHash", FileOps.treeHash(schemaDir));
        out.put("resourceLimits", limits);
        validate(out, "manufacturing-job.schema.json", "Manufacturing job");
        return out;
    }

    protected Map<String, Object> seedNormalisation(ManufacturingRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("originalExpression", request.identitySeed());
        out.put("normalisedExpression", normalise(request.identitySeed()));
        out.put("inputLanguage", request.inputLanguage());
        if (request.scopeHint() != null) out.put("scopeHint", request.scopeHint());
        validate(out, "identity-seed.schema.json", "Identity seed");
        return out;
    }

    protected Map<String, Object> interpretations(Map<String, Object> seed, FoundryProvider provider) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seed", seed.get("normalisedExpression"));
        out.put("provider", provider.name());
        out.put("interpretations", deepCopyList(provider.interpretations()));
        validate(out, "interpretation-candidates.schema.json", "Interpretation candidates");
        ensureUniqueIds(list(out.get("interpretations"), "interpretations"), "candidateId", "interpretation");
        return out;
    }

    protected Map<String, Object> scopeResolution(Map<String, Object> interpretations, FoundryProvider provider) {
        Map<String, Object> out = deepCopyMap(provider.scopeResolution());
        validate(out, "scope-resolution.schema.json", "Scope resolution");
        String selected = string(out.get("selectedInterpretation"), "selectedInterpretation");
        boolean found = list(interpretations.get("interpretations"), "interpretations").stream()
                .map(v -> map(v)).anyMatch(v -> selected.equals(v.get("candidateId")));
        if (!found) throw new IllegalArgumentException("Scope selects unknown interpretation: " + selected);
        return out;
    }

    protected Map<String, Object> manufacturingPlan(Map<String, Object> scope, FoundryProvider provider) {
        Map<String, Object> out = deepCopyMap(provider.manufacturingPlan());
        validate(out, "manufacturing-plan.schema.json", "Manufacturing plan");
        if (!string(scope.get("canonicalWorkingLabel"), "canonicalWorkingLabel").equals(out.get("selectedIdentity"))) {
            throw new IllegalArgumentException("Manufacturing plan selectedIdentity must equal the explicit scope working label.");
        }
        return out;
    }

    protected Map<String, Object> sourceStrategy(FoundryProvider provider) {
        Map<String, Object> out = deepCopyMap(provider.sourceStrategy());
        validate(out, "source-strategy.schema.json", "Source strategy");
        return out;
    }

    protected Map<String, Object> sourceAcquisition(FoundryProvider provider) {
        Path stageFile = jobDir.resolve("07-source-registry.json");
        Path corpus = jobDir.resolve("source-corpus");
        FileOps.deleteTree(corpus);
        List<Object> records = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Object raw : provider.sources()) {
            Map<String, Object> source = map(raw);
            String sourceId = string(source.get("sourceId"), "sourceId");
            if (!ids.add(sourceId)) throw new IllegalArgumentException("Duplicate sourceId: " + sourceId);
            String locator = string(source.get("locator"), "source locator");
            String content;
            Object sourceClass = source.get("sourceClass");
            Object license = source.get("license");
            if (locator.startsWith("registry://")) {
                byte[] exact = verifiedRegistryBytes(locator);
                try { content = new String(exact, StandardCharsets.UTF_8); }
                catch (Exception ex) { throw new IllegalArgumentException("Registry evidence is not UTF-8 text: " + locator, ex); }
                sourceClass = "foundry-registry";
                license = "UAO-FOUNDRY-REGISTRY-SNAPSHOT";
            } else {
                content = string(source.get("content"), "source content");
            }
            String fileName = sourceId + ".txt";
            FileOps.writeText(corpus.resolve(fileName), content);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("sourceId", sourceId);
            record.put("locator", locator);
            record.put("sourceClass", sourceClass);
            record.put("retrievedAt", source.get("retrievedAt"));
            record.put("license", license);
            record.put("sha256", Hashes.sha256(content.getBytes(StandardCharsets.UTF_8)));
            record.put("snapshotPath", "source-corpus/" + fileName);
            validate(record, "source-record.schema.json", "Source record " + sourceId);
            records.add(record);
        }
        records.sort(Comparator.comparing(v -> string(map(v).get("sourceId"), "sourceId")));
        Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("sources", records);
        FileOps.writeJson(stageFile, registry);
        markStage("07_SOURCE_ACQUISITION", stageFile);
        return registry;
    }

    protected Map<String, Object> knowledgeExtraction(FoundryProvider provider) {
        Map<String, Object> in = provider.candidates();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("identities", "claims", "relationships", "evidence", "states", "events", "languageMappings")) {
            Object value = in.get(key);
            out.put(key, value == null ? List.of() : deepCopy(value));
        }
        return out;
    }

    protected Map<String, Object> candidateValidation(Map<String, Object> candidates) {
        Map<String, String> schemas = Map.of(
                "identities", "candidate-identity.schema.json",
                "claims", "candidate-claim.schema.json",
                "relationships", "candidate-relationship.schema.json",
                "evidence", "candidate-evidence.schema.json"
        );
        Map<String, Object> valid = new LinkedHashMap<>();
        List<Object> quarantined = new ArrayList<>();
        for (String category : List.of("identities", "claims", "relationships", "evidence")) {
            List<Object> accepted = new ArrayList<>();
            int index = 0;
            for (Object record : list(candidates.get(category), category)) {
                ValidationResult result = validator.validate(record, schemaDir.resolve(schemas.get(category)));
                if (result.valid()) accepted.add(deepCopy(record));
                else {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("category", category);
                    q.put("index", new BigDecimal(index));
                    q.put("errors", new ArrayList<>(result.errors()));
                    q.put("record", deepCopy(record));
                    quarantined.add(q);
                }
                index++;
            }
            valid.put(category, accepted);
        }
        for (String category : List.of("states", "events", "languageMappings")) valid.put(category, deepCopy(candidates.get(category)));
        ensureUniqueIds(list(valid.get("identities"), "identities"), "candidateId", "candidate identity");
        ensureUniqueIds(list(valid.get("claims"), "claims"), "candidateId", "candidate claim");
        ensureUniqueIds(list(valid.get("relationships"), "relationships"), "candidateId", "candidate relationship");
        ensureUniqueIds(list(valid.get("evidence"), "evidence"), "evidenceId", "candidate evidence");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", valid);
        out.put("quarantined", quarantined);
        return out;
    }

    protected Map<String, Object> identityResolution(Map<String, Object> candidateValidation) {
        Map<String, Object> valid = map(candidateValidation.get("valid"));
        Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
        for (Object raw : list(valid.get("identities"), "identities")) {
            Map<String, Object> candidate = map(raw);
            String resolutionKey = ResolutionKeys.requireCanonical(string(candidate.get("resolutionKey"), "resolutionKey"));
            groups.computeIfAbsent(resolutionKey, ignored -> new ArrayList<>()).add(candidate);
        }
        if (groups.isEmpty()) throw new IllegalArgumentException("No valid candidate identities remain after validation.");
        List<Object> resolved = new ArrayList<>();
        Map<String, Object> candidateToUao = new TreeMap<>();
        int roots = 0;
        String rootUao = null;
        Set<String> issued = new LinkedHashSet<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            group.sort(Comparator.comparing(v -> string(v.get("candidateId"), "candidateId")));
            String uaoId = StableIdentifiers.forText("uao", 12, entry.getKey());
            if (!issued.add(uaoId)) throw new IllegalArgumentException("Stable UAO identifier collision: " + uaoId);
            boolean root = group.stream().anyMatch(v -> bool(v.get("root")));
            if (root) { roots++; rootUao = uaoId; }
            Set<String> aliases = new LinkedHashSet<>();
            Set<String> sources = new LinkedHashSet<>();
            List<Object> refs = new ArrayList<>();
            for (Map<String, Object> candidate : group) {
                String cid = string(candidate.get("candidateId"), "candidateId");
                candidateToUao.put(cid, uaoId);
                refs.add(cid);
                aliases.add(string(candidate.get("label"), "label"));
                for (Object alias : listOrEmpty(candidate.get("aliases"))) aliases.add(string(alias, "alias"));
                for (Object source : list(candidate.get("sourceRefs"), "sourceRefs")) sources.add(string(source, "sourceRef"));
            }
            String label = string(group.getFirst().get("label"), "label");
            aliases.remove(label);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uaoId", uaoId);
            item.put("candidateRefs", refs);
            item.put("label", label);
            item.put("resolutionKey", entry.getKey());
            item.put("root", root);
            item.put("aliases", new ArrayList<>(aliases));
            item.put("sourceRefs", new ArrayList<>(sources));
            resolved.add(item);
        }
        if (roots != 1) throw new IllegalArgumentException("Exactly one resolved root identity is required; found " + roots);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rootUaoId", rootUao);
        out.put("candidateToUao", candidateToUao);
        out.put("resolvedIdentities", resolved);
        return out;
    }

    protected Map<String, Object> relationshipConstruction(Map<String, Object> candidateValidation, Map<String, Object> resolution) {
        Map<String, Object> valid = map(candidateValidation.get("valid"));
        List<Object> candidateRelationships = list(valid.get("relationships"), "relationships");
        List<Object> unresolved = new ArrayList<>();
        for (Object raw : candidateRelationships) {
            Map<String, Object> rel = map(raw);
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("candidateId", rel.get("candidateId"));
            finding.put("code", "URO_TYPE_AUTHORITY_UNAVAILABLE");
            finding.put("description", "Current ASA CSS defines URO structure but the Foundry has no current authoritative domain Relationship Type role registry to validate this candidate. Publication of this URO is fail-closed.");
            unresolved.add(finding);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authorityStatus", "CURRENT_CSS_STRUCTURE_AVAILABLE_TYPE_ROLE_AUTHORITY_UNAVAILABLE");
        out.put("canonicalUros", List.of());
        out.put("unresolvedRelationships", unresolved);
        out.put("candidateCount", new BigDecimal(candidateRelationships.size()));
        return out;
    }

}
