package dev.specgraph.reference.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RiskEvidence(
        UUID assessmentId,
        UUID transactionId,
        String ruleId,
        String ruleName,
        Instant triggeredAt,
        BigDecimal scoreContribution) {}
