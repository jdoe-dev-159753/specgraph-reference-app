package dev.specgraph.reference.analysis;

public interface AnalysisModelPort {
    AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence);
}
