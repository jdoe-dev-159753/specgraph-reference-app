package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
/**
 * Characterizes deterministic bounds, explainability and risk-positive monotonicity of the heuristic
 * fuzzy surface. Crafted scenarios are not calibration or production-performance evidence.
 */
final class FuzzyRiskSignalDetectorAdapterTests {
    private final FuzzyRiskSignalDetectorAdapter detector = new FuzzyRiskSignalDetectorAdapter();

    @Test
    void scoreIsRepeatableGradedAndMonotonicForIncreasingCrossBorderExposure() {
        RiskSignalEvidence domestic = onlySignal(snapshot(
                payment(1, "CH"), payment(2, "CH"), payment(3, "CH"), payment(4, "CH")));
        RiskSignalEvidence oneForeign = onlySignal(snapshot(
                payment(1, "DE"), payment(2, "CH"), payment(3, "CH"), payment(4, "CH")));
        RiskSignalEvidence twoForeign = onlySignal(snapshot(
                payment(1, "DE"), payment(2, "NL"), payment(3, "CH"), payment(4, "CH")));
        CustomerSnapshot threeForeignSnapshot = snapshot(
                payment(1, "DE"), payment(2, "NL"), payment(3, "GB"), payment(4, "CH"));
        RiskSignalEvidence threeForeign = onlySignal(threeForeignSnapshot);

        assertThat(domestic.score()).isEqualTo(0.05);
        assertThat(oneForeign.score()).isGreaterThan(domestic.score());
        assertThat(twoForeign.score()).isGreaterThan(oneForeign.score());
        assertThat(threeForeign.score()).isGreaterThan(twoForeign.score());
        assertThat(onlySignal(threeForeignSnapshot)).isEqualTo(threeForeign);

        assertThat(twoForeign.provenance())
                .containsEntry("crossBorderRatio", "0.500000")
                .containsEntry("activation.R2_CROSS_BORDER", "0.800000")
                .containsEntry("ruleSetVersion", FuzzyRiskSignalDetectorAdapter.RULE_SET_VERSION)
                .containsEntry("defuzzification", "weighted-singleton-monotonic-v2")
                .containsEntry("positiveConsequent", "1.000000");
    }

    @Test
    void addingIncompleteStatusToCryptoEvidenceCannotLowerScore() {
        RiskSignalEvidence allCompleted = onlySignal(snapshot(
                crypto(20, "Completed"),
                crypto(21, "Completed"),
                crypto(22, "Completed"),
                crypto(23, "Completed")));
        RiskSignalEvidence oneDeclined = onlySignal(snapshot(
                crypto(20, "Declined"),
                crypto(21, "Completed"),
                crypto(22, "Completed"),
                crypto(23, "Completed")));

        assertThat(oneDeclined.provenance().get("activation.R3_INCOMPLETE"))
                .isNotEqualTo("0.000000");
        assertThat(oneDeclined.score()).isGreaterThan(allCompleted.score());
    }

    @Test
    void mixedSignalsRemainBoundedExplainableAndDoNotMutateSourceRisk() {
        Activity foreign = payment(10, "AE");
        RiskEvidence sourceRisk = new RiskEvidence(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                foreign.transactionId(),
                "SYNTH-RULE-1",
                "Synthetic source rule",
                Instant.parse("2026-08-30T08:10:00Z"),
                new BigDecimal("0.35"));
        CustomerSnapshot snapshot = new CustomerSnapshot(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                List.of(
                        foreign,
                        crypto(11, "Completed"),
                        card(12, "Declined"),
                        payment(13, "CH")),
                List.of(sourceRisk));
        List<RiskEvidence> sourceBefore = snapshot.riskEvidence();

        RiskSignalEvidence signal = onlySignal(snapshot);

        assertThat(signal.score()).isBetween(0.0, 1.0).isGreaterThan(0.80);
        assertThat(signal.detectorIdentity()).isEqualTo(FuzzyRiskSignalDetectorAdapter.DETECTOR_IDENTITY);
        assertThat(signal.signalIdentity()).isEqualTo(FuzzyRiskSignalDetectorAdapter.SIGNAL_IDENTITY);
        assertThat(signal.provenance())
                .containsEntry("cryptoRatio", "0.250000")
                .containsEntry("incompleteRatio", "0.250000")
                .containsEntry("sourceRiskDensity", "0.250000")
                .containsEntry("implementation", "project-owned-minimal-fuzzy-inference-v2")
                .containsEntry("demoLimitation", "synthetic heuristic; not production AML calibration");
        assertThat(signal.provenance().get("activation.R5_CROSS_BORDER_WITH_SOURCE_RISK"))
                .isNotEqualTo("0.000000");
        assertThat(snapshot.riskEvidence()).containsExactlyElementsOf(sourceBefore);
    }

    @Test
    void noActivityProducesNoDerivedSignal() {
        CustomerSnapshot snapshot = new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of());
        assertThat(detector.detect(snapshot)).isEmpty();
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

    private Activity payment(int suffix, String receiverCountry) {
        return new Activity(
                id(suffix),
                Activity.ActivityType.PAYMENT,
                new BigDecimal("1000.00"),
                "CHF",
                "Completed",
                time(suffix),
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
                time(suffix),
                new Activity.CryptoDetails(
                        "Ethereum", "0xfrom", "0xto", "synthetic-hash-" + suffix, "Synthetic Exchange"));
    }

    /** Creates a non-cross-border, non-crypto control whose only fuzzy variable is completion status. */
    private Activity card(int suffix, String status) {
        return new Activity(
                id(suffix),
                Activity.ActivityType.CARD,
                new BigDecimal("100.00"),
                "CHF",
                status,
                time(suffix),
                new Activity.CardDetails(
                        "**** **** **** 0000", "VISA", "Synthetic Merchant", "0000", true,
                        "AUTH-" + suffix, "Declined".equals(status) ? "Synthetic decline" : null));
    }

    private Instant time(int suffix) {
        return Instant.parse("2026-08-30T08:00:00Z").plusSeconds(suffix);
    }

    private UUID id(int suffix) {
        return UUID.fromString("dddddddd-dddd-dddd-dddd-" + String.format("%012d", suffix));
    }
}
