package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class BayesianSequentialRiskSignalDetectorAdapterTests {
    private final BayesianSequentialRiskSignalDetectorAdapter detector =
            new BayesianSequentialRiskSignalDetectorAdapter();

    @Test
    void posteriorIsRepeatableAndSensitiveToIncreasingReviewElevatedEvidence() {
        RiskSignalEvidence stable = onlySignal(snapshot(
                card(1, "Completed"),
                payment(2, "Completed", "CH"),
                card(3, "Completed")));
        RiskSignalEvidence growing = onlySignal(snapshot(
                payment(4, "Completed", "CH"),
                payment(5, "Completed", "DE"),
                payment(6, "Completed", "NL"),
                crypto(7, "Completed")));
        CustomerSnapshot denseSnapshot = snapshot(
                card(8, "Declined"),
                card(9, "Declined"),
                payment(10, "Completed", "GB"),
                payment(11, "Completed", "AE"),
                crypto(12, "Completed"));
        RiskSignalEvidence dense = onlySignal(denseSnapshot);

        assertThat(stable.score()).isLessThan(0.10);
        assertThat(growing.score()).isGreaterThan(stable.score());
        assertThat(dense.score()).isGreaterThan(growing.score()).isGreaterThan(0.80);
        assertThat(onlySignal(denseSnapshot)).isEqualTo(dense);

        assertThat(stable.provenance()).containsEntry("elevatedObservations", "0");
        assertThat(growing.provenance()).containsEntry("elevatedObservations", "3");
        assertThat(dense.provenance())
                .containsEntry("elevatedObservations", "5")
                .containsEntry("totalObservations", "5")
                .containsEntry("library", "apache-commons-math3-3.6.1")
                .containsEntry("demoLimitation", "synthetic heuristic; not production AML calibration");
    }

    @Test
    void noActivityProducesNoDerivedSignalAndNeverInventsSourceRiskFacts() {
        CustomerSnapshot snapshot = new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of());
        assertThat(detector.detect(snapshot)).isEmpty();
        assertThat(snapshot.riskEvidence()).isEmpty();
    }

    private RiskSignalEvidence onlySignal(CustomerSnapshot snapshot) {
        return detector.detect(snapshot).getFirst();
    }

    private CustomerSnapshot snapshot(Activity... activities) {
        return new CustomerSnapshot(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                List.of(activities),
                List.of());
    }

    private Activity card(int suffix, String status) {
        return new Activity(
                id(suffix),
                Activity.ActivityType.CARD,
                new BigDecimal("100.00"),
                "CHF",
                status,
                Instant.parse("2026-08-30T08:00:00Z").plusSeconds(suffix),
                new Activity.CardDetails(
                        "**** **** **** 0000", "VISA", "Synthetic Merchant", "0000", true,
                        "AUTH-" + suffix, status.equals("Declined") ? "Synthetic decline" : null));
    }

    private Activity payment(int suffix, String status, String receiverCountry) {
        return new Activity(
                id(suffix),
                Activity.ActivityType.PAYMENT,
                new BigDecimal("1000.00"),
                "CHF",
                status,
                Instant.parse("2026-08-30T08:00:00Z").plusSeconds(suffix),
                new Activity.PaymentDetails(
                        "BANK_TRANSFER", "CH00-SYNTHETIC", receiverCountry + "00-SYNTHETIC", receiverCountry));
    }

    private Activity crypto(int suffix, String status) {
        return new Activity(
                id(suffix),
                Activity.ActivityType.CRYPTO,
                new BigDecimal("1.00"),
                "ETH",
                status,
                Instant.parse("2026-08-30T08:00:00Z").plusSeconds(suffix),
                new Activity.CryptoDetails(
                        "Ethereum", "0xfrom", "0xto", "synthetic-hash-" + suffix, "Synthetic Exchange"));
    }

    private UUID id(int suffix) {
        return UUID.fromString("eeeeeeee-eeee-eeee-eeee-" + String.format("%012d", suffix));
    }
}
