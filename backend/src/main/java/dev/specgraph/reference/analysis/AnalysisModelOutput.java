package dev.specgraph.reference.analysis;

import java.util.Objects;

/**
 * Provider-neutral model response pairing the structured assessment with its execution provenance.
 * Both parts are required so callers cannot retain an analysis without its model and grounding
 * identity.
 */
public record AnalysisModelOutput(
        AnalysisResult result,
        AnalysisModelProvenance provenance) {
    public AnalysisModelOutput {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(provenance, "provenance");
    }
}
