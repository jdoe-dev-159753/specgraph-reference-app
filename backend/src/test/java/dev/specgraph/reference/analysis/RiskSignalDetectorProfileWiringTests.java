package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RiskSignalDetectorProfileWiringTests {

    @Test
    void defaultProfileSelectsOnlyNoOpDetector() {
        assertSelected(NoOpRiskSignalDetectorAdapter.class);
    }

    @Test
    void bayesianProfileSelectsOnlyBayesianDetector() {
        assertSelected(BayesianSequentialRiskSignalDetectorAdapter.class, "bayesian-detector");
    }

    @Test
    void fuzzyProfileSelectsOnlyFuzzyDetector() {
        assertSelected(FuzzyRiskSignalDetectorAdapter.class, "fuzzy-detector");
    }

    @Test
    void simultaneousConcreteDetectorProfilesFailWithExplicitTransitionalGuard() {
        try (AnnotationConfigApplicationContext context = unrefreshedContext("bayesian-detector", "fuzzy-detector")) {
            assertThatThrownBy(context::refresh)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(RiskSignalDetectorProfileGuardConfiguration.ERROR_MESSAGE);
        }
    }

    private void assertSelected(Class<? extends RiskSignalDetectorPort> expectedType, String... profiles) {
        try (AnnotationConfigApplicationContext context = unrefreshedContext(profiles)) {
            context.refresh();
            Map<String, RiskSignalDetectorPort> detectors = context.getBeansOfType(RiskSignalDetectorPort.class);
            assertThat(detectors).hasSize(1);
            assertThat(detectors.values().iterator().next()).isInstanceOf(expectedType);
        }
    }

    private AnnotationConfigApplicationContext unrefreshedContext(String... profiles) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        context.register(
                NoOpRiskSignalDetectorAdapter.class,
                BayesianSequentialRiskSignalDetectorAdapter.class,
                FuzzyRiskSignalDetectorAdapter.class,
                RiskSignalDetectorProfileGuardConfiguration.class);
        return context;
    }
}
