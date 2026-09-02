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
    private final RiskSignalDetectorPort riskSignalDetector;
    private final PolicyKnowledgePort policyKnowledge;
    private final AnalysisModelPort analysisModel;
    private final AnalysisHistoryPort analysisHistory;

    AnalysisService(
            CustomerActivityPort customerActivity,
            RiskSignalDetectorPort riskSignalDetector,
            PolicyKnowledgePort policyKnowledge,
            AnalysisModelPort analysisModel,
            AnalysisHistoryPort analysisHistory) {
        this.customerActivity = customerActivity;
        this.riskSignalDetector = riskSignalDetector;
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

        List<RiskSignalEvidence> detectorEvidence;
        try {
            detectorEvidence = List.copyOf(riskSignalDetector.detect(snapshot));
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.DETECTOR_FAILURE,
                    "Risk-signal detector execution failed",
                    exception);
        }

        List<PolicyEvidence> policyEvidence;
        try {
            policyEvidence = List.copyOf(policyKnowledge.retrieveRelevant(snapshot));
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.GROUNDING_FAILURE,
                    "Policy evidence retrieval failed",
                    exception);
        }
        if (policyEvidence.isEmpty()) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.INSUFFICIENT_GROUNDING,
                    "No relevant policy evidence was available for the analysis");
        }

        AnalysisEvidenceEnvelope evidence = new AnalysisEvidenceEnvelope(
                snapshot,
                detectorEvidence,
                policyEvidence);

        AnalysisModelOutput output;
        try {
            output = analysisModel.analyze(evidence);
        } catch (InvalidAnalysisResultException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.INVALID_RESULT,
                    "Analysis model returned an invalid structured result",
                    exception);
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.MODEL_FAILURE,
                    "Analysis model execution failed",
                    exception);
        }
        if (output == null) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.INVALID_RESULT,
                    "Analysis model returned no structured output");
        }

        AnalysisHistoryCreateCommand command = new AnalysisHistoryCreateCommand(
                customerId,
                operatorId,
                Instant.now(),
                output.result(),
                policyEvidence,
                detectorEvidence,
                output.provenance());
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
