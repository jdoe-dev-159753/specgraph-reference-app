package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisHistoryPort {
    AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command);

    List<AnalysisHistoryEntry> listByCustomer(UUID customerId);

    Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId);
}
