package dev.specgraph.reference.identity;

import java.util.Objects;

public record OperatorId(String value) {
    public OperatorId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("operator identity must not be blank");
    }
}
