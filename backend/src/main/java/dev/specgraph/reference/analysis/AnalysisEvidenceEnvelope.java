package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * Application-owned evidence boundary supplied to advisory analysis models.
 * Source facts, derived detector signals and retrieved policy evidence stay distinguishable.
 */
public record AnalysisEvidenceEnvelope(
        CustomerSnapshot snapshot,
        List<RiskSignalEvidence> detectorEvidence,
        List<PolicyEvidence> policyEvidence) {
    public AnalysisEvidenceEnvelope {
        Objects.requireNonNull(snapshot, "snapshot");
        detectorEvidence = List.copyOf(Objects.requireNonNull(detectorEvidence, "detectorEvidence"));
        policyEvidence = List.copyOf(Objects.requireNonNull(policyEvidence, "policyEvidence"));
    }
}
