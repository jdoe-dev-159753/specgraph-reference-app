package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AnalysisHistoryCreateCommand(
        UUID customerId,
        OperatorId operatorId,
        Instant generatedAt,
        AnalysisResult result,
        List<PolicyEvidence> evidenceProvenance,
        List<RiskSignalEvidence> detectorProvenance,
        AnalysisModelProvenance modelProvenance) {
    public AnalysisHistoryCreateCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(result, "result");
        evidenceProvenance = List.copyOf(Objects.requireNonNull(evidenceProvenance, "evidenceProvenance"));
        detectorProvenance = List.copyOf(Objects.requireNonNull(detectorProvenance, "detectorProvenance"));
        Objects.requireNonNull(modelProvenance, "modelProvenance");
    }
}
