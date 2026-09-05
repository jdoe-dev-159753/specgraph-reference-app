package dev.specgraph.reference.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Independent detail limits for each evidence family admitted to Stage 3.
 * The activity limit cannot be smaller than the source-risk limit because every selected source
 * risk fact must retain its backing transaction in the bounded envelope.
 */
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
