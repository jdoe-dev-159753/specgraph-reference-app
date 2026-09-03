package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class RiskSignalDetectorProfileWiringTests {

    @Test
    void defaultSelectionResolvesNoOpDetector() {
        assertSelected(NoOpRiskSignalDetectorAdapter.class, Map.of());
    }

    @Test
    void bayesianProfileRemainsACompatibilityAlias() {
        assertSelected(BayesianSequentialRiskSignalDetectorAdapter.class, Map.of(), "bayesian-detector");
    }

    @Test
    void fuzzyProfileRemainsACompatibilityAlias() {
        assertSelected(FuzzyRiskSignalDetectorAdapter.class, Map.of(), "fuzzy-detector");
    }

    @Test
    void simultaneousLegacyProfilesResolveAComposite() {
        assertSelected(CompositeRiskSignalDetector.class, Map.of(), "bayesian-detector", "fuzzy-detector");
    }

    @Test
    void typedSelectionOverridesLegacyProfiles() {
        assertSelected(
                FuzzyRiskSignalDetectorAdapter.class,
                Map.of("specgraph.analysis.detectors[0]", "FUZZY"),
                "bayesian-detector");
    }

    private void assertSelected(
            Class<? extends RiskSignalDetectorPort> expectedType,
            Map<String, Object> properties,
            String... profiles) {
        try (AnnotationConfigApplicationContext context = unrefreshedContext(properties, profiles)) {
            context.refresh();
            assertThat(context.getBean(RiskSignalDetectorPort.class)).isInstanceOf(expectedType);
        }
    }

    private AnnotationConfigApplicationContext unrefreshedContext(
            Map<String, Object> properties,
            String... profiles) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        context.register(
                NoOpRiskSignalDetectorAdapter.class,
                BayesianSequentialRiskSignalDetectorAdapter.class,
                FuzzyRiskSignalDetectorAdapter.class,
                RiskSignalDetectorConfiguration.class);
        return context;
    }
}
