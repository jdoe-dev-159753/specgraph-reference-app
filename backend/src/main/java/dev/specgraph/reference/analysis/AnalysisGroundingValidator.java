package dev.specgraph.reference.analysis;

import java.util.HashSet;
import java.util.Set;

final class AnalysisGroundingValidator {
    private AnalysisGroundingValidator() {}

    static void validate(AnalysisEvidenceEnvelope evidence, AnalysisModelProvenance provenance) {
        var references = provenance.evidenceReferences();
        if (references.isEmpty()) {
            throw new InvalidAnalysisResultException(
                    "analysis model provenance must cite evidence supplied to the model");
        }

        if (new HashSet<>(references).size() != references.size()) {
            throw new InvalidAnalysisResultException(
                    "analysis model provenance must not contain duplicate evidence references");
        }

        Set<String> activityIds = evidence.activities().stream()
                .map(activity -> activity.transactionId().toString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> sourceRiskIds = evidence.sourceRiskEvidence().stream()
                .map(risk -> risk.assessmentId().toString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> detectorIds = evidence.detectorEvidence().stream()
                .map(AnalysisPipelineArtifact::artifactIdentity)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> policyIds = evidence.policyEvidence().stream()
                .map(AnalysisPipelineArtifact::artifactIdentity)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        boolean citesSource = false;
        boolean citesPolicy = false;
        for (AnalysisEvidenceReference reference : references) {
            boolean present = switch (reference.kind()) {
                case ACTIVITY -> {
                    citesSource = true;
                    yield activityIds.contains(reference.evidenceIdentity());
                }
                case SOURCE_RISK -> {
                    citesSource = true;
                    yield sourceRiskIds.contains(reference.evidenceIdentity());
                }
                case DETECTOR_SIGNAL -> detectorIds.contains(reference.evidenceIdentity());
                case POLICY_RETRIEVAL -> {
                    citesPolicy = true;
                    yield policyIds.contains(reference.evidenceIdentity());
                }
            };
            if (!present) {
                throw new InvalidAnalysisResultException(
                        "analysis model cited unsupported " + reference.kind()
                                + " evidence " + reference.evidenceIdentity());
            }
        }

        if (!citesSource) {
            throw new InvalidAnalysisResultException(
                    "analysis model provenance must cite at least one supplied source activity or source-risk fact");
        }
        if (!citesPolicy) {
            throw new InvalidAnalysisResultException(
                    "analysis model provenance must cite at least one retrieved policy artifact");
        }
    }
}
