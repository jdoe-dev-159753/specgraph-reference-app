package dev.specgraph.reference.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.analysis.context")
record AnalysisContextProperties(
        int maxActivities,
        int maxSourceRiskEvidence,
        int maxDetectorEvidence,
        int maxPolicyEvidence) {

    AnalysisContextProperties {
        requirePositive(maxActivities, "maxActivities");
        requirePositive(maxSourceRiskEvidence, "maxSourceRiskEvidence");
        requirePositive(maxDetectorEvidence, "maxDetectorEvidence");
        requirePositive(maxPolicyEvidence, "maxPolicyEvidence");
        if (maxActivities < maxSourceRiskEvidence) {
            throw new IllegalArgumentException(
                    "maxActivities must be at least maxSourceRiskEvidence so selected source-risk facts can retain their backing activities");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
