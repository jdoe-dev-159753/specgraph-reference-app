package dev.specgraph.reference.analysis;

import java.util.Map;
import java.util.Objects;

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
