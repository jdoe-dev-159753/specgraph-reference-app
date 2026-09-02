package dev.specgraph.reference.analysis;

import java.util.Map;
import java.util.Objects;

public record AnalysisModelProvenance(
        String backendIdentity,
        String modelIdentity,
        Map<String, String> metadata) implements AnalysisPipelineArtifact {
    public AnalysisModelProvenance {
        backendIdentity = requireText(backendIdentity, "backendIdentity");
        modelIdentity = requireText(modelIdentity, "modelIdentity");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
