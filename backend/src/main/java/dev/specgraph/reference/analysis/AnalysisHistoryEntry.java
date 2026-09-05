package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Retained analysis record returned to application consumers.
 *
 * <p>The generated assessment is advisory. Its policy retrievals and derived detector signals are
 * preserved as provenance rather than promoted to source facts. All collections are immutable
 * snapshots suitable for crossing an adapter boundary.
 */
public record AnalysisHistoryEntry(
        UUID analysisId,
        UUID customerId,
        OperatorId operatorId,
        Instant generatedAt,
        AnalysisResult result,
        List<PolicyEvidence> evidenceProvenance,
        List<RiskSignalEvidence> detectorProvenance,
        AnalysisModelProvenance modelProvenance) {
    public AnalysisHistoryEntry {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(result, "result");
        evidenceProvenance = List.copyOf(Objects.requireNonNull(evidenceProvenance, "evidenceProvenance"));
        detectorProvenance = List.copyOf(Objects.requireNonNull(detectorProvenance, "detectorProvenance"));
        Objects.requireNonNull(modelProvenance, "modelProvenance");
    }
}
