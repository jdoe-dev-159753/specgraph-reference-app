package dev.specgraph.reference.analysis;

import java.util.List;

public record AnalysisResult(RiskLevel riskLevel, String findingsSummary, List<String> recommendations) {
    public enum RiskLevel { LOW, MEDIUM, HIGH }
}
