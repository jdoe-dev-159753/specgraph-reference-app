package dev.specgraph.reference.analysis;

import dev.specgraph.reference.analysis.randomforest.RandomForestRiskSignalDetectorRuntime;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Composition boundary for one ordered detector selection or bounded composite.
 * Typed configuration is authoritative; historical Spring profiles are consulted only when the
 * typed selector is absent, preserving compatibility without creating a second active control.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskSignalDetectorProperties.class)
class RiskSignalDetectorConfiguration {

    @Bean
    RiskSignalDetectorFactory riskSignalDetectorFactory(
            NoOpRiskSignalDetectorAdapter noOp,
            BayesianSequentialRiskSignalDetectorAdapter bayesian,
            FuzzyRiskSignalDetectorAdapter fuzzy) {
        EnumMap<RiskSignalDetectorId, Supplier<? extends RiskSignalDetectorPort>> registry =
                new EnumMap<>(RiskSignalDetectorId.class);
        registry.put(RiskSignalDetectorId.NO_OP, () -> noOp);
        registry.put(RiskSignalDetectorId.BAYESIAN, () -> bayesian);
        registry.put(RiskSignalDetectorId.FUZZY, () -> fuzzy);
        RandomForestRiskSignalDetectorRuntime randomForestRuntime =
                new RandomForestRiskSignalDetectorRuntime();
        registry.put(RiskSignalDetectorId.RANDOM_FOREST, randomForestRuntime::load);
        return new RiskSignalDetectorFactory(registry);
    }

    @Bean
    @Primary
    RiskSignalDetectorPort selectedRiskSignalDetector(
            RiskSignalDetectorFactory factory,
            RiskSignalDetectorProperties properties,
            Environment environment) {
        List<RiskSignalDetectorId> selection = properties.hasExplicitSelection()
                ? properties.configuredDetectors()
                : legacyProfileSelection(environment);
        return factory.resolve(selection);
    }

    /** Translates legacy profile combinations only when no explicit typed selection is present. */
    static List<RiskSignalDetectorId> legacyProfileSelection(Environment environment) {
        boolean bayesian = environment.acceptsProfiles(Profiles.of("bayesian-detector"));
        boolean fuzzy = environment.acceptsProfiles(Profiles.of("fuzzy-detector"));
        if (bayesian && fuzzy) {
            return List.of(RiskSignalDetectorId.BAYESIAN, RiskSignalDetectorId.FUZZY);
        }
        if (bayesian) {
            return List.of(RiskSignalDetectorId.BAYESIAN);
        }
        if (fuzzy) {
            return List.of(RiskSignalDetectorId.FUZZY);
        }
        return List.of(RiskSignalDetectorId.NO_OP);
    }
}
