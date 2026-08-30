package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DeterministicAnalysisAdapter implements AnalysisModelPort {
    @Override
    public AnalysisResult analyze(CustomerSnapshot snapshot, List<PolicyEvidence> evidence) {
        return new AnalysisResult(AnalysisResult.RiskLevel.LOW, "Deterministic R0 shell result",
                List.of("Review source activity and risk evidence."));
    }
}
