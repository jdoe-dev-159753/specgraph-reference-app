package dev.specgraph.reference.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Transitional guard for the pre-Composite detector selector.
 *
 * <p>#224 replaces this mutual-exclusion rule with an explicit bounded Composite. Until then,
 * activating more than one concrete detector profile is invalid and must fail clearly rather than
 * through accidental ambiguous dependency injection.
 */
@Configuration(proxyBeanMethods = false)
@Profile("bayesian-detector & fuzzy-detector")
class RiskSignalDetectorProfileGuardConfiguration {
    static final String ERROR_MESSAGE =
            "Profiles 'bayesian-detector' and 'fuzzy-detector' are mutually exclusive until #224 introduces Composite detector selection";

    @Bean
    RiskSignalDetectorPort invalidCombinedDetectorProfiles() {
        throw new IllegalStateException(ERROR_MESSAGE);
    }
}
