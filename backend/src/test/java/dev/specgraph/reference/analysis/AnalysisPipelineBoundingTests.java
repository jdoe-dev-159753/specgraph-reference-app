package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.identity.OperatorId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class AnalysisPipelineBoundingTests {
    @Test
    void detectorAndRetrievalSeeCompleteHistoryWhileModelPortSeesOnlyBoundedProjection() {
        UUID customerId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        List<Activity> activities = IntStream.range(0, 250)
                .mapToObj(index -> new Activity(
                        new UUID(2L, index + 1L),
                        Activity.ActivityType.CARD,
                        BigDecimal.valueOf(index + 1L),
                        "CHF",
                        "Completed",
                        Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index),
                        new Activity.CardDetails(
                                "**** **** **** 0000",
                                "VISA",
                                "Dense Pipeline Merchant",
                                "5999",
                                true,
                                "PIPELINE-" + index,
                                null)))
                .toList();
        var snapshot = new CustomerSnapshot(customerId, activities, List.of());
        var detectorObservedActivities = new AtomicInteger();
        var retrievalObservedActivities = new AtomicInteger();
        var modelObservedContext = new AtomicReference<AnalysisEvidenceEnvelope>();
        var detector = new RiskSignalEvidence(
                "pipeline-detector",
                "dense-history",
                0.5,
                Map.of("fixture", "250"));
        var policy = new PolicyEvidence(
                "synthetic-policy:pipeline-bounding",
                "Synthetic policy evidence for bounded pipeline verification.",
                Map.of("rank", "0"));
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = new AnalysisService(
                ignored -> Optional.of(snapshot),
                observed -> {
                    detectorObservedActivities.set(observed.activities().size());
                    return List.of(detector);
                },
                observed -> {
                    retrievalObservedActivities.set(observed.activities().size());
                    return List.of(policy);
                },
                new AnalysisContextBuilder(new AnalysisContextProperties(25, 20, 8, 3)),
                evidence -> {
                    modelObservedContext.set(evidence);
                    return new AnalysisModelOutput(
                            new AnalysisResult(
                                    AnalysisResult.RiskLevel.MEDIUM,
                                    "Bounded synthetic pipeline finding.",
                                    List.of("Review supplied evidence.")),
                            new AnalysisModelProvenance(
                                    "test-backend",
                                    "test-model",
                                    "test-prompt",
                                    AnalysisEvidenceReferences.from(evidence),
                                    Map.of("externalTransmission", "false")));
                },
                history);

        service.analyze(customerId, new OperatorId("operator-bounded-pipeline"));

        assertThat(detectorObservedActivities).hasValue(250);
        assertThat(retrievalObservedActivities).hasValue(250);
        assertThat(modelObservedContext.get().totalActivityCount()).isEqualTo(250);
        assertThat(modelObservedContext.get().activities()).hasSize(25);
        assertThat(modelObservedContext.get().detectorEvidence()).containsExactly(detector);
        assertThat(modelObservedContext.get().policyEvidence()).containsExactly(policy);
    }
}
