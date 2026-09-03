package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.risk.RiskEvidence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application-owned bounded evidence boundary supplied to advisory analysis models.
 *
 * Full-history aggregate counts remain truthful while detail collections are selected and bounded
 * before any model adapter call. Source facts, derived detector signals and retrieved policy evidence
 * stay distinguishable and only supplied details are eligible for model grounding references.
 */
public record AnalysisEvidenceEnvelope(
        UUID customerId,
        int totalActivityCount,
        int totalSourceRiskCount,
        int totalDetectorEvidenceCount,
        int totalPolicyEvidenceCount,
        List<Activity> activities,
        List<RiskEvidence> sourceRiskEvidence,
        List<RiskSignalEvidence> detectorEvidence,
        List<PolicyEvidence> policyEvidence) {
    public AnalysisEvidenceEnvelope {
        Objects.requireNonNull(customerId, "customerId");
        activities = List.copyOf(Objects.requireNonNull(activities, "activities"));
        sourceRiskEvidence = List.copyOf(Objects.requireNonNull(sourceRiskEvidence, "sourceRiskEvidence"));
        detectorEvidence = List.copyOf(Objects.requireNonNull(detectorEvidence, "detectorEvidence"));
        policyEvidence = List.copyOf(Objects.requireNonNull(policyEvidence, "policyEvidence"));

        requireTotalAtLeastSelected(totalActivityCount, activities.size(), "totalActivityCount");
        requireTotalAtLeastSelected(totalSourceRiskCount, sourceRiskEvidence.size(), "totalSourceRiskCount");
        requireTotalAtLeastSelected(totalDetectorEvidenceCount, detectorEvidence.size(), "totalDetectorEvidenceCount");
        requireTotalAtLeastSelected(totalPolicyEvidenceCount, policyEvidence.size(), "totalPolicyEvidenceCount");
    }

    Map<String, String> contextDiagnostics() {
        Map<String, String> diagnostics = new LinkedHashMap<>();
        diagnostics.put("context.activities.total", Integer.toString(totalActivityCount));
        diagnostics.put("context.activities.selected", Integer.toString(activities.size()));
        diagnostics.put("context.sourceRisk.total", Integer.toString(totalSourceRiskCount));
        diagnostics.put("context.sourceRisk.selected", Integer.toString(sourceRiskEvidence.size()));
        diagnostics.put("context.detector.total", Integer.toString(totalDetectorEvidenceCount));
        diagnostics.put("context.detector.selected", Integer.toString(detectorEvidence.size()));
        diagnostics.put("context.policy.total", Integer.toString(totalPolicyEvidenceCount));
        diagnostics.put("context.policy.selected", Integer.toString(policyEvidence.size()));
        return Map.copyOf(diagnostics);
    }

    private static void requireTotalAtLeastSelected(int total, int selected, String field) {
        if (total < 0 || total < selected) {
            throw new IllegalArgumentException(field + " must be non-negative and at least the selected count");
        }
    }
}
