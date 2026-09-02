package dev.specgraph.reference.analysis;

import java.util.Map;
import java.util.Objects;

/** Derived detector evidence. It is advisory model input and never source risk truth. */
public record RiskSignalEvidence(
        String detectorIdentity,
        String signalIdentity,
        double score,
        Map<String, String> provenance) {
    public RiskSignalEvidence {
        detectorIdentity = requireText(detectorIdentity, "detectorIdentity");
        signalIdentity = requireText(signalIdentity, "signalIdentity");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
