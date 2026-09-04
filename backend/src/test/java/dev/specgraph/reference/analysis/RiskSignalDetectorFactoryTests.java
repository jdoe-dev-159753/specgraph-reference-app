package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RiskSignalDetectorFactoryTests {
    private final RiskSignalDetectorPort noOp = snapshot -> java.util.List.of();
    private final RiskSignalDetectorPort bayesian = snapshot -> java.util.List.of();
    private final RiskSignalDetectorPort fuzzy = snapshot -> java.util.List.of();
    private final RiskSignalDetectorPort randomForest = snapshot -> java.util.List.of();

    private final RiskSignalDetectorFactory factory = new RiskSignalDetectorFactory(Map.of(
            RiskSignalDetectorId.NO_OP, () -> noOp,
            RiskSignalDetectorId.BAYESIAN, () -> bayesian,
            RiskSignalDetectorId.FUZZY, () -> fuzzy,
            RiskSignalDetectorId.RANDOM_FOREST, () -> randomForest));

    @Test
    void singleSelectionReturnsTheRegisteredLeaf() {
        assertThat(factory.resolve(java.util.List.of(RiskSignalDetectorId.BAYESIAN))).isSameAs(bayesian);
    }

    @Test
    void randomForestProviderIsLoadedOnlyWhenSelected() {
        AtomicInteger loads = new AtomicInteger();
        Supplier<RiskSignalDetectorPort> lazyRandomForest = () -> {
            loads.incrementAndGet();
            return randomForest;
        };
        RiskSignalDetectorFactory lazyFactory = new RiskSignalDetectorFactory(Map.of(
                RiskSignalDetectorId.NO_OP, () -> noOp,
                RiskSignalDetectorId.RANDOM_FOREST, lazyRandomForest));

        assertThat(lazyFactory.resolve(java.util.List.of(RiskSignalDetectorId.NO_OP))).isSameAs(noOp);
        assertThat(loads).hasValue(0);
        assertThat(lazyFactory.resolve(java.util.List.of(RiskSignalDetectorId.RANDOM_FOREST)))
                .isSameAs(randomForest);
        assertThat(lazyFactory.resolve(java.util.List.of(RiskSignalDetectorId.RANDOM_FOREST)))
                .isSameAs(randomForest);
        assertThat(loads).hasValue(1);
    }

    @Test
    void selectedDetectorProviderFailureIsNotReplacedByAFallback() {
        RiskSignalDetectorFactory failingFactory = new RiskSignalDetectorFactory(Map.of(
                RiskSignalDetectorId.NO_OP, () -> noOp,
                RiskSignalDetectorId.RANDOM_FOREST, () -> {
                    throw new IllegalStateException("model resource is unavailable");
                }));

        assertThatThrownBy(() -> failingFactory.resolve(
                        java.util.List.of(RiskSignalDetectorId.RANDOM_FOREST)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("model resource is unavailable");
    }

    @Test
    void multipleConcreteSelectionsProduceAComposite() {
        assertThat(factory.resolve(java.util.List.of(RiskSignalDetectorId.BAYESIAN, RiskSignalDetectorId.FUZZY)))
                .isInstanceOf(CompositeRiskSignalDetector.class);
    }

    @Test
    void randomForestCanParticipateInAComposite() {
        assertThat(factory.resolve(java.util.List.of(
                        RiskSignalDetectorId.BAYESIAN,
                        RiskSignalDetectorId.RANDOM_FOREST)))
                .isInstanceOf(CompositeRiskSignalDetector.class);
    }

    @Test
    void duplicateSelectionsFailClearly() {
        assertThatThrownBy(() -> factory.resolve(java.util.List.of(
                        RiskSignalDetectorId.RANDOM_FOREST,
                        RiskSignalDetectorId.RANDOM_FOREST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate detector selection");
    }

    @Test
    void noOpCannotBeMixedWithConcreteLeaves() {
        assertThatThrownBy(() -> factory.resolve(java.util.List.of(
                        RiskSignalDetectorId.NO_OP,
                        RiskSignalDetectorId.RANDOM_FOREST)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_OP cannot be combined");
    }
}
