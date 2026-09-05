package dev.specgraph.reference.analysis;

import java.util.Map;

/**
 * Closed application-owned pivot for derived analysis-stage artifacts.
 *
 * <p>The concrete record type is the discriminant. There is deliberately no generic nullable
 * payload slot: adding a new artifact variant must extend this sealed hierarchy and makes
 * exhaustive pattern switches fail to compile until the new variant is handled explicitly.
 */
public sealed interface AnalysisPipelineArtifact
        permits RiskSignalEvidence, PolicyEvidence, AnalysisModelProvenance {

    /** Closed discriminator for the supported derived-artifact families. */
    enum Kind {
        DETECTOR_EVIDENCE,
        POLICY_RETRIEVAL_EVIDENCE,
        MODEL_BACKEND_PROVENANCE
    }

    default Kind kind() {
        return switch (this) {
            case RiskSignalEvidence ignored -> Kind.DETECTOR_EVIDENCE;
            case PolicyEvidence ignored -> Kind.POLICY_RETRIEVAL_EVIDENCE;
            case AnalysisModelProvenance ignored -> Kind.MODEL_BACKEND_PROVENANCE;
        };
    }

    default String artifactIdentity() {
        return switch (this) {
            case RiskSignalEvidence evidence -> evidence.detectorIdentity() + ":" + evidence.signalIdentity();
            case PolicyEvidence evidence -> evidence.sourceIdentity();
            case AnalysisModelProvenance provenance ->
                    provenance.backendIdentity() + ":" + provenance.modelIdentity();
        };
    }

    /** Provider-neutral metadata retained with the typed payload. */
    Map<String, String> metadata();
}
