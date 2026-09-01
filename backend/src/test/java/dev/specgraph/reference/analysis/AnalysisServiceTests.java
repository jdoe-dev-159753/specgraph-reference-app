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
    private static final AnalysisResult RESULT = new AnalysisResult(
            AnalysisResult.RiskLevel.MEDIUM,
            "Structured deterministic finding.",
            List.of("Review the source evidence."));

    @Test
    void completesOnlyAfterValidatedResultIsPersistedWithOperatorAndEvidence() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(POLICY),
                (snapshot, evidence) -> RESULT,
                history);

        AnalysisHistoryEntry completed = service.analyze(CUSTOMER_ID, OPERATOR_ID);

        assertThat(completed.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(completed.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(completed.result()).isEqualTo(RESULT);
        assertThat(completed.evidenceProvenance()).containsExactly(POLICY);
        assertThat(history.listByCustomer(CUSTOMER_ID)).containsExactly(completed);
    }

    @Test
    void insufficientGroundingDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(),
                (snapshot, evidence) -> RESULT,
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(AnalysisFailureException.Reason.INSUFFICIENT_GROUNDING));
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void modelFailureDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(POLICY),
                (snapshot, evidence) -> {
                    throw new IllegalStateException("model unavailable");
                },
                history);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AnalysisFailureException.Reason.MODEL_FAILURE));
        assertThat(history.listByCustomer(CUSTOMER_ID)).isEmpty();
    }

    @Test
    void invalidNullModelResultDoesNotCreateHistory() {
        var history = new InMemoryAnalysisHistoryAdapter();
        var service = service(
                customer -> Optional.of(SNAPSHOT),
                snapshot -> List.of(POLICY),
                (snapshot, evidence) -> null,
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
                snapshot -> List.of(POLICY),
                (snapshot, evidence) -> new AnalysisResult(
                        AnalysisResult.RiskLevel.MEDIUM,
                        "   ",
                        List.of("Review the source evidence.")),
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
                snapshot -> List.of(POLICY),
                (snapshot, evidence) -> RESULT,
                failingHistory);

        assertThatThrownBy(() -> service.analyze(CUSTOMER_ID, OPERATOR_ID))
                .isInstanceOfSatisfying(AnalysisFailureException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(AnalysisFailureException.Reason.PERSISTENCE_FAILURE));
    }

    private AnalysisService service(
            CustomerActivityPort customerActivity,
            PolicyKnowledgePort policyKnowledge,
            AnalysisModelPort model,
            AnalysisHistoryPort history) {
        return new AnalysisService(customerActivity, policyKnowledge, model, history);
    }
}
