package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class NoOpRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        return List.of();
    }
}
