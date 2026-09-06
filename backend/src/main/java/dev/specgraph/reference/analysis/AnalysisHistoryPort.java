package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence boundary for generated analysis records.
 *
 * <p>Implementations allocate the analysis identifier when persisting a complete write command.
 * Customer identity is part of every read key so an analysis cannot be retrieved through another
 * customer's history. Storage failures are reported as runtime failures and translated by the
 * application service.
 */
public interface AnalysisHistoryPort {
    /** Persists one immutable analysis snapshot and returns its assigned identity. */
    AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command);

    /** Returns the complete retained history for compatibility consumers. */
    List<AnalysisHistoryEntry> listByCustomer(UUID customerId);

    /**
     * Returns a bounded page; adapters may override this default to perform pagination in storage.
     */
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

    /** Finds an analysis only when both its customer and analysis identities match. */
    Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId);
}
