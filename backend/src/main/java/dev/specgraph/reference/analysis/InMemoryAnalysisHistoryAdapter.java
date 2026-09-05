package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Process-local history adapter for the hollow baseline profile.
 * Synchronization makes its mutable list deterministic within one process, but entries deliberately
 * disappear on restart; durable profiles replace it through the same port.
 */
@Component
class InMemoryAnalysisHistoryAdapter implements AnalysisHistoryPort {
    private final List<AnalysisHistoryEntry> analyses = new ArrayList<>();

    /** Creates a fresh identity and stores one immutable process-local history entry atomically. */
    @Override
    public synchronized AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
        var entry = new AnalysisHistoryEntry(
                UUID.randomUUID(),
                command.customerId(),
                command.operatorId(),
                command.generatedAt(),
                command.result(),
                command.evidenceProvenance(),
                command.detectorProvenance(),
                command.modelProvenance());
        analyses.add(entry);
        return entry;
    }

    @Override
    public synchronized List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
        return analyses.stream().filter(a -> a.customerId().equals(customerId)).toList();
    }

    @Override
    public synchronized Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId) {
        return analyses.stream()
                .filter(a -> a.customerId().equals(customerId) && a.analysisId().equals(analysisId))
                .findFirst();
    }
}
