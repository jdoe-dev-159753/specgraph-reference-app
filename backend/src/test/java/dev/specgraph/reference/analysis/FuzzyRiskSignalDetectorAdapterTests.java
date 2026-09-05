package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class FuzzyRiskSignalDetectorAdapterTests {
    private static final double EPSILON = 0.000001;

    private final FuzzyRiskSignalDetectorAdapter detector = new FuzzyRiskSignalDetectorAdapter();

    @Test
    void fourPersistedScenariosOccupyDistinctGradedRegions() {
        RiskSignalEvidence stable222 = onlySignal(scenario222());
        RiskSignalEvidence seeded111 = onlySignal(scenario111());
        RiskSignalEvidence growing333 = onlySignal(scenario333());
        CustomerSnapshot mixedSnapshot = scenario444();
        RiskSignalEvidence mixed444 = onlySignal(mixedSnapshot);

        assertThat(stable222.score()).isCloseTo(0.050000, within(EPSILON));
        assertThat(seeded111.score()).isCloseTo(0.490625, within(EPSILON));
        assertThat(growing333.score()).isCloseTo(0.618750, within(EPSILON));
        assertThat(mixed444.score()).isCloseTo(0.716518, within(EPSILON));
        assertThat(stable222.score()).isLessThan(seeded111.score());
        assertThat(seeded111.score()).isLessThan(growing333.score());
        assertThat(growing333.score()).isLessThan(mixed444.score());
        assertThat(growing333.score() - seeded111.score()).isGreaterThan(0.09);
        assertThat(mixed444.score() - growing333.score()).isGreaterThan(0.09);
        assertThat(onlySignal(mixedSnapshot)).isEqualTo(mixed444);
    }

    @Test
    void overlappingMembershipsAndRuleFiringsRemainInspectable() {
        RiskSignalEvidence signal = onlySignal(scenario333());

        assertThat(signal.provenance())
                .containsEntry("featureSchemaVersion", FuzzyRiskSignalDetectorAdapter.FEATURE_SCHEMA_VERSION)
                .containsEntry("ruleSetVersion", FuzzyRiskSignalDetectorAdapter.RULE_SET_VERSION)
                .containsEntry("crossBorderRatio", "0.500000")
                .containsEntry("raw.crossBorderRatio", "0.500000")
                .containsEntry("effective.crossBorderRatio", "0.333333")
                .containsEntry("membership.crossBorder.low", "0.000000")
                .containsEntry("membership.crossBorder.medium", "0.888889")
                .containsEntry("membership.crossBorder.high", "0.111111")
                .containsEntry("activation.R2_CROSS_BORDER", "0.555556")
                .containsEntry(
                        "monotonicDimensions",
                        "effective.cryptoRatio,effective.crossBorderRatio,effective.incompleteRatio,effective.sourceRiskRatio")
                .containsEntry("defuzzification", "fixed-weight-monotone-surface-v3")
                .containsEntry("calibration", "not an AML probability");
    }

    @Test
    void everyDeclaredPositiveDimensionIsMonotoneAtFixedObservationCount() {
        for (Dimension dimension : Dimension.values()) {
            double previous = -1.0;
            for (int positives = 0; positives <= 8; positives++) {
                double score = onlySignal(dimensionSnapshot(dimension, positives, 8)).score();
                assertThat(score)
                        .as("%s with %s positives", dimension, positives)
                        .isGreaterThanOrEqualTo(previous);
                previous = score;
            }
        }
    }

    @Test
    void oneFullyActivatedPrimitiveSignalCannotSaturateTheAggregate() {
        for (Dimension dimension : Dimension.values()) {
            RiskSignalEvidence signal = onlySignal(dimensionSnapshot(dimension, 8, 8));
            assertThat(signal.score())
                    .as("single fully activated %s dimension", dimension)
                    .isLessThan(0.60);
        }
        assertThat(onlySignal(scenario444()).score()).isLessThan(0.75);
    }

    @Test
    void addTwoPriorMakesTheSmallSampleTreatmentExplicitAndGraded() {
        RiskSignalEvidence oneCryptoObservation = onlySignal(dimensionSnapshot(Dimension.CRYPTO, 1, 1));

        assertThat(oneCryptoObservation.score()).isCloseTo(0.105556, within(EPSILON)).isLessThan(0.15);
        assertThat(oneCryptoObservation.provenance())
                .containsEntry("observationCount", "1")
                .containsEntry("effectiveObservationCount", "3")
                .containsEntry("smallSampleTreatment", "add-two zero-positive prior observations")
                .containsEntry("raw.cryptoRatio", "1.000000")
                .containsEntry("effective.cryptoRatio", "0.333333")
                .containsEntry("membership.crypto.medium", "0.888889")
                .containsEntry("membership.crypto.high", "0.111111");
    }

    @Test
    void coupledRuleIsVisibleButDoesNotDoubleCountItsEvidence() {
        RiskSignalEvidence mixed = onlySignal(scenario444());

        assertThat(mixed.provenance())
                .containsEntry("activation.R5_CROSS_BORDER_WITH_SOURCE_RISK", "0.464286")
                .containsEntry("consequent.R5_CROSS_BORDER_WITH_SOURCE_RISK", "0.000000")
                .containsEntry(
                        "interactionPolicy",
                        "diagnostic conjunction only; cross-border/source-risk consequents share a fixed 0.75 budget");
    }

    @Test
    void sourceRiskFactsRemainUnchangedAndOutputContractStaysGeneric() {
        CustomerSnapshot snapshot = scenario444();
        List<RiskEvidence> sourceBefore = snapshot.riskEvidence();

        RiskSignalEvidence signal = onlySignal(snapshot);

        assertThat(signal.detectorIdentity()).isEqualTo(FuzzyRiskSignalDetectorAdapter.DETECTOR_IDENTITY);
        assertThat(signal.signalIdentity()).isEqualTo(FuzzyRiskSignalDetectorAdapter.SIGNAL_IDENTITY);
        assertThat(signal.score()).isBetween(0.0, 1.0);
        assertThat(signal.provenance())
                .containsEntry("implementation", "project-owned-overlapping-fuzzy-inference-v3")
                .containsEntry("demoLimitation", "synthetic heuristic; not production AML calibration");
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

    private CustomerSnapshot scenario222() {
        List<Activity> activities = List.of(
                card(1, "Completed"),
                payment(2, "CH"),
                card(3, "Completed"));
        return snapshot("22222222-2222-2222-2222-222222222222", activities, 0);
    }

    private CustomerSnapshot scenario111() {
        List<Activity> activities = List.of(
                card(11, "Completed"),
                payment(12, "DE"),
                crypto(13, "Pending"));
        return snapshot("11111111-1111-1111-1111-111111111111", activities, 2);
    }

    private CustomerSnapshot scenario333() {
        List<Activity> activities = List.of(
                payment(31, "CH"),
                payment(32, "DE"),
                payment(33, "NL"),
                crypto(34, "Completed"));
        return snapshot("33333333-3333-3333-3333-333333333333", activities, 3);
    }

    private CustomerSnapshot scenario444() {
        List<Activity> activities = List.of(
                card(41, "Declined"),
                card(42, "Declined"),
                payment(43, "GB"),
                payment(44, "AE"),
                crypto(45, "Completed"));
        return snapshot("44444444-4444-4444-4444-444444444444", activities, 5);
    }

    private CustomerSnapshot dimensionSnapshot(Dimension dimension, int positives, int observations) {
        List<Activity> activities = new ArrayList<>();
        for (int index = 0; index < observations; index++) {
            boolean positive = index < positives;
            activities.add(switch (dimension) {
                case CRYPTO -> positive ? crypto(100 + index, "Completed") : card(100 + index, "Completed");
                case CROSS_BORDER -> positive ? payment(200 + index, "DE") : payment(200 + index, "CH");
                case INCOMPLETE -> card(300 + index, positive ? "Declined" : "Completed");
                case SOURCE_RISK -> card(400 + index, "Completed");
            });
        }
        int sourceRisks = dimension == Dimension.SOURCE_RISK ? positives : 0;
        return snapshot("77777777-7777-7777-7777-777777777777", activities, sourceRisks);
    }

    private CustomerSnapshot snapshot(String customerId, List<Activity> activities, int sourceRiskCount) {
        List<RiskEvidence> sourceRisks = new ArrayList<>();
        for (int index = 0; index < sourceRiskCount; index++) {
            Activity activity = activities.get(index % activities.size());
            sourceRisks.add(new RiskEvidence(
                    assessmentId(index),
                    activity.transactionId(),
                    "SYNTH-RULE-" + index,
                    "Synthetic source rule " + index,
                    time(500 + index),
                    new BigDecimal("0.35")));
        }
        return new CustomerSnapshot(UUID.fromString(customerId), activities, sourceRisks);
    }

    private Activity payment(int suffix, String receiverCountry) {
        return new Activity(
                transactionId(suffix),
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
                transactionId(suffix),
                Activity.ActivityType.CRYPTO,
                new BigDecimal("1.00"),
                "ETH",
                status,
                time(suffix),
                new Activity.CryptoDetails(
                        "Ethereum", "0xfrom", "0xto", "synthetic-hash-" + suffix, "Synthetic Exchange"));
    }

    private Activity card(int suffix, String status) {
        return new Activity(
                transactionId(suffix),
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

    private UUID transactionId(int suffix) {
        return UUID.fromString("dddddddd-dddd-dddd-dddd-" + String.format("%012d", suffix));
    }

    private UUID assessmentId(int suffix) {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-" + String.format("%012d", suffix));
    }

    private enum Dimension {
        CRYPTO,
        CROSS_BORDER,
        INCOMPLETE,
        SOURCE_RISK
    }
}
