package dev.specgraph.reference.analysis;

import java.util.List;

/**
 * Structured, operator-facing assessment produced by an analysis model.
 *
 * <p>A result always has a risk level, a non-blank findings narrative and at least one non-blank
 * recommendation. It is advisory output rather than a calibrated probability or automated
 * decision, and its recommendations are defensively copied.
 */
public record AnalysisResult(RiskLevel riskLevel, String findingsSummary, List<String> recommendations) {
    /** Enforces the bounded structured-output shape independently of any model provider. */
    public AnalysisResult {
        if (riskLevel == null) {
            throw new InvalidAnalysisResultException("risk level must not be null");
        }
        if (findingsSummary == null || findingsSummary.isBlank()) {
            throw new InvalidAnalysisResultException("findings summary must not be blank");
        }
        if (recommendations == null
                || recommendations.isEmpty()
                || recommendations.stream().anyMatch(recommendation -> recommendation == null || recommendation.isBlank())) {
            throw new InvalidAnalysisResultException(
                    "analysis must contain one or more non-blank recommendations");
        }
        recommendations = List.copyOf(recommendations);
    }

    /** Ordinal review classification; it is not a calibrated numeric probability. */
    public enum RiskLevel { LOW, MEDIUM, HIGH }
}
