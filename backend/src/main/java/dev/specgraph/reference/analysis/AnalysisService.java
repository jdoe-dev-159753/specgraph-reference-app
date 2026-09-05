package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single application orchestrator for the staged analysis flow.
 * It keeps source loading, detector inference, policy retrieval, bounded model synthesis,
 * grounding validation and persistence ordered, translating failures without fabricating success.
 */
@Service
final class AnalysisService implements AnalysisUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);

    private final CustomerActivityPort customerActivity;
    private final RiskSignalDetectorPort riskSignalDetector;
    private final PolicyKnowledgePort policyKnowledge;
    private final AnalysisContextBuilder contextBuilder;
    private final AnalysisModelPort analysisModel;
    private final AnalysisHistoryPort analysisHistory;

    AnalysisService(
            CustomerActivityPort customerActivity,
            RiskSignalDetectorPort riskSignalDetector,
            PolicyKnowledgePort policyKnowledge,
            AnalysisContextBuilder contextBuilder,
            AnalysisModelPort analysisModel,
            AnalysisHistoryPort analysisHistory) {
        this.customerActivity = customerActivity;
        this.riskSignalDetector = riskSignalDetector;
        this.policyKnowledge = policyKnowledge;
        this.contextBuilder = contextBuilder;
        this.analysisModel = analysisModel;
        this.analysisHistory = analysisHistory;
    }

    /**
     * Executes the fail-closed pipeline and persists only a grounded, structurally valid model result.
     * Stage failures retain distinct reasons so the HTTP boundary can report safe operational outcomes.
     */
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

        AnalysisEvidenceEnvelope evidence = contextBuilder.build(snapshot, detectorEvidence, policyEvidence);

        AnalysisModelOutput output;
        try {
            output = analysisModel.analyze(evidence);
        } catch (InvalidAnalysisResultException exception) {
            LOGGER.warn(
                    "Analysis model output failed the bounded structured-result contract: {}",
                    exception.getMessage());
            throw invalidResult("Analysis model returned an invalid structured result", exception);
        } catch (RuntimeException exception) {
            throw new AnalysisFailureException(
                    AnalysisFailureException.Reason.MODEL_FAILURE,
                    "Analysis model execution failed",
                    exception);
        }
        if (output == null) {
            throw invalidResult("Analysis model returned no structured output", null);
        }

        try {
            AnalysisGroundingValidator.validate(evidence, output.provenance());
        } catch (InvalidAnalysisResultException exception) {
            throw invalidResult("Analysis model returned unsupported or incomplete grounding provenance", exception);
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
    public AnalysisHistoryPage listHistory(UUID customerId, AnalysisHistoryQuery query) {
        return analysisHistory.pageByCustomer(customerId, query);
    }

    @Override
    public Optional<AnalysisHistoryEntry> findHistory(UUID customerId, UUID analysisId) {
        return analysisHistory.findByCustomerAndId(customerId, analysisId);
    }

    private static AnalysisFailureException invalidResult(String message, RuntimeException cause) {
        if (cause == null) {
            return new AnalysisFailureException(AnalysisFailureException.Reason.INVALID_RESULT, message);
        }
        return new AnalysisFailureException(AnalysisFailureException.Reason.INVALID_RESULT, message, cause);
    }
}
