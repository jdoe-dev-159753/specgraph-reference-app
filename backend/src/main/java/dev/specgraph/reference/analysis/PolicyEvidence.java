package dev.specgraph.reference.analysis;

import java.util.Map;
import java.util.Objects;

/**
 * Retrieved policy excerpt supplied as grounding material to the analysis model.
 *
 * <p>Its source identity and retrieval metadata provide traceability, but retrieval relevance does
 * not make the excerpt a customer fact or a generated detector signal. Values are immutable after
 * construction and blank source or content values are rejected.
 */
public record PolicyEvidence(String sourceIdentity, String content, Map<String, String> retrievalMetadata)
        implements AnalysisPipelineArtifact {
    public PolicyEvidence {
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(retrievalMetadata, "retrievalMetadata");
        if (sourceIdentity.isBlank()) {
            throw new IllegalArgumentException("sourceIdentity must not be blank");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        retrievalMetadata = Map.copyOf(retrievalMetadata);
    }

    @Override
    public Map<String, String> metadata() {
        return retrievalMetadata;
    }
}
