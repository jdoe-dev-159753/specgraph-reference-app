package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.identity.OperatorId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-001")
@Tag("VFY-FAILURE-PATHS-001")
/**
 * Failure-injection specification for orchestration order, grounding validation and no-false-success
 * behavior. In-memory doubles isolate application semantics from transport and persistence frameworks.
 */
final class AnalysisServiceTests {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTIVITY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final OperatorId OPERATOR_ID = new OperatorId("operator-test");
    private static final Activity ACTIVITY = new Activity(
            ACTIVITY_ID,
            Activity.ActivityType.CARD,
            new BigDecimal("125.00"),
            "CHF",
            "Completed",
            Instant.parse("2026-08-28T09:15:00Z"),
            new Activity.CardDetails(
                    "411111******1111",
                    "VISA",
                    "Synthetic Merchant",
                    "5732",
                    false,
                    "AUTH-TEST",
                    null));
    private static final CustomerSnapshot SNAPSHOT = new CustomerSnapshot(CUSTOMER_ID, List.of(ACTIVITY), List.of());
    private static final PolicyEvidence POLICY = new PolicyEvidence(
            "synthetic-policy:test",
            "Synthetic test policy evidence.",
            Map.of("adapter", "test"));
    private static final RiskSignalEvidence DETECTOR_SIGNAL = new RiskSignalEvidence(
            "test-detector:v1",
            "temporal-volume-shift",
            0.72,
            Map.of("featureSchema", "test-v1"));
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
                    return output(evidence, RESULT);
                },
                history);

        AnalysisHistoryEntry completed = service.analyze(CUSTOMER_ID, OPERATOR_ID);

        assertThat(receivedEvidence.get().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(receivedEvidence.get().totalActivityCount()).isEqualTo(1);
        assertThat(receivedEvidence.get().totalSourceRiskCount()).isZero();
        assertThat(receivedEvidence.get().activities()).containsExactly(ACTIVITY);
        assertThat(receivedEvidence.get().sourceRiskEvidence()).isEmpty();
        assertThat(receivedEvidence.get().detectorEvidence()).containsExactly(DETECTOR_SIGNAL);
        assertThat(receivedEvidence.get().policyEvidence()).containsExactly(POLICY);
        assertThat(completed.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(completed.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(completed.result()).isEqualTo(RESULT);
        assertThat(completed.evidenceProvenance()).containsExactly(POLICY);
        assertThat(completed.detectorProvenance()).containsExactly(DETECTOR_SIGNAL);
        assertThat(completed.modelProvenance().backendIdentity()).isEqualTo("test-backend");
        assertThat(completed.modelProvenance().modelIdentity()).isEqualTo("test-model-v1");
        assertThat(completed.modelProvenance().promptIdentity()).isEqualTo("test-prompt-v1");
        assertThat(completed.modelProvenance().evidenceReferences()).containsExactly(
                new AnalysisEvidenceReference(AnalysisEvidenceReference.Kind.ACTIVITY, ACTIVITY_ID.toString()),
                new AnalysisEvidenceReference(
                        AnalysisEvidenceReference.Kind.DETECTOR_SIGNAL,
                        DETECTOR_SIGNAL.artifactIdentity()),
                new AnalysisEvidenceReference(
                        AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                        POLICY.artifactIdentity()));
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
                    return output(evidence, RESULT);
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
                evidence -> output(evidence, RESULT),
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
                    return output(evidence, RESULT);
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
                        provenance(evidence)),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT);
                    assertThat(exception.getCause()).isInstanceOf(InvalidAnalysisResultException.class);
                });
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void fabricatedEvidenceReferenceIsRejectedBeforePersistence() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> new AnalysisModelOutput(
                        RESULT,
                        new AnalysisModelProvenance(
                                "test-backend",
                                "test-model-v1",
                                "test-prompt-v1",
                                List.of(
                                        new AnalysisEvidenceReference(
                                                AnalysisEvidenceReference.Kind.ACTIVITY,
                                                ACTIVITY_ID.toString()),
                                        new AnalysisEvidenceReference(
                                                AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                                                "synthetic-policy:invented")),
                                Map.of("externalTransmission", "false"))),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT);
                    assertThat(exception.getCause()).isInstanceOf(InvalidAnalysisResultException.class);
                    assertThat(exception.getCause().getMessage()).contains("unsupported POLICY_RETRIEVAL");
                });
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void modelCannotClaimGroundedCompletionWithoutSourceReference() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> new AnalysisModelOutput(
                        RESULT,
                        new AnalysisModelProvenance(
                                "test-backend",
                                "test-model-v1",
                                "test-prompt-v1",
                                List.of(new AnalysisEvidenceReference(
                                        AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                                        POLICY.artifactIdentity())),
                                Map.of("externalTransmission", "false"))),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT);
                    assertThat(exception.getCause().getMessage()).contains("source activity or source-risk fact");
                });
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void modelCannotClaimGroundedCompletionWithoutPolicyReference() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                snapshot -> List.of(POLICY),
                evidence -> new AnalysisModelOutput(
                        RESULT,
                        new AnalysisModelProvenance(
                                "test-backend",
                                "test-model-v1",
                                "test-prompt-v1",
                                List.of(new AnalysisEvidenceReference(
                                        AnalysisEvidenceReference.Kind.ACTIVITY,
                                        ACTIVITY_ID.toString())),
                                Map.of("externalTransmission", "false"))),
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.INVALID_RESULT);
                    assertThat(exception.getCause().getMessage()).contains("policy artifact");
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
                evidence -> output(evidence, RESULT),
                failingHistory);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(AnalysisFailureException.Reason.PERSISTENCE_FAILURE));
    }

    private AnalysisModelOutput output(AnalysisEvidenceEnvelope evidence, AnalysisResult result) {
        return new AnalysisModelOutput(result, provenance(evidence));
    }

    /** Cites every supplied family to produce the valid control provenance used by failure variants. */
    private AnalysisModelProvenance provenance(AnalysisEvidenceEnvelope evidence) {
        List<AnalysisEvidenceReference> references = new ArrayList<>();
        evidence.activities().forEach(activity -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.ACTIVITY,
                activity.transactionId().toString())));
        evidence.sourceRiskEvidence().forEach(risk -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.SOURCE_RISK,
                risk.assessmentId().toString())));
        evidence.detectorEvidence().forEach(signal -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.DETECTOR_SIGNAL,
                signal.artifactIdentity())));
        evidence.policyEvidence().forEach(policy -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                policy.artifactIdentity())));
        return new AnalysisModelProvenance(
                "test-backend",
                "test-model-v1",
                "test-prompt-v1",
                references,
                Map.of("externalTransmission", "false"));
    }

    private AnalysisService service(
            CustomerActivityPort customerActivity,
            RiskSignalDetectorPort detector,
            PolicyKnowledgePort policyKnowledge,
            AnalysisModelPort model,
            AnalysisHistoryPort history) {
        var contextBuilder = new AnalysisContextBuilder(new AnalysisContextProperties(25, 20, 8, 3));
        return new AnalysisService(customerActivity, detector, policyKnowledge, contextBuilder, model, history);
    }
}
