package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class DeterministicAnalysisAdapter implements AnalysisModelPort {
    private static final AnalysisModelProvenance PROVENANCE = new AnalysisModelProvenance(
            "deterministic",
            "r3-offline-baseline-v1",
            Map.of("externalTransmission", "false"));

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
                PROVENANCE);
    }
}
