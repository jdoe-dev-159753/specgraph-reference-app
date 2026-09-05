package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete application-owned write request for one generated analysis.
 *
 * <p>The command binds the result to the customer, authenticated operator and generation instant,
 * and keeps policy, detector and model provenance in separate evidence families. Collections are
 * defensively copied so persistence adapters receive an immutable snapshot of the decision record.
 */
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
