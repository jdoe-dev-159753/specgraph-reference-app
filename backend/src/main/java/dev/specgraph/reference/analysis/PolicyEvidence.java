package dev.specgraph.reference.analysis;

import java.util.Map;
import java.util.Objects;

public record PolicyEvidence(String sourceIdentity, String content, Map<String, String> retrievalMetadata) {
    public PolicyEvidence {
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        Objects.requireNonNull(content, "content");
        retrievalMetadata = Map.copyOf(retrievalMetadata);
    }
}
