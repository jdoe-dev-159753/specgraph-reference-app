package dev.specgraph.reference.analysis;

import java.util.Map;

public record PolicyEvidence(String sourceIdentity, String content, Map<String, String> retrievalMetadata) {}
