import org.seventeenthsecond.uaofoundry.identity.IdentityReference;
import org.seventeenthsecond.uaofoundry.io.RequestLoader;
import org.seventeenthsecond.uaofoundry.json.Json;
import org.seventeenthsecond.uaofoundry.model.ManufacturingRequest;
import org.seventeenthsecond.uaofoundry.pipeline.FoundryPipeline;
import org.seventeenthsecond.uaofoundry.pipeline.PipelineResult;
import org.seventeenthsecond.uaofoundry.provider.FixtureProvider;
import org.seventeenthsecond.uaofoundry.registry.FoundryRegistry;
import org.seventeenthsecond.uaofoundry.util.FileOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry scale measurement (directive §48).
 *
 * <p>Measures what actually happens as a registry grows, rather than asserting readiness from a
 * three-package example. Reports admission cost, index read cost, search cost and exact-address
 * cost at increasing sizes, so the growth curve is visible instead of assumed.
 *
 * <p>Run: {@code java -cp target/uao-foundry-0.1.0.jar:benchmark/scale RegistryScale <root> <sizes…>}
 */
public final class RegistryScale {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        int[] sizes = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) sizes[i - 1] = Integer.parseInt(args[i]);

        Path schemas = Path.of("schemas");
        Path work = root.resolve("work");
        Path dist = root.resolve("dist");
        Path fixtures = root.resolve("fixtures");
        Files.createDirectories(fixtures);

        System.out.printf("%8s %14s %14s %14s %14s %12s%n",
                "size", "admit ms/pkg", "index ms", "search ms", "address ms", "index KB");

        for (int size : sizes) {
            Path registryRoot = root.resolve("reg-" + size);
            FileOps.deleteTree(registryRoot);
            FoundryRegistry registry = new FoundryRegistry(registryRoot, schemas);
            registry.rebuildAndPersist();

            long admitTotal = 0;
            for (int i = 0; i < size; i++) {
                Path fixture = fixtures.resolve("synthetic-" + i + ".json");
                FileOps.writeJson(fixture, syntheticBundle(i));
                RequestLoader loader = new RequestLoader(schemas.resolve("manufacturing-request.schema.json"));
                ManufacturingRequest request = loader.fromSeed("component " + i, "en", "experimental");
                PipelineResult result = new FoundryPipeline(schemas, work, dist, "scale",
                        registryRoot, registry.index()).manufacture(request, new FixtureProvider(fixture, schemas), false);
                long t0 = System.nanoTime();
                registry.register(result.packagePath());
                admitTotal += System.nanoTime() - t0;
            }

            long t = System.nanoTime();
            Map<String,Object> index = registry.index();
            long indexMs = (System.nanoTime() - t) / 1_000_000;

            t = System.nanoTime();
            registry.search("component 7");
            long searchMs = (System.nanoTime() - t) / 1_000_000;

            String uid = String.valueOf(Json.object(
                    Json.array(index.get("identities"), "identities").getFirst(), "identity").get("uid"));
            t = System.nanoTime();
            registry.identityRecord(IdentityReference.uid(uid));
            long addressMs = (System.nanoTime() - t) / 1_000_000;

            long indexBytes = Files.size(registryRoot.resolve("index.json"));
            System.out.printf("%8d %14.1f %14d %14d %14d %12d%n",
                    size, admitTotal / 1e6 / size, indexMs, searchMs, addressMs, indexBytes / 1024);
            System.out.flush();
        }
    }

    /** One distinct identity per bundle; nothing is shared, so the registry genuinely grows. */
    private static Map<String,Object> syntheticBundle(int n) {
        String clock = "2026-08-21T00:00:00Z";
        String sourceId = "src-synthetic-" + n;
        String label = "component " + n;
        Map<String,Object> bundle = new LinkedHashMap<>();
        bundle.put("fixtureVersion", "0.1.0");
        bundle.put("identitySeed", label);
        bundle.put("fixedClock", clock);
        bundle.put("knowledgeHorizon", clock);
        bundle.put("interpretations", List.of(Map.of(
                "candidateId", "int-x", "label", label,
                "definition", "A synthetic identity used to measure registry scale.",
                "semanticTypeProposal", "SyntheticClass", "confidence", new java.math.BigDecimal("1.0"),
                "status", "SELECTED", "references", List.of("fixture://" + sourceId))));
        bundle.put("scopeResolution", new LinkedHashMap<>(Map.of(
                "selectedInterpretation", "int-x", "scopeStatus", "FIXTURE_SELECTED",
                "canonicalWorkingLabel", label,
                "includedBoundaries", List.of("synthetic"), "excludedBoundaries", List.of("real world"),
                "excludedInterpretations", List.of(), "unresolvedQuestions", List.of())));
        bundle.put("manufacturingPlan", new LinkedHashMap<>(Map.of(
                "planVersion", "0.1.0", "selectedIdentity", label,
                "dimensions", List.of("synthetic"), "neighbouringIdentities", List.of(),
                "anticipatedSourceClasses", List.of("synthetic-reference"), "risks", List.of(),
                "completionQuestions", List.of(Map.of("questionId", "q-x", "prompt", "Covered?", "required", true)),
                "exclusions", List.of())));
        bundle.put("sourceStrategy", new LinkedHashMap<>(Map.of(
                "strategyVersion", "0.1.0",
                "sourceClasses", List.of(Map.of("classId", "synthetic-reference", "purpose", "scale", "priority", new java.math.BigDecimal(1))),
                "authorityNotes", List.of("Synthetic."), "safetyConstraints", List.of("No network."))));
        bundle.put("sources", List.of(new LinkedHashMap<>(Map.of(
                "sourceId", sourceId, "locator", "fixture://" + sourceId,
                "sourceClass", "synthetic-reference", "retrievedAt", clock,
                "license", "SCALE-FIXTURE", "content", "Synthetic evidence for " + label + "."))));
        bundle.put("candidates", new LinkedHashMap<>(Map.of(
                "identities", List.of(new LinkedHashMap<>(Map.of(
                        "candidateId", "cid-x", "label", label,
                        "resolutionKey", "foundry:v0.1:synthetic:component-" + n,
                        "root", true, "aliases", List.of("part-" + n),
                        "sourceRefs", List.of(sourceId),
                        "externalIdentifiers", Map.of("scale", "S" + n)))),
                "claims", List.of(new LinkedHashMap<>(Map.of(
                        "candidateId", "clm-x", "subjectIdentityRef", "cid-x",
                        "statement", "Synthetic assertion for " + label + ".",
                        "channels", List.of("foundry"), "sourceRefs", List.of(sourceId)))),
                "relationships", List.of(),
                "evidence", List.of(new LinkedHashMap<>(Map.of(
                        "evidenceId", "ev-x", "sourceRef", sourceId, "supportsCandidateRef", "clm-x",
                        "extract", "Synthetic.", "locatorWithinSource", "s1"))),
                "states", List.of(), "events", List.of(), "languageMappings", List.of())));
        bundle.put("coverageAnswers", Map.of("q-x", "covered"));
        return bundle;
    }
}
