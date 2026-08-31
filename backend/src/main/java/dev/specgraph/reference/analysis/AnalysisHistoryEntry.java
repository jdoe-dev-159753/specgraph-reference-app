package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AnalysisHistoryEntry(
        UUID analysisId,
        UUID customerId,
        OperatorId operatorId,
        Instant generatedAt,
        AnalysisResult result,
        List<PolicyEvidence> evidenceProvenance) {
    public AnalysisHistoryEntry {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(result, "result");
        evidenceProvenance = List.copyOf(evidenceProvenance);
    }
}
