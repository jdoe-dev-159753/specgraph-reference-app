package dev.specgraph.reference.analysis;

import java.util.List;

public record AnalysisResult(RiskLevel riskLevel, String findingsSummary, List<String> recommendations) {
    public AnalysisResult {
        if (riskLevel == null) {
            throw new InvalidAnalysisResultException("risk level must not be null");
        }
        if (findingsSummary == null || findingsSummary.isBlank()) {
            throw new InvalidAnalysisResultException("findings summary must not be blank");
        }
        if (findingsSummary.length() > 500) {
            throw new InvalidAnalysisResultException("findings summary must not exceed 500 characters");
        }
        if (recommendations == null
                || recommendations.isEmpty()
                || recommendations.stream().anyMatch(recommendation -> recommendation == null || recommendation.isBlank())) {
            throw new InvalidAnalysisResultException(
                    "analysis must contain one or more non-blank recommendations");
        }
        if (recommendations.size() > 3) {
            throw new InvalidAnalysisResultException("analysis must not contain more than 3 recommendations");
        }
        if (recommendations.stream().anyMatch(recommendation -> recommendation.length() > 140)) {
            throw new InvalidAnalysisResultException("recommendations must not exceed 140 characters");
        }
        recommendations = List.copyOf(recommendations);
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }
}
