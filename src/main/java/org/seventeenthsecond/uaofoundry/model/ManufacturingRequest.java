package org.seventeenthsecond.uaofoundry.model;

public record ManufacturingRequest(
        String schemaVersion,
        String identitySeed,
        String language,
        String manufacturingProfile
) {
    public ManufacturingRequest {
        requireNonBlank(schemaVersion, "schemaVersion");
        requireNonBlank(identitySeed, "identitySeed");
        requireNonBlank(language, "language");
        requireNonBlank(manufacturingProfile, "manufacturingProfile");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
    }
}
