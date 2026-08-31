package dev.specgraph.reference.analysis;

import java.util.List;
import java.util.Objects;

public record AnalysisResult(RiskLevel riskLevel, String findingsSummary, List<String> recommendations) {
    public AnalysisResult {
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(findingsSummary, "findingsSummary");
        recommendations = List.copyOf(recommendations);
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }
}
