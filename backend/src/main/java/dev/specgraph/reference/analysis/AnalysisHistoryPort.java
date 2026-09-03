package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisHistoryPort {
    AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command);

    List<AnalysisHistoryEntry> listByCustomer(UUID customerId);

    default AnalysisHistoryPage pageByCustomer(UUID customerId, AnalysisHistoryQuery query) {
        List<AnalysisHistoryEntry> all = listByCustomer(customerId);
        long offset = query.offset();
        List<AnalysisHistoryEntry> entries = offset >= all.size()
                ? List.of()
                : all.subList(
                        (int) offset,
                        Math.min(all.size(), Math.toIntExact(Math.min(Integer.MAX_VALUE, offset + query.pageSize()))));
        return new AnalysisHistoryPage(entries, query.page(), query.pageSize(), all.size());
    }

    Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId);
}
