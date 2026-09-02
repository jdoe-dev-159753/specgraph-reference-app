package dev.specgraph.reference.analysis;

import java.util.Objects;

public record AnalysisModelOutput(
        AnalysisResult result,
        AnalysisModelProvenance provenance) {
    public AnalysisModelOutput {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(provenance, "provenance");
    }
}
