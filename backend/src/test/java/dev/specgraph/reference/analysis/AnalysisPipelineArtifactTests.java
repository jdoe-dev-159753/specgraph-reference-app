package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnalysisPipelineArtifactTests {

    @Test
    void keepsStageArtifactsAsAClosedTypedVariantSet() {
        AnalysisPipelineArtifact detector = new RiskSignalEvidence(
                "detector:v1",
                "velocity-shift",
                0.81,
                Map.of("featureSchema", "v1"));
        AnalysisPipelineArtifact retrieval = new PolicyEvidence(
                "policy:chunk:42",
                "Synthetic policy content.",
                Map.of("adapter", "pgvector"));
        AnalysisPipelineArtifact model = new AnalysisModelProvenance(
                "deterministic",
                "baseline-v1",
                Map.of("externalTransmission", "false"));

        assertThat(AnalysisPipelineArtifact.class.isSealed()).isTrue();
        assertThat(List.of(AnalysisPipelineArtifact.class.getPermittedSubclasses()))
                .containsExactlyInAnyOrder(
                        RiskSignalEvidence.class,
                        PolicyEvidence.class,
                        AnalysisModelProvenance.class);
        assertThat(List.of(detector.kind(), retrieval.kind(), model.kind()))
                .containsExactly(
                        AnalysisPipelineArtifact.Kind.DETECTOR_EVIDENCE,
                        AnalysisPipelineArtifact.Kind.POLICY_RETRIEVAL_EVIDENCE,
                        AnalysisPipelineArtifact.Kind.MODEL_BACKEND_PROVENANCE);
        assertThat(List.of(
                        typedPayloadIdentity(detector),
                        typedPayloadIdentity(retrieval),
                        typedPayloadIdentity(model)))
                .containsExactly("velocity-shift", "policy:chunk:42", "baseline-v1");
    }

    private String typedPayloadIdentity(AnalysisPipelineArtifact artifact) {
        return switch (artifact) {
            case RiskSignalEvidence detector -> detector.signalIdentity();
            case PolicyEvidence retrieval -> retrieval.sourceIdentity();
            case AnalysisModelProvenance model -> model.modelIdentity();
        };
    }
}
