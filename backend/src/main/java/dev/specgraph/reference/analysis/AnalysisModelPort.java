package dev.specgraph.reference.analysis;

/**
 * Outbound boundary for an advisory analysis model.
 *
 * <p>The model receives only the application-selected, bounded {@link AnalysisEvidenceEnvelope};
 * it must return structured output with provenance referring solely to evidence present in that
 * envelope. Provider transport and decoding failures are exposed as runtime failures for the
 * application service to classify.
 */
public interface AnalysisModelPort {
    /** Produces one structured, provenance-bearing assessment from bounded evidence. */
    AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence);
}
