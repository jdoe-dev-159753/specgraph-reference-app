package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.identity.OperatorId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-001")
final class AnalysisServiceTests {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OperatorId OPERATOR_ID = new OperatorId("operator-test");
    private static final CustomerSnapshot SNAPSHOT = new CustomerSnapshot(CUSTOMER_ID, List.of(), List.of());
    private static final PolicyEvidence POLICY = new PolicyEvidence(
            "synthetic-policy:test",
            "Synthetic test policy evidence.",
            Map.of("adapter", "test"));
    private static final RiskSignalEvidence DETECTOR_SIGNAL = new RiskSignalEvidence(
            "test-detector:v1",
            "temporal-volume-shift",
            0.72,
            Map.of("featureSchema", "test-v1"));
    private static final AnalysisModelProvenance MODEL_PROVENANCE = new AnalysisModelProvenance(
            "test-backend",
            "test-model-v1",
            Map.of("externalTransmission", "false"));
    private static final AnalysisResult RESULT = new AnalysisResult(
            AnalysisResult.RiskLevel.MEDIUM,
            "Structured deterministic finding.",
            List.of("Review the source evidence."));

    @Test
    void composesSourceDetectorAndPolicyEvidenceBeforePersistingValidatedOutput() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var receivedEvidence = new AtomicReference<AnalysisEvidenceEnvelope>();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(DETECTOR_SIGNAL),
                snapshot -> List.of(POLICY),
                evidence -> {
                    receivedEvidence.set(evidence);
                    return output(RESULT);
                },
                history);

        AnalysisHistoryEntry completed = service.analyze(CUSTOMER_ID, OPERATOR_ID);

        assertThat(receivedEvidence.get().snapshot()).isEqualTo(SNAPSHOT);
        assertThat(receivedEvidence.get().detectorEvidence()).containsExactly(DETECTOR_SIGNAL);
        assertThat(receivedEvidence.get().policyEvidence()).containsExactly(POLICY);
        assertThat(completed.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(completed.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(completed.result()).isEqualTo(RESULT);
        assertThat(completed.evidenceProvenance()).containsExactly(POLICY);
        assertThat(completed.detectorProvenance()).containsExactly(DETECTOR_SIGNAL);
        assertThat(completed.modelProvenance()).isEqualTo(MODEL_PROVENANCE);
        assertThat(history.listByCustomer(CUSTOMER_ID)).containsExactly(completed);
    }

    @Test
    void detectorFailureStopsBeforeGroundingModelAndHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var groundingCalls = new AtomicInteger();
        var modelCalls = new AtomicInteger();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> {
                    throw new IllegalStateException("detector unavailable");
                },
                snapshot -> {
                    groundingCalls.incrementAndGet();
                    return List.of(POLICY);
                },
                evidence -> {
                    modelCalls.incrementAndGet();
                    return output(RESULT);
                },
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.DETECTOR_FAILURE));
        assertThat(groundingCalls).hasValue(0);
        assertThat(modelCalls).hasValue(0);
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void insufficientGroundingDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(),
                evidence -> output(RESULT),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(AnalysisFailureException.Reason.INSUFFICIENT_GROUNDING));
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void structurallyEmptyGroundingDoesNotInvokeModelOrCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var modelCalls = new AtomicInteger();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(new PolicyEvidence("   ", "Synthetic test policy evidence.", Map.of())),
                evidence -> {
                    modelCalls.incrementAndGet();
                    return output(RESULT);
                },
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.GROUNDING_FAILURE);
                    assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
                });
        assertThat(modelCalls).hasValue(0);
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();

        assertThatThrownBy(() -> new PolicyEvidence("synthetic-policy:test", "   ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void modelFailureDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> {
                    throw new IllegalStateException("model unavailable");
                },
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.MODEL_FAILURE));
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void invalidNullModelOutputDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> null,
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT));
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void invalidStructuredModelResultDoesNotBecomeModelFailureOrCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> new AnalysisModelOutput(
                        new AnalysisResult(
                                AnalysisResult.RiskLevel.MEDIUM,
                                "   ",
                                List.of("Review the source evidence.")),
                        MODEL_PROVENANCE),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT);
                    assertThat(exception.getCause()).isInstanceOf(InvalidAnalysisResultException.class);
                });
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void persistenceFailureIsExplicitAndCannotReturnFalseCompletedState() {
        AnalysisHistoryPort failingHistory = new AnalysisHistoryPort() {
            @Override
            public AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
                throw new IllegalStateException("database unavailable");
            }

            @Override
            public List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
                return List.of();
            }

            @Override
            public Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId) {
                return Optional.empty();
            }
        };
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> output(RESULT),
                failingHistory);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(AnalysisFailureException.Reason.PERSISTENCE_FAILURE));
    }

    private AnalysisModelOutput output(AnalysisResult result) {
        return new AnalysisModelOutput(result, MODEL_PROVENANCE);
    }

    private AnalysisService service(
            CustomerActivityPort customerActivity,
            RiskSignalDetectorPort detector,
            PolicyKnowledgePort policyKnowledge,
            AnalysisModelPort model,
            AnalysisHistoryPort history) {
        return new AnalysisService(customerActivity, detector, policyKnowledge, model, history);
    }
}
