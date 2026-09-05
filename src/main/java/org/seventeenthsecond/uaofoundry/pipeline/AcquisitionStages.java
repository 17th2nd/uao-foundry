package org.seventeenthsecond.uaofoundry.pipeline;

import org.seventeenthsecond.uaofoundry.identifiers.StableIdentifiers;
import org.seventeenthsecond.uaofoundry.identity.ExternalIdentifiers;
import org.seventeenthsecond.uaofoundry.identity.IdentityDecision;
import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolution;
import org.seventeenthsecond.uaofoundry.identity.IdentityResolver;
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
            Map<String, String> externalIdentifiers = new TreeMap<>();
            Map<String, Set<String>> nameCandidates = new TreeMap<>();
            Map<String, Set<String>> nameSources = new TreeMap<>();
            for (Map<String, Object> candidate : group) {
                String cid = string(candidate.get("candidateId"), "candidateId");
                candidateToUao.put(cid, uaoId);
                refs.add(cid);
                List<String> candidateSources = new ArrayList<>();
                for (Object source : list(candidate.get("sourceRefs"), "sourceRefs")) candidateSources.add(string(source, "sourceRef"));
                sources.addAll(candidateSources);
                // Every name is recorded with the candidate that used it and the sources behind
                // that candidate. A name without provenance cannot later be weighed against a
                // competing name, which is what alias-driven ambiguity resolution will need.
                List<String> names = new ArrayList<>();
                names.add(string(candidate.get("label"), "label"));
                for (Object alias : listOrEmpty(candidate.get("aliases"))) names.add(string(alias, "alias"));
                for (String name : names) {
                    aliases.add(name);
                    nameCandidates.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(cid);
                    nameSources.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).addAll(candidateSources);
                }
                // Candidates grouped under one resolution key must agree on durable external
                // identity. Disagreement means the provider named two different objects with one
                // address, which cannot be repaired here without inventing a winner.
                Map<String, String> declared = ExternalIdentifiers.requireCanonical(
                        candidate.get("externalIdentifiers"), "Candidate " + cid + " externalIdentifiers");
                for (Map.Entry<String, String> external : declared.entrySet()) {
                    String previous = externalIdentifiers.putIfAbsent(external.getKey(), external.getValue());
                    if (previous != null && !previous.equals(external.getValue())) {
                        throw new IllegalArgumentException("EXTERNAL_IDENTIFIER_CONTRADICTION: candidates sharing resolutionKey "
                                + entry.getKey() + " declare conflicting " + external.getKey() + " identifiers.");
                    }
                }
            }
            ExternalIdentifiers.requireConsistentWithResolutionKey(entry.getKey(), externalIdentifiers);
            String label = string(group.getFirst().get("label"), "label");
            aliases.remove(label);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uaoId", uaoId);
            item.put("candidateRefs", refs);
            item.put("label", label);
            item.put("resolutionKey", entry.getKey());
            item.put("semanticType", ResolutionKeys.semanticType(entry.getKey()));
            item.put("root", root);
            item.put("aliases", new ArrayList<>(aliases));
            item.put("aliasProvenance", aliasProvenance(nameCandidates, nameSources));
            item.put("externalIdentifiers", ExternalIdentifiers.toCanonicalMap(externalIdentifiers));
            item.put("sourceRefs", new ArrayList<>(sources));
            resolved.add(item);
        }
        if (roots != 1) throw new IllegalArgumentException("Exactly one resolved root identity is required; found " + roots);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rootUaoId", rootUao);
        out.put("candidateToUao", candidateToUao);
        out.put("resolvedIdentities", resolved);
        out.put("identityDecisions", identityDecisions(resolved));
        return out;
    }

    /**
     * Turns the observed names into provenance-bearing alias records.
     *
     * <p>Covers every name the identity was seen under, the canonical label included: §9 of the
     * programme treats a human label as one alias kind among many, and a label carries no more
     * inherent authority than any other name. Sorted by name for determinism.
     */
    private static List<Object> aliasProvenance(Map<String, Set<String>> nameCandidates, Map<String, Set<String>> nameSources) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : nameCandidates.entrySet()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("alias", entry.getKey());
            record.put("candidateRefs", new ArrayList<>(entry.getValue()));
            record.put("sourceRefs", new ArrayList<>(nameSources.getOrDefault(entry.getKey(), Set.of())));
            out.add(record);
        }
        return out;
    }

    /**
     * Records, for every resolved identity, why the Foundry believes it does or does not denote an
     * already-registered object.
     *
     * <p>Before this existed the resolution key was an <em>assertion</em> of identity that the
     * pipeline treated as <em>evidence</em> of identity, and no artefact could answer the question
     * afterwards. Each decision now carries its reference, verdict, reason codes, the registered
     * identities the evidence pointed at, and the candidate and source refs that supported it.
     *
     * <p>History is preserved by accretion rather than by mutation: decisions live inside an
     * immutable package, so a later manufacture adds a new package carrying its own decisions and
     * can never rewrite an earlier determination.
     *
     * <p>When no registry is supplied the Foundry does not pretend to have looked. Every decision
     * is {@code UNRESOLVED / REGISTRY_NOT_CONSULTED}, which is deliberately distinguishable from
     * having looked and found nothing.
     */
    private List<Object> identityDecisions(List<Object> resolvedIdentities) {
        IdentityResolver resolver = registryIndex == null ? null : new IdentityResolver(registryIndex);
        List<Object> decisions = new ArrayList<>();
        for (Object raw : resolvedIdentities) {
            Map<String, Object> identity = map(raw);
            String uaoId = string(identity.get("uaoId"), "uaoId");
            String resolutionKey = string(identity.get("resolutionKey"), "resolutionKey");
            Map<String, String> externalIdentifiers = new TreeMap<>();
            map(identity.get("externalIdentifiers")).forEach((k, v) -> externalIdentifiers.put(k, String.valueOf(v)));

            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("uaoId", uaoId);
            decision.put("reference", IdentityReference.resolutionKey(resolutionKey).toMap());
            if (resolver == null) {
                decision.put("decision", "UNRESOLVED");
                decision.put("reasonCodes", List.of("REGISTRY_NOT_CONSULTED"));
                decision.put("candidateUids", List.of());
            } else {
                IdentityResolution resolution = resolver.resolveCandidate(resolutionKey, externalIdentifiers);
                if (resolution.decision() == IdentityDecision.DIFFERENT) {
                    // Positive evidence of difference under one address. There is no winner to pick,
                    // and repairing it here would fabricate identity certainty, so manufacture stops.
                    throw new IllegalArgumentException("EXTERNAL_IDENTIFIER_CONTRADICTION: candidate resolutionKey "
                            + resolutionKey + " contradicts the durable external identity already registered for "
                            + String.join(", ", resolution.candidateUids()) + ".");
                }
                decision.put("decision", resolution.decision().name());
                decision.put("reasonCodes", new ArrayList<>(resolution.reasonCodes()));
                if (resolution.uid() != null) decision.put("uid", resolution.uid());
                decision.put("candidateUids", new ArrayList<>(resolution.candidateUids()));
            }
            decision.put("resolutionKey", resolutionKey);
            decision.put("candidateRefs", deepCopyList(list(identity.get("candidateRefs"), "candidateRefs")));
            decision.put("sourceRefs", deepCopyList(list(identity.get("sourceRefs"), "sourceRefs")));
            validate(decision, "identity-decision.schema.json", "Identity decision " + uaoId);
            decisions.add(decision);
        }
        decisions.sort(Comparator.comparing(v -> string(map(v).get("uaoId"), "uaoId")));
        return decisions;
    }

    protected Map<String, Object> relationshipConstruction(Map<String, Object> candidateValidation, Map<String, Object> resolution) {
        Map<String, Object> valid = map(candidateValidation.get("valid"));
        List<Object> candidateRelationships = list(valid.get("relationships"), "relationships");
        Map<String, Object> candidateToUao = map(resolution.get("candidateToUao"));
        List<Object> unresolved = new ArrayList<>();
        if (relationshipEdition != null) {
            // Edition-aware path (Experiment 002). Type resolution, RTR §10.1 instance validation
            // and identity binding all happen in the shared builder so the verifier can re-derive
            // every record from the package's own candidates and embedded edition copy.
            Map<String,String> labelsByUid = new java.util.TreeMap<>();
            for (Object raw : list(resolution.get("resolvedIdentities"), "resolvedIdentities")) {
                Map<String,Object> identity = map(raw);
                labelsByUid.put(string(identity.get("uaoId"), "uaoId"), string(identity.get("label"), "label"));
            }
            List<Object> experimental = new ArrayList<>();
            for (Object raw : candidateRelationships) {
                var outcome = org.seventeenthsecond.uaofoundry.relationship.ExperimentalRelationships.build(
                        map(raw), candidateToUao, labelsByUid, relationshipEdition);
                if (outcome.record() != null) {
                    validate(outcome.record(), "experimental-relationship.schema.json", "Experimental relationship " + outcome.record().get("relationshipId"));
                    experimental.add(outcome.record());
                } else {
                    validate(outcome.unresolved(), "unresolved-relationship.schema.json", "Unresolved relationship " + outcome.unresolved().get("candidateId"));
                    unresolved.add(outcome.unresolved());
                }
            }
            experimental.sort(Comparator.comparing(v -> string(map(v).get("relationshipId"), "relationshipId")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("authorityStatus", "FOUNDRY_EXPERIMENTAL_EDITION_" + relationshipEdition.registryVersion() + "_NOT_ASA_ADMITTED");
            out.put("canonicalUros", List.of());
            out.put("experimentalRelationships", experimental);
            out.put("unresolvedRelationships", unresolved);
            out.put("candidateCount", new BigDecimal(candidateRelationships.size()));
            out.put("edition", relationshipEdition.summary());
            return out;
        }
        for (Object raw : candidateRelationships) {
            Map<String, Object> rel = map(raw);
            Map<String, Object> finding = new LinkedHashMap<>();
            finding.put("candidateId", rel.get("candidateId"));
            finding.put("code", "URO_TYPE_AUTHORITY_UNAVAILABLE");
            finding.put("description", "Current ASA CSS defines URO structure but the Foundry has no current authoritative domain Relationship Type role registry to validate this candidate. Publication of this URO is fail-closed.");
            finding.put("typeVersion", rel.get("typeVersion"));

            // Identity binding and type-role authority are separable problems. Resolving cid-x to
            // uao-y is an identity operation the Foundry can already perform; deciding whether
            // "container" is a legal role of asa.core/contains@1 needs the authority ASA#29 tracks.
            // Binding the first does not smuggle the candidate one step closer to publication --
            // it only stops the retained evidence pointing at bundle-local handles that mean
            // nothing outside this package.
            List<Object> participants = new ArrayList<>();
            int bound = 0;
            for (Object rawParticipant : list(rel.get("participants"), "relationship participants")) {
                Map<String, Object> participant = map(rawParticipant);
                String ref = string(participant.get("candidateIdentityRef"), "candidateIdentityRef");
                Object uaoId = candidateToUao.get(ref);
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("role", participant.get("role"));
                record.put("candidateIdentityRef", ref);
                // Never invent a uid to make a relation look complete.
                record.put("binding", uaoId == null ? "UNRESOLVED" : "RESOLVED");
                if (uaoId != null) { record.put("uaoId", uaoId); bound++; }
                participants.add(record);
            }
            finding.put("participants", participants);
            finding.put("identityBindingStatus", bound == 0 ? "UNBOUND"
                    : bound == participants.size() ? "ALL_PARTICIPANTS_BOUND" : "PARTIALLY_BOUND");
            finding.put("identityLiterals", rel.get("identityLiterals"));
            finding.put("contextualBindings", rel.get("contextualBindings"));
            finding.put("sourceRefs", rel.get("sourceRefs"));
            validate(finding, "unresolved-relationship.schema.json", "Unresolved relationship " + rel.get("candidateId"));
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
