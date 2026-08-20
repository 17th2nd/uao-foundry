package org.seventeenthsecond.uaofoundry.significance;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rule that UAO identity state never stores significance.
 *
 * <h2>Why the boundary exists</h2>
 *
 * Significance is <em>transient</em>. It is computed for an objective {@code q}, in a context
 * {@code C_q}, at an epoch {@code e} — none of which the Foundry knows or should know. A
 * significance result baked into identity state would be a snapshot of one runtime's judgement,
 * indistinguishable from a durable fact about the object, and every later reader would inherit it
 * as though it were one.
 *
 * <p>What a significance computation produces may legitimately become durable — new evidence, a
 * changed state, a new or superseded relationship, a validity update. Those arrive through the
 * ordinary manufacture and lifecycle paths. The <em>result itself</em> never does.
 *
 * <h2>Two tiers of rejected field</h2>
 *
 * Kept separate deliberately, because they carry different authority:
 *
 * <ul>
 *   <li>{@link #ASA_FORBIDDEN} is derived from ADR-0002 §5, which reflects current ASA authority.
 *       The Foundry did not choose these and may not relax them.</li>
 *   <li>{@link #FOUNDRY_FORBIDDEN} is Foundry-local defence in depth. Narrowing what the Foundry
 *       is willing to emit is always safe; it creates no ASA authority and must never be presented
 *       as though it did.</li>
 * </ul>
 */
public final class SignificanceBoundary {

    /** ADR-0002 §5, reflecting current ASA authority. Not Foundry-chosen; not Foundry-relaxable. */
    public static final Set<String> ASA_FORBIDDEN = Set.of("score", "significance_value", "belief", "stance");

    /**
     * Foundry-local tightening. Every one of these names a transient runtime judgement that would
     * masquerade as a durable property of the object if persisted.
     */
    public static final Set<String> FOUNDRY_FORBIDDEN = Set.of(
            "significance", "importance", "priority", "urgency_score", "attention_weight",
            "reasoning_tier", "allocation_score", "historical_significance");

    private SignificanceBoundary() {}

    /** Recursively collects every forbidden field, labelling which tier each violation breaches. */
    public static void collect(Object value, String path, List<String> errors) {
        if (value instanceof Map<?,?> map) {
            for (Map.Entry<?,?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (ASA_FORBIDDEN.contains(key)) {
                    errors.add("Forbidden ASA field at " + path + "." + key);
                } else if (FOUNDRY_FORBIDDEN.contains(key)) {
                    errors.add("Forbidden significance field at " + path + "." + key
                            + " (UAO identity state does not store significance)");
                }
                collect(entry.getValue(), path + "." + key, errors);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) collect(list.get(i), path + "[" + i + "]", errors);
        }
    }
}
