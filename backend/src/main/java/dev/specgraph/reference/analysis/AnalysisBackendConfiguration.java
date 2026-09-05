package dev.specgraph.reference.analysis;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Composition boundary for the independently selected Stage-3 analysis backend.
 * The project-owned selector remains authoritative even when Spring AI also exposes a chat-model
 * selector, preventing ambient provider configuration from silently changing data transmission.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalysisBackendProperties.class)
class AnalysisBackendConfiguration {

    @Bean
    AnalysisBackendFactory analysisBackendFactory(
            DeterministicAnalysisAdapter deterministic,
            ObjectProvider<SpringAiAnalysisAdapter> openAi,
            ObjectProvider<LmStudioAnalysisAdapter> local) {
        var adapters = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        adapters.put(AnalysisBackendId.DETERMINISTIC, () -> deterministic);
        adapters.put(AnalysisBackendId.OPENAI, openAi::getIfAvailable);
        adapters.put(AnalysisBackendId.LOCAL, local::getIfAvailable);
        return new AnalysisBackendFactory(adapters);
    }

    /** Rejects disagreement between the project selector and Spring AI before a provider is used. */
    @Bean
    @Primary
    AnalysisModelPort selectedAnalysisModel(
            AnalysisBackendProperties properties,
            AnalysisBackendFactory factory,
            @Value("${spring.ai.model.chat:${specgraph.analysis.backend}}") String chatModelSelector) {
        String expectedSelector = properties.backend().name().toLowerCase(Locale.ROOT);
        if (!expectedSelector.equalsIgnoreCase(chatModelSelector)) {
            throw new IllegalStateException("spring.ai.model.chat must match the authoritative "
                    + "specgraph.analysis.backend selection (expected " + expectedSelector
                    + ", got " + chatModelSelector + ")");
        }
        return factory.resolve(properties.backend());
    }
}
