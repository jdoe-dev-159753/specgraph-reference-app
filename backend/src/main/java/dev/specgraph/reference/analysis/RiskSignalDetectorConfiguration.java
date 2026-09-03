package dev.specgraph.reference.analysis;

import java.util.EnumMap;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RiskSignalDetectorProperties.class)
class RiskSignalDetectorConfiguration {

    @Bean
    RiskSignalDetectorFactory riskSignalDetectorFactory(
            NoOpRiskSignalDetectorAdapter noOp,
            BayesianSequentialRiskSignalDetectorAdapter bayesian,
            FuzzyRiskSignalDetectorAdapter fuzzy) {
        EnumMap<RiskSignalDetectorId, RiskSignalDetectorPort> registry =
                new EnumMap<>(RiskSignalDetectorId.class);
        registry.put(RiskSignalDetectorId.NO_OP, noOp);
        registry.put(RiskSignalDetectorId.BAYESIAN, bayesian);
        registry.put(RiskSignalDetectorId.FUZZY, fuzzy);
        return new RiskSignalDetectorFactory(registry);
    }

    @Bean
    @Primary
    RiskSignalDetectorPort selectedRiskSignalDetector(
            RiskSignalDetectorFactory factory,
            RiskSignalDetectorProperties properties,
            Environment environment) {
        List<RiskSignalDetectorId> selection = properties.hasExplicitSelection()
                ? properties.detectors()
                : legacyProfileSelection(environment);
        return factory.resolve(selection);
    }

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
