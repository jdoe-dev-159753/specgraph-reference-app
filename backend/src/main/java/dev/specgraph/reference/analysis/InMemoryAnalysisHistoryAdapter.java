package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InMemoryAnalysisHistoryAdapter implements AnalysisHistoryPort {
    private final List<StoredAnalysis> analyses = new ArrayList<>();

    @Override
    public synchronized UUID persist(UUID customerId, OperatorId operatorId, Instant generatedAt,
                                     AnalysisResult result, List<PolicyEvidence> evidence) {
        var id = UUID.randomUUID();
        analyses.add(new StoredAnalysis(id, customerId, operatorId, generatedAt, result, List.copyOf(evidence)));
        return id;
    }

    @Override
    public synchronized List<StoredAnalysis> listByCustomer(UUID customerId) {
        return analyses.stream().filter(a -> a.customerId().equals(customerId)).toList();
    }
}
