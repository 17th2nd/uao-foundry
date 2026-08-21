package org.seventeenthsecond.uaofoundry.identity;

/**
 * The Foundry's identity decision space.
 *
 * <p>Deliberately three-valued. Binary same/not-same forces a determination that available
 * evidence often cannot support, and the forced answer then becomes indistinguishable from an
 * evidenced one. {@code UNRESOLVED} is a first-class outcome, not a failure.
 *
 * <p>Note in particular that the absence of a registered match yields {@link #UNRESOLVED} and
 * never {@link #DIFFERENT}. Not having seen an identity before is not evidence that a new
 * reference denotes a different object.
 */
public enum IdentityDecision {
    /** Positive evidence that the reference denotes an already-registered identity. */
    SAME,
    /** Positive evidence of contradiction. Never inferred from mere absence of a match. */
    DIFFERENT,
    /** Evidence is insufficient to decide. The default, and never an error. */
    UNRESOLVED
}
