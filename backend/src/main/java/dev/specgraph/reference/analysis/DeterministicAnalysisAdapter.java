package dev.specgraph.reference.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Offline Stage-3 baseline that produces reproducible structured output from aggregate source-risk
 * counts. It exercises the same grounding and provenance contract as live models while explicitly
 * avoiding external transmission and institutional-policy claims.
 */
@Component
class DeterministicAnalysisAdapter implements AnalysisModelPort {
    private static final String BACKEND_IDENTITY = "deterministic";
    private static final String MODEL_IDENTITY = "r3-offline-baseline-v1";
    private static final String PROMPT_IDENTITY = "grounded-analysis-v1";

    /** Produces an offline comparison result while retaining the same evidence-reference contract. */
    @Override
    public AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence) {
        int sourceRiskSignals = evidence.totalSourceRiskCount();
        AnalysisResult.RiskLevel riskLevel = sourceRiskSignals == 0
                ? AnalysisResult.RiskLevel.LOW
                : sourceRiskSignals <= 2
                        ? AnalysisResult.RiskLevel.MEDIUM
                        : AnalysisResult.RiskLevel.HIGH;

        String findings = "Deterministic offline baseline observed " + sourceRiskSignals
                + " persisted source risk signal(s) across " + evidence.totalActivityCount()
                + " activity record(s). This synthetic analysis supports review and is not institutional risk policy.";

        List<String> recommendations = sourceRiskSignals == 0
                ? List.of(
                        "Continue routine review of the persisted activity timeline.",
                        "Confirm the activity context against the retrieved synthetic policy evidence.")
                : List.of(
                        "Review the persisted source risk signals with the associated transactions.",
                        "Confirm the activity context against the retrieved synthetic policy evidence before escalation.");

        Map<String, String> metadata = new LinkedHashMap<>(evidence.contextDiagnostics());
        metadata.put("externalTransmission", "false");

        return new AnalysisModelOutput(
                new AnalysisResult(riskLevel, findings, recommendations),
                new AnalysisModelProvenance(
                        BACKEND_IDENTITY,
                        MODEL_IDENTITY,
                        PROMPT_IDENTITY,
                        AnalysisEvidenceReferences.from(evidence),
                        Map.copyOf(metadata)));
    }
}
