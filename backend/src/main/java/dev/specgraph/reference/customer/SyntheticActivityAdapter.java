package dev.specgraph.reference.customer;

import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SyntheticActivityAdapter implements CustomerActivityPort {
    static final UUID SEEDED_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CARD_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID PAYMENT_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID CRYPTO_TX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");

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
                    new RiskEvidence(CARD_TX, "RULE-CARD-01", "Card not present high value",
                            Instant.parse("2026-08-28T09:15:01Z"), new BigDecimal("12.5")),
                    new RiskEvidence(CRYPTO_TX, "RULE-CRYPTO-01", "New crypto destination",
                            Instant.parse("2026-08-30T14:05:01Z"), new BigDecimal("18.0"))));

    @Override
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        return SEEDED_CUSTOMER_ID.equals(customerId) ? Optional.of(seeded) : Optional.empty();
    }
}
