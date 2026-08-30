package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.util.List;
import java.util.UUID;

public record CustomerSnapshot(
        UUID customerId,
        List<Activity> activities,
        List<RiskEvidence> riskEvidence) {}
