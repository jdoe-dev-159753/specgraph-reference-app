package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
