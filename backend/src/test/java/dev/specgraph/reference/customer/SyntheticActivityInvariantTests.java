package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("unit_property")
/**
 * Proves fixed-fixture repeatability, exact activity specialization and source-risk ownership.
 * These invariants characterize synthetic demo data and do not validate an upstream data feed.
 */
final class SyntheticActivityInvariantTests {
    @Test
    void freshAdaptersProduceTheSameDeterministicSnapshot() {
        var first = new SyntheticActivityAdapter().loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID);
        var second = new SyntheticActivityAdapter().loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID);

        assertThat(first).isPresent();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void eachActivityCarriesOnlyItsMatchingSourceSpecialization() {
        CustomerSnapshot snapshot = new SyntheticActivityAdapter()
                .loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                .orElseThrow();

        for (Activity activity : snapshot.activities()) {
            switch (activity.type()) {
                case CARD -> assertThat(activity.details()).isInstanceOf(Activity.CardDetails.class);
                case PAYMENT -> assertThat(activity.details()).isInstanceOf(Activity.PaymentDetails.class);
                case CRYPTO -> assertThat(activity.details()).isInstanceOf(Activity.CryptoDetails.class);
            }
        }

        assertThatThrownBy(() -> new Activity(
                UUID.randomUUID(),
                Activity.ActivityType.CARD,
                BigDecimal.ONE,
                "CHF",
                "Completed",
                Instant.EPOCH,
                new Activity.PaymentDetails("BANK_TRANSFER", "sender", "receiver", "CH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("details must match activity type");
    }

    @Test
    void cardPresentRemainsABooleanApplicationValue() {
        CustomerSnapshot snapshot = new SyntheticActivityAdapter()
                .loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                .orElseThrow();
        Activity card = snapshot.activities().stream()
                .filter(activity -> activity.type() == Activity.ActivityType.CARD)
                .findFirst()
                .orElseThrow();

        Activity.CardDetails details = (Activity.CardDetails) card.details();
        assertThat(details.cardPresent()).isFalse();
    }

    @Test
    void everySourceRiskSignalReferencesAnActivityInTheSelectedCustomerSnapshot() {
        CustomerSnapshot snapshot = new SyntheticActivityAdapter()
                .loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                .orElseThrow();
        Set<UUID> transactionIds = snapshot.activities().stream()
                .map(Activity::transactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(snapshot.riskEvidence()).isNotEmpty();
        assertThat(snapshot.riskEvidence())
                .allSatisfy(evidence -> assertThat(transactionIds).contains(evidence.transactionId()));
    }
}
