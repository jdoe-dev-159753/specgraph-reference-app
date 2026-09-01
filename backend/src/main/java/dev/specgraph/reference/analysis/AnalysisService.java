package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class AnalysisService implements AnalysisUseCase {
    private final CustomerActivityPort customerActivity;
    private final PolicyKnowledgePort policyKnowledge;
    private final AnalysisModelPort analysisModel;
    private final AnalysisHistoryPort analysisHistory;

    AnalysisService(
            CustomerActivityPort customerActivity,
            PolicyKnowledgePort policyKnowledge,
            AnalysisModelPort analysisModel,
            AnalysisHistoryPort analysisHistory) {
        this.customerActivity = customerActivity;
        this.policyKnowledge = policyKnowledge;
        this.analysisModel = analysisModel;
        this.analysisHistory = analysisHistory;
    }

    @Override
    public AnalysisHistoryEntry analyze(UUID customerId, OperatorId operatorId) {
        CustomerSnapshot snapshot = customerActivity.loadSnapshot(customerId)
                .orElseThrow(() -> new AnalysisFailureException(
                        AnalysisFailureException.Reason.CUSTOMER_NOT_FOUND,
                        "Customer " + customerId + " was not found"));

        List<PolicyEvidence> evidence;
        try {
            evidence = List.copyOf(policyKnowledge.retrieveRelevant(snapshot));
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.GROUNDING_FAILURE,
                    "Policy evidence retrieval failed",
                    exception);
        }
        if (evidence.isEmpty()) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.INSUFFICIENT_GROUNDING,
                    "No relevant policy evidence was available for the analysis");
        }

        AnalysisResult result;
        try {
            result = analysisModel.analyze(snapshot, evidence);
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.MODEL_FAILURE,
                    "Analysis model execution failed",
                    exception);
        }
        if (result == null) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.INVALID_RESULT,
                    "Analysis model returned no structured result");
        }

        AnalysisHistoryCreateCommand command = new AnalysisHistoryCreateCommand(
                customerId,
                operatorId,
                Instant.now(),
                result,
                evidence);
        try {
            return analysisHistory.persist(command);
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.PERSISTENCE_FAILURE,
                    "Completed analysis could not be persisted",
                    exception);
        }
    }

    @Override
    public List<AnalysisHistoryEntry> listHistory(UUID customerId) {
        return analysisHistory.listByCustomer(customerId);
    }

    @Override
    public Optional<AnalysisHistoryEntry> findHistory(UUID customerId, UUID analysisId) {
        return analysisHistory.findByCustomerAndId(customerId, analysisId);
    }
}
