package org.seventeenthsecond.uaofoundry.validation;

import java.util.List;

public record ValidationResult(List<String> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
    }
    public boolean valid() { return errors.isEmpty(); }
    public void requireValid(String subject) {
        if (!valid()) throw new IllegalArgumentException(subject + " failed schema validation: " + String.join("; ", errors));
    }
}
