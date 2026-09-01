package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisUseCase {
    AnalysisHistoryEntry analyze(UUID customerId, OperatorId operatorId);

    List<AnalysisHistoryEntry> listHistory(UUID customerId);

    Optional<AnalysisHistoryEntry> findHistory(UUID customerId, UUID analysisId);
}
