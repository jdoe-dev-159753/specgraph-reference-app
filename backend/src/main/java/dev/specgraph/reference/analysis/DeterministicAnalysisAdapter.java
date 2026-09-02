package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class DeterministicAnalysisAdapter implements AnalysisModelPort {
    private static final String BACKEND_IDENTITY = "deterministic";
    private static final String MODEL_IDENTITY = "r3-offline-baseline-v1";
    private static final String PROMPT_IDENTITY = "grounded-analysis-v1";

    @Override
    public AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence) {
        var snapshot = evidence.snapshot();
        int sourceRiskSignals = snapshot.riskEvidence().size();
        AnalysisResult.RiskLevel riskLevel = sourceRiskSignals == 0
                ? AnalysisResult.RiskLevel.LOW
                : sourceRiskSignals <= 2
                        ? AnalysisResult.RiskLevel.MEDIUM
                        : AnalysisResult.RiskLevel.HIGH;

        String findings = "Deterministic offline baseline observed " + sourceRiskSignals
                + " persisted source risk signal(s) across " + snapshot.activities().size()
                + " activity record(s). This synthetic analysis supports review and is not institutional risk policy.";

        List<String> recommendations = sourceRiskSignals == 0
                ? List.of(
                        "Continue routine review of the persisted activity timeline.",
                        "Confirm the activity context against the retrieved synthetic policy evidence.")
                : List.of(
                        "Review the persisted source risk signals with the associated transactions.",
                        "Confirm the activity context against the retrieved synthetic policy evidence before escalation.");

        return new AnalysisModelOutput(
                new AnalysisResult(riskLevel, findings, recommendations),
                new AnalysisModelProvenance(
                        BACKEND_IDENTITY,
                        MODEL_IDENTITY,
                        PROMPT_IDENTITY,
                        evidenceReferences(evidence),
                        Map.of("externalTransmission", "false")));
    }

    private List<AnalysisEvidenceReference> evidenceReferences(AnalysisEvidenceEnvelope evidence) {
        List<AnalysisEvidenceReference> references = new ArrayList<>();
        evidence.snapshot().activities().forEach(activity -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.ACTIVITY,
                activity.transactionId().toString())));
        evidence.snapshot().riskEvidence().forEach(risk -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.SOURCE_RISK,
                risk.assessmentId().toString())));
        evidence.detectorEvidence().forEach(signal -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.DETECTOR_SIGNAL,
                signal.artifactIdentity())));
        evidence.policyEvidence().forEach(policy -> references.add(new AnalysisEvidenceReference(
                AnalysisEvidenceReference.Kind.POLICY_RETRIEVAL,
                policy.artifactIdentity())));
        return List.copyOf(references);
    }
}
