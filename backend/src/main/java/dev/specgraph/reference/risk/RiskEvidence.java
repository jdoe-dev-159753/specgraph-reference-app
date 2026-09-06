package dev.specgraph.reference.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Upstream rule assessment attached to a source transaction.
 *
 * <p>This is retained source risk evidence. It stays distinct from detector-generated
 * {@code RiskSignalEvidence}; the contribution is an upstream rule value and is not asserted to be
 * a calibrated probability.
 */
public record RiskEvidence(
        UUID assessmentId,
        UUID transactionId,
        String ruleId,
        String ruleName,
        Instant triggeredAt,
        BigDecimal scoreContribution) {}
