package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InMemoryAnalysisHistoryAdapter implements AnalysisHistoryPort {
    private final List<AnalysisHistoryEntry> analyses = new ArrayList<>();

    @Override
    public synchronized AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
        var entry = new AnalysisHistoryEntry(
                UUID.randomUUID(),
                command.customerId(),
                command.operatorId(),
                command.generatedAt(),
                command.result(),
                command.evidenceProvenance());
        analyses.add(entry);
        return entry;
    }

    @Override
    public synchronized List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
        return analyses.stream().filter(a -> a.customerId().equals(customerId)).toList();
    }
}
