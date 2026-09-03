package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class AnalysisContextBuilderTests {
    private static final UUID CUSTOMER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void denseHistoryKeepsTruthfulTotalsWhileEveryModelEvidenceFamilyIsBoundedDeterministically() {
        List<Activity> activities = IntStream.range(0, 250)
                .mapToObj(this::activity)
                .toList();
        List<RiskEvidence> sourceRisk = IntStream.range(0, 25)
                .mapToObj(index -> sourceRisk(index, activities.get(index * 10).transactionId()))
                .toList();
        List<RiskSignalEvidence> detectorEvidence = IntStream.range(0, 12)
                .mapToObj(index -> new RiskSignalEvidence(
                        "dense-detector",
                        "signal-" + index,
                        index / 10.0,
                        Map.of("index", Integer.toString(index))))
                .toList();
        List<PolicyEvidence> policyEvidence = IntStream.range(0, 7)
                .mapToObj(index -> new PolicyEvidence(
                        "synthetic-policy:dense-" + index,
                        "Synthetic ranked policy evidence " + index,
                        Map.of("rank", Integer.toString(index))))
                .toList();
        var snapshot = new CustomerSnapshot(CUSTOMER_ID, activities, sourceRisk);
        var builder = new AnalysisContextBuilder(new AnalysisContextProperties(25, 20, 8, 3));

        AnalysisEvidenceEnvelope context = builder.build(snapshot, detectorEvidence, policyEvidence);

        assertThat(context.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(context.totalActivityCount()).isEqualTo(250);
        assertThat(context.totalSourceRiskCount()).isEqualTo(25);
        assertThat(context.totalDetectorEvidenceCount()).isEqualTo(12);
        assertThat(context.totalPolicyEvidenceCount()).isEqualTo(7);
        assertThat(context.activities()).hasSize(25);
        assertThat(context.sourceRiskEvidence()).hasSize(20);
        assertThat(context.detectorEvidence()).hasSize(8);
        assertThat(context.policyEvidence()).hasSize(3);

        List<UUID> selectedRiskTransactionIds = context.sourceRiskEvidence().stream()
                .map(RiskEvidence::transactionId)
                .toList();
        assertThat(context.activities())
                .extracting(Activity::transactionId)
                .containsAll(selectedRiskTransactionIds)
                .contains(
                        new UUID(0L, 250L),
                        new UUID(0L, 249L),
                        new UUID(0L, 248L),
                        new UUID(0L, 247L),
                        new UUID(0L, 246L));
        assertThat(context.activities())
                .extracting(Activity::createdAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(context.sourceRiskEvidence())
                .extracting(RiskEvidence::assessmentId)
                .containsExactlyElementsOf(IntStream.iterate(24, index -> index >= 5, index -> index - 1)
                        .mapToObj(index -> new UUID(1L, index + 1L))
                        .toList());
        assertThat(context.detectorEvidence())
                .extracting(RiskSignalEvidence::score)
                .containsExactly(1.1, 1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4);
        assertThat(context.policyEvidence())
                .extracting(PolicyEvidence::sourceIdentity)
                .containsExactly(
                        "synthetic-policy:dense-0",
                        "synthetic-policy:dense-1",
                        "synthetic-policy:dense-2");
        assertThat(context.contextDiagnostics())
                .containsEntry("context.activities.total", "250")
                .containsEntry("context.activities.selected", "25")
                .containsEntry("context.sourceRisk.total", "25")
                .containsEntry("context.sourceRisk.selected", "20");
    }

    @Test
    void sourceRiskWithoutBackingActivityIsNotSuppliedAsCitableModelDetail() {
        Activity activity = activity(0);
        RiskEvidence linked = sourceRisk(0, activity.transactionId());
        RiskEvidence orphan = sourceRisk(1, new UUID(9L, 9L));
        var builder = new AnalysisContextBuilder(new AnalysisContextProperties(2, 2, 2, 2));

        AnalysisEvidenceEnvelope context = builder.build(
                new CustomerSnapshot(CUSTOMER_ID, List.of(activity), List.of(linked, orphan)),
                List.of(),
                List.of(new PolicyEvidence(
                        "synthetic-policy:orphan-test",
                        "Synthetic policy evidence.",
                        Map.of("rank", "0"))));

        assertThat(context.totalSourceRiskCount()).isEqualTo(2);
        assertThat(context.sourceRiskEvidence()).containsExactly(linked);
        assertThat(context.activities()).containsExactly(activity);
    }

    @Test
    void contextConfigurationCannotReserveMoreRiskFactsThanBackingActivitySlots() {
        assertThatThrownBy(() -> new AnalysisContextProperties(4, 5, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxActivities")
                .hasMessageContaining("maxSourceRiskEvidence");
    }

    @Test
    void omittedSourceDetailCannotBeCitedAsIfItHadBeenSuppliedToTheModel() {
        List<Activity> activities = IntStream.range(0, 30).mapToObj(this::activity).toList();
        var policy = new PolicyEvidence(
                "synthetic-policy:bounded",
                "Synthetic bounded policy evidence.",
                Map.of("rank", "0"));
        var builder = new AnalysisContextBuilder(new AnalysisContextProperties(5, 5, 5, 3));
        AnalysisEvidenceEnvelope context = builder.build(
                new CustomerSnapshot(CUSTOMER_ID, activities, List.of()),
                List.of(),
                List.of(policy));
        UUID omittedOldestActivity = activities.getFirst().transactionId();
        var provenance = new AnalysisModelProvenance(
                "test-backend",
                "test-model",
                "test-prompt",
                List.of(
                        new AnalysisEvidenceReference(
                                AnalysisEvidenceReference.Kind.ACTIVITY,
                                omittedOldestActivity.toString()),
                        new AnalysisEvidenceReference(
                                AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                                policy.artifactIdentity())),
                Map.of("externalTransmission", "false"));

        assertThatThrownBy(() -> AnalysisGroundingValidator.validate(context, provenance))
                .isInstanceOf(InvalidAnalysisResultException.class)
                .hasMessageContaining("unsupported ACTIVITY")
                .hasMessageContaining(omittedOldestActivity.toString());
    }

    private Activity activity(int index) {
        return new Activity(
                new UUID(0L, index + 1L),
                Activity.ActivityType.CARD,
                BigDecimal.valueOf(100L + index),
                "CHF",
                index % 2 == 0 ? "Completed" : "Declined",
                BASE_TIME.plusSeconds(index),
                new Activity.CardDetails(
                        "**** **** **** 0000",
                        "VISA",
                        "Dense Fixture Merchant",
                        "5999",
                        true,
                        "DENSE-" + index,
                        index % 2 == 0 ? null : "Synthetic decline"));
    }

    private RiskEvidence sourceRisk(int index, UUID transactionId) {
        return new RiskEvidence(
                new UUID(1L, index + 1L),
                transactionId,
                "SRC-RULE-" + index,
                "Dense source rule " + index,
                BASE_TIME.plusSeconds(index * 10L + 1L),
                BigDecimal.valueOf(index + 1L));
    }
}
