package dev.specgraph.reference.analysis;

import java.util.ArrayList;
import java.util.List;

final class AnalysisEvidenceReferences {
    private AnalysisEvidenceReferences() {}

    static List<AnalysisEvidenceReference> from(AnalysisEvidenceEnvelope evidence) {
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
        return List.copyOf(references);
    }
}
