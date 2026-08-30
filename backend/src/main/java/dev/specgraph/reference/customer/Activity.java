package dev.specgraph.reference.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Activity(
        UUID transactionId,
        ActivityType type,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Map<String, String> details) {
    public enum ActivityType { CARD, PAYMENT, CRYPTO }
}
