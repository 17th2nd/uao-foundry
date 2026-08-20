package org.seventeenthsecond.usifoundry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An operator-facing failure with a classified cause (§25).
 *
 * <p>Collapsing everything into "Manufacture failed" is the difference between a tool an operator
 * can act on and one they can only retry. A provider that timed out, evidence that failed schema
 * validation, an identity whose meaning is in dispute and a registry that refused admission are
 * four different situations with four different responses.
 */
public final class UsiFoundryException extends RuntimeException {

    /** Stable codes; the UI keys guidance off these rather than off message text. */
    public static final String PROVIDER_FAILURE = "PROVIDER_FAILURE";
    public static final String INVALID_EVIDENCE = "INVALID_EVIDENCE";
    public static final String IDENTITY_AMBIGUITY = "IDENTITY_AMBIGUITY";
    public static final String SEMANTIC_VARIANT_CONFLICT = "SEMANTIC_VARIANT_CONFLICT";
    public static final String IDENTITY_LIFECYCLE = "IDENTITY_LIFECYCLE";
    public static final String VERIFICATION_FAILURE = "VERIFICATION_FAILURE";
    public static final String REGISTRY_CONFLICT = "REGISTRY_CONFLICT";
    public static final String RELATIONSHIP_AUTHORITY_UNAVAILABLE = "RELATIONSHIP_AUTHORITY_UNAVAILABLE";
    public static final String SOURCE_ACQUISITION_FAILURE = "SOURCE_ACQUISITION_FAILURE";
    public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    private final String code;
    private final String guidance;

    public UsiFoundryException(String code, String message) { this(code, message, null); }

    public UsiFoundryException(String code, String message, String guidance) {
        super(message);
        this.code = code;
        this.guidance = guidance;
    }

    public String code() { return code; }

    /**
     * Classifies a core failure by the reason codes the audited core already emits.
     *
     * <p>Matching on the core's own stable codes rather than on prose keeps the classification
     * honest: if the core stops emitting a code, this stops claiming it. Anything unrecognised is
     * reported as {@code UNCLASSIFIED} with the original message intact, because a wrong
     * classification is worse than an admitted absence of one.
     */
    public static UsiFoundryException classify(RuntimeException cause) {
        String message = cause.getMessage() == null ? "" : cause.getMessage();
        if (message.contains("MULTIPLE_UNRECONCILED_VARIANTS") || message.contains("SEMANTIC_VARIANT_DIVERGENCE")) {
            return new UsiFoundryException(SEMANTIC_VARIANT_CONFLICT, message,
                    "This identity has more than one irreconcilable account of its meaning. Every occurrence is "
                            + "preserved; automatic reuse is blocked until a governed reconciliation exists. "
                            + "Unrelated identities are unaffected.");
        }
        if (message.contains("EXTERNAL_IDENTIFIER_CONTRADICTION")) {
            return new UsiFoundryException(IDENTITY_AMBIGUITY, message,
                    "The proposed identity contradicts durable external identity already registered under the "
                            + "same address. There is no winner to pick, so manufacture stopped.");
        }
        if (message.contains("EXTERNAL_IDENTIFIER_AMBIGUOUS") || message.contains("EXTERNAL_IDENTIFIER_CROSS_KEY_MATCH")) {
            return new UsiFoundryException(IDENTITY_AMBIGUITY, message,
                    "The evidence points at more than one registered identity, or at one under a different "
                            + "address. Merging is a governed operation and never happens during manufacture.");
        }
        if (message.contains("IDENTITY_LIFECYCLE_NOT_ACTIVE") || message.contains("IDENTITY_RETIRED")
                || message.contains("IDENTITY_SUPERSEDED") || message.contains("IDENTITY_MERGED")) {
            return new UsiFoundryException(IDENTITY_LIFECYCLE, message,
                    "A recorded lifecycle operation has taken this identity out of automatic reuse.");
        }
        if (message.contains("URO_TYPE_AUTHORITY_UNAVAILABLE")) {
            return new UsiFoundryException(RELATIONSHIP_AUTHORITY_UNAVAILABLE, message,
                    "Relationship candidates are retained and bound to persistent identities, but canonical "
                            + "publication is fail-closed pending 17th2nd/ASA#29.");
        }
        if (message.contains("collision")) {
            return new UsiFoundryException(REGISTRY_CONFLICT, message, null);
        }
        if (message.contains("verification failed") || message.contains("failed verification")
                || message.contains("Checksum mismatch") || message.contains("content digest")) {
            return new UsiFoundryException(VERIFICATION_FAILURE, message, null);
        }
        if (message.contains("identitySeed does not match") || message.contains("executionMode")) {
            // An operator picked an evidence bundle that is about something else. Reporting this as
            // a provider failure sends them to check timeouts and command paths, which is the wrong
            // place entirely -- the fix is to choose the right bundle or the right topic.
            return new UsiFoundryException(INVALID_INPUT, message,
                    "The evidence bundle describes a different identity than the one requested. "
                            + "Choose a bundle whose subject matches the topic, or change the topic to match the bundle.");
        }
        if (message.contains("Provider") || message.contains("provider command")
                || message.contains("timed out") || message.contains("exit code")) {
            return new UsiFoundryException(PROVIDER_FAILURE, message,
                    "The research provider did not return a usable bundle. Check the provider command and timeout "
                            + "in the application settings.");
        }
        if (message.contains("Unable to hash registry evidence") || message.contains("Registry evidence hash mismatch")
                || message.contains("source snapshot")) {
            return new UsiFoundryException(SOURCE_ACQUISITION_FAILURE, message, null);
        }
        if (message.contains("schema validation") || message.contains("must be") || message.contains("does not match")) {
            return new UsiFoundryException(INVALID_EVIDENCE, message,
                    "The evidence bundle did not satisfy the Foundry contracts. Nothing was manufactured.");
        }
        return new UsiFoundryException(UNCLASSIFIED, message);
    }

    public Map<String,Object> toMap() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("error", code);
        out.put("message", getMessage());
        if (guidance != null) out.put("guidance", guidance);
        return out;
    }
}
