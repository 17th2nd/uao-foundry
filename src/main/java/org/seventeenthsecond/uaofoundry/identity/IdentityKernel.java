package org.seventeenthsecond.uaofoundry.identity;

import org.seventeenthsecond.uaofoundry.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assembles the minimum persistent identity kernel carried inside
 * {@code internal_state.foundry_identity} of a canonical UAO.
 *
 * <p>The kernel lives under {@code internal_state} because the ASA UAO validation projection is a
 * closed schema — {@code additionalProperties: false} — so the Foundry may not add top-level UAO
 * members. {@code internal_state} is the one deliberately open object, and this is Foundry-owned
 * material within it. Nothing here amends ASA authority.
 *
 * <p>Kernel members and where each comes from:
 *
 * <table>
 *   <tr><td>{@code canonical_label}</td><td>resolved identity</td></tr>
 *   <tr><td>{@code aliases}</td><td>resolved identity, label removed</td></tr>
 *   <tr><td>{@code resolution_key}</td><td>provider, canonicalised by {@code ResolutionKeys}</td></tr>
 *   <tr><td>{@code semantic_type}</td><td>derived from the key grammar; {@code null} for {@code ext:}</td></tr>
 *   <tr><td>{@code external_identifiers}</td><td>provider, canonicalised by {@link ExternalIdentifiers}</td></tr>
 *   <tr><td>{@code identity_digest}</td><td>derived over the identity projection</td></tr>
 *   <tr><td>{@code state_version}</td><td>derived over the state projection, attached last</td></tr>
 *   <tr><td>{@code source_refs}</td><td>resolved identity provenance</td></tr>
 * </table>
 *
 * <p>{@code uid} and {@code lifecycle_status} are deliberately <em>not</em> duplicated here. Both
 * already exist as ASA-governed top-level UAO members and restating them would create two places
 * to disagree.
 *
 * <p>Both digests are derived, never authored. A provider cannot supply them, and an independent
 * verifier re-derives and compares them.
 */
public final class IdentityKernel {
    private IdentityKernel() {}

    /**
     * Builds the identity-bearing half of the kernel from a resolved identity record. The
     * {@code state_version} is attached separately once the surrounding canonical UAO exists,
     * because it covers that UAO's state projection.
     */
    public static Map<String,Object> build(Map<String,Object> resolvedIdentity) {
        Map<String,Object> kernel = new LinkedHashMap<>();
        kernel.put("canonical_label", resolvedIdentity.get("label"));
        kernel.put("aliases", deepCopy(resolvedIdentity.get("aliases")));
        kernel.put("resolution_key", resolvedIdentity.get("resolutionKey"));
        kernel.put("semantic_type", resolvedIdentity.get("semanticType"));
        kernel.put("external_identifiers", deepCopy(resolvedIdentity.get("externalIdentifiers")));
        kernel.put("identity_digest", IdentityProjections.identityDigest(
                String.valueOf(resolvedIdentity.get("uaoId")), kernel));
        kernel.put("source_refs", deepCopy(resolvedIdentity.get("sourceRefs")));
        return kernel;
    }

    private static Object deepCopy(Object value) { return value == null ? null : Json.parse(Json.canonical(value)); }
}
