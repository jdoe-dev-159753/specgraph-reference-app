package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Objects;

public record AnalysisResult(RiskLevel riskLevel, String findingsSummary, List<String> recommendations) {
    public AnalysisResult {
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(findingsSummary, "findingsSummary");
        Objects.requireNonNull(recommendations, "recommendations");
        if (findingsSummary.isBlank()) {
            throw new IllegalArgumentException("findings summary must not be blank");
        }
        recommendations = List.copyOf(recommendations);
        if (recommendations.isEmpty() || recommendations.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("analysis must contain one or more non-blank recommendations");
        }
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }
}
