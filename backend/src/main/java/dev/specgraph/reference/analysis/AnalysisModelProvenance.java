package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral identity and grounding trail for one model execution.
 *
 * <p>Evidence references point only to details supplied in the bounded model envelope; they do not
 * establish that retrieved policy, derived signals and source facts have equal authority. Metadata
 * is retained as immutable diagnostics and must not be interpreted as part of the domain result.
 */
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
