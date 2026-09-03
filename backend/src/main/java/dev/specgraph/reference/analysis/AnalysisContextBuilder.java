package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class AnalysisContextBuilder {
    private static final Comparator<Activity> ACTIVITY_ORDER = Comparator
            .comparing(Activity::createdAt)
            .reversed()
            .thenComparing(activity -> activity.transactionId().toString());
    private static final Comparator<RiskEvidence> SOURCE_RISK_ORDER = Comparator
            .comparing(RiskEvidence::triggeredAt)
            .reversed()
            .thenComparing(risk -> risk.assessmentId().toString());
    private static final Comparator<RiskSignalEvidence> DETECTOR_ORDER = Comparator
            .comparingDouble(RiskSignalEvidence::score)
            .reversed()
            .thenComparing(RiskSignalEvidence::artifactIdentity);

    private final AnalysisContextProperties properties;

    AnalysisContextBuilder(AnalysisContextProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    AnalysisEvidenceEnvelope build(
            CustomerSnapshot snapshot,
            List<RiskSignalEvidence> detectorEvidence,
            List<PolicyEvidence> policyEvidence) {
        Objects.requireNonNull(snapshot, "snapshot");
        detectorEvidence = List.copyOf(Objects.requireNonNull(detectorEvidence, "detectorEvidence"));
        policyEvidence = List.copyOf(Objects.requireNonNull(policyEvidence, "policyEvidence"));

        List<Activity> selectedActivities = snapshot.activities().stream()
                .sorted(ACTIVITY_ORDER)
                .limit(properties.maxActivities())
                .toList();
        List<RiskEvidence> selectedSourceRisk = snapshot.riskEvidence().stream()
                .sorted(SOURCE_RISK_ORDER)
                .limit(properties.maxSourceRiskEvidence())
                .toList();
        List<RiskSignalEvidence> selectedDetectorEvidence = detectorEvidence.stream()
                .sorted(DETECTOR_ORDER)
                .limit(properties.maxDetectorEvidence())
                .toList();
        List<PolicyEvidence> selectedPolicyEvidence = policyEvidence.stream()
                .limit(properties.maxPolicyEvidence())
                .toList();

        return new AnalysisEvidenceEnvelope(
                snapshot.customerId(),
                snapshot.activities().size(),
                snapshot.riskEvidence().size(),
                detectorEvidence.size(),
                policyEvidence.size(),
                selectedActivities,
                selectedSourceRisk,
                selectedDetectorEvidence,
                selectedPolicyEvidence);
    }
}
