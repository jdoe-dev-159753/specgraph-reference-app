package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class CompositeRiskSignalDetectorTests {
    private final CustomerSnapshot snapshot = new CustomerSnapshot(UUID.randomUUID(), List.of(), List.of());

    @Test
    void preservesConfiguredChildAndEvidenceOrder() {
        RiskSignalEvidence first = new RiskSignalEvidence("first", "one", 0.1, Map.of());
        RiskSignalEvidence second = new RiskSignalEvidence("second", "two", 0.2, Map.of());
        RiskSignalEvidence third = new RiskSignalEvidence("second", "three", 0.3, Map.of());

        CompositeRiskSignalDetector composite = new CompositeRiskSignalDetector(List.of(
                ignored -> List.of(first),
                ignored -> List.of(second, third)));

        assertThat(composite.detect(snapshot)).containsExactly(first, second, third);
    }

    @Test
    void childFailureFailsTheWholeStageInsteadOfSilentlyDroppingEvidence() {
        RiskSignalDetectorPort failing = ignored -> {
            throw new IllegalStateException("detector failed");
        };
        CompositeRiskSignalDetector composite = new CompositeRiskSignalDetector(List.of(
                ignored -> List.of(),
                failing));

        assertThatThrownBy(() -> composite.detect(snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("detector failed");
    }
}
