package dev.specgraph.reference.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Activity(
        UUID transactionId,
        ActivityType type,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Map<String, String> details) {
    public Activity {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(type, "type");
        details = Map.copyOf(details);
    }

    public enum ActivityType { CARD, PAYMENT, CRYPTO }
}
