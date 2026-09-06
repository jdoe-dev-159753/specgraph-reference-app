package dev.specgraph.reference.identity;

import java.util.Objects;

/**
 * Project-owned, non-blank identity of the operator accountable for an analysis action.
 * The value deliberately avoids coupling application records to a security-provider principal.
 */
public record OperatorId(String value) {
    public OperatorId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("operator identity must not be blank");
    }
}
