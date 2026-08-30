package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalysisHistoryPort {
    UUID persist(UUID customerId, OperatorId operatorId, Instant generatedAt, AnalysisResult result,
                 List<PolicyEvidence> evidence);

    List<StoredAnalysis> listByCustomer(UUID customerId);

    record StoredAnalysis(UUID analysisId, UUID customerId, OperatorId operatorId, Instant generatedAt,
                          AnalysisResult result, List<PolicyEvidence> evidence) {}
}
