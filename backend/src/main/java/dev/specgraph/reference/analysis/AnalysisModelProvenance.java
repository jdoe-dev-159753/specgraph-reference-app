package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AnalysisModelProvenance(
        String backendIdentity,
        String modelIdentity,
        String promptIdentity,
        List<AnalysisEvidenceReference> evidenceReferences,
        Map<String, String> metadata) implements AnalysisPipelineArtifact {
    private static final String LEGACY_PROMPT_IDENTITY = "r3-legacy-unversioned";

    public AnalysisModelProvenance {
        backendIdentity = requireText(backendIdentity, "backendIdentity");
        modelIdentity = requireText(modelIdentity, "modelIdentity");
        promptIdentity = promptIdentity == null || promptIdentity.isBlank()
                ? LEGACY_PROMPT_IDENTITY
                : promptIdentity;
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        if (evidenceReferences.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evidenceReferences must not contain null values");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * Backward-compatible construction for pre-grounding callers and pre-R4 persisted JSON.
     */
    public AnalysisModelProvenance(
            String backendIdentity,
            String modelIdentity,
            Map<String, String> metadata) {
        this(backendIdentity, modelIdentity, LEGACY_PROMPT_IDENTITY, List.of(), metadata);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
