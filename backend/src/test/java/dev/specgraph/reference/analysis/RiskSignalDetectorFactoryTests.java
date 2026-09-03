package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RiskSignalDetectorFactoryTests {
    private final RiskSignalDetectorPort noOp = snapshot -> java.util.List.of();
    private final RiskSignalDetectorPort bayesian = snapshot -> java.util.List.of();
    private final RiskSignalDetectorPort fuzzy = snapshot -> java.util.List.of();

    private final RiskSignalDetectorFactory factory = new RiskSignalDetectorFactory(Map.of(
            RiskSignalDetectorId.NO_OP, noOp,
            RiskSignalDetectorId.BAYESIAN, bayesian,
            RiskSignalDetectorId.FUZZY, fuzzy));

    @Test
    void singleSelectionReturnsTheRegisteredLeaf() {
        assertThat(factory.resolve(java.util.List.of(RiskSignalDetectorId.BAYESIAN))).isSameAs(bayesian);
    }

    @Test
    void multipleConcreteSelectionsProduceAComposite() {
        assertThat(factory.resolve(java.util.List.of(RiskSignalDetectorId.BAYESIAN, RiskSignalDetectorId.FUZZY)))
                .isInstanceOf(CompositeRiskSignalDetector.class);
    }

    @Test
    void duplicateSelectionsFailClearly() {
        assertThatThrownBy(() -> factory.resolve(java.util.List.of(
                        RiskSignalDetectorId.BAYESIAN,
                        RiskSignalDetectorId.BAYESIAN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate detector selection");
    }

    @Test
    void noOpCannotBeMixedWithConcreteLeaves() {
        assertThatThrownBy(() -> factory.resolve(java.util.List.of(
                        RiskSignalDetectorId.NO_OP,
                        RiskSignalDetectorId.FUZZY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_OP cannot be combined");
    }
}
