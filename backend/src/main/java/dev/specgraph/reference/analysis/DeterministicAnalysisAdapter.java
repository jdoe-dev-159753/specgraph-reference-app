package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DeterministicAnalysisAdapter implements AnalysisModelPort {
    @Override
    public AnalysisResult analyze(CustomerSnapshot snapshot, List<PolicyEvidence> evidence) {
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

        return new AnalysisResult(riskLevel, findings, recommendations);
    }
}
