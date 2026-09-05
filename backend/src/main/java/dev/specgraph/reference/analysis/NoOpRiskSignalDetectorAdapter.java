package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Neutral detector leaf used when no derived-signal strategy is selected.
 * Returning no derived evidence preserves the distinction from persisted source risk facts.
 */
@Component
final class NoOpRiskSignalDetectorAdapter implements RiskSignalDetectorPort {
    @Override
    public List<RiskSignalEvidence> detect(CustomerSnapshot snapshot) {
        return List.of();
    }
}
