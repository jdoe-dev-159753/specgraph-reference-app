package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Fixed in-memory customer fixture implementing the same complete-snapshot and bounded-review ports
 * as relational storage. It supports offline acceptance only and never fabricates unknown customers.
 */
@Component
class SyntheticActivityAdapter implements CustomerActivityPort, CustomerReviewQueryPort {
    static final UUID SEEDED_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CARD_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID PAYMENT_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID CRYPTO_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
    private static final UUID CARD_RISK = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CRYPTO_RISK = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final CustomerSnapshot seeded = new CustomerSnapshot(
            SEEDED_CUSTOMER_ID,
            List.of(
                    new Activity(CARD_TX, Activity.ActivityType.CARD, new BigDecimal("248.50"), "CHF", "Completed",
                            Instant.parse("2026-08-28T09:15:00Z"),
                            new Activity.CardDetails(
                                    "**** **** **** 4242", "VISA", "Alpine Camera", "5946", false, "A12345", null)),
                    new Activity(PAYMENT_TX, Activity.ActivityType.PAYMENT, new BigDecimal("1250.00"), "CHF", "Completed",
                            Instant.parse("2026-08-29T11:30:00Z"),
                            new Activity.PaymentDetails(
                                    "BANK_TRANSFER", "CH00-SYNTHETIC-01", "DE00-SYNTHETIC-02", "DE")),
                    new Activity(CRYPTO_TX, Activity.ActivityType.CRYPTO, new BigDecimal("0.42"), "BTC", "Pending",
                            Instant.parse("2026-08-30T14:05:00Z"),
                            new Activity.CryptoDetails(
                                    "Bitcoin", "bc1q-demo-from", "bc1q-demo-to", "synthetic-tx-hash", "Demo Exchange"))),
            List.of(
                    new RiskEvidence(CARD_RISK, CARD_TX, "RULE-CARD-01", "Card not present high value",
                            Instant.parse("2026-08-28T09:15:01Z"), new BigDecimal("12.5")),
                    new RiskEvidence(CRYPTO_RISK, CRYPTO_TX, "RULE-CRYPTO-01", "New crypto destination",
                            Instant.parse("2026-08-30T14:05:01Z"), new BigDecimal("18.0"))));

    @Override
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        return SEEDED_CUSTOMER_ID.equals(customerId) ? Optional.of(seeded) : Optional.empty();
    }

    /** Applies the same filter/page/evidence semantics as the durable adapter to the fixed fixture. */
    @Override
    public Optional<CustomerReviewPage> loadReviewPage(UUID customerId, CustomerReviewQuery query) {
        if (!SEEDED_CUSTOMER_ID.equals(customerId)) {
            return Optional.empty();
        }

        List<Activity> matchingActivities = seeded.activities().stream()
                .filter(activity -> matches(activity, query))
                .toList();
        long offset = query.offset();
        List<Activity> pageActivities = offset >= matchingActivities.size()
                ? List.of()
                : matchingActivities.subList(
                        (int) offset,
                        Math.min(matchingActivities.size(), (int) offset + query.pageSize()));

        Set<UUID> matchingTransactionIds = matchingActivities.stream()
                .map(Activity::transactionId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> pageTransactionIds = pageActivities.stream()
                .map(Activity::transactionId)
                .collect(Collectors.toUnmodifiableSet());

        List<RiskEvidence> pageRiskEvidence = seeded.riskEvidence().stream()
                .filter(risk -> pageTransactionIds.contains(risk.transactionId()))
                .toList();
        long totalRiskEvidence = seeded.riskEvidence().stream()
                .filter(risk -> matchingTransactionIds.contains(risk.transactionId()))
                .count();

        return Optional.of(new CustomerReviewPage(
                customerId,
                pageActivities,
                pageRiskEvidence,
                query.page(),
                query.pageSize(),
                matchingActivities.size(),
                totalRiskEvidence));
    }

    /** Uses a half-open upper timestamp bound to match the database review query contract. */
    private static boolean matches(Activity activity, CustomerReviewQuery query) {
        if (query.activityType() != null && activity.type() != query.activityType()) {
            return false;
        }
        if (query.status() != null && !activity.status().equalsIgnoreCase(query.status())) {
            return false;
        }
        if (query.createdFrom() != null && activity.createdAt().isBefore(query.createdFrom())) {
            return false;
        }
        return query.createdTo() == null || activity.createdAt().isBefore(query.createdTo());
    }
}
