package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Complete application-owned source snapshot used to derive an analysis.
 *
 * <p>Activities and upstream risk evidence remain separate collections and are defensively copied.
 * The snapshot is deliberately unbounded; a separate application boundary selects the detail that
 * may be sent to an external or local analysis model.
 */
public record CustomerSnapshot(
        UUID customerId,
        List<Activity> activities,
        List<RiskEvidence> riskEvidence) {
    public CustomerSnapshot {
        Objects.requireNonNull(customerId, "customerId");
        activities = List.copyOf(activities);
        riskEvidence = List.copyOf(riskEvidence);
    }
}
