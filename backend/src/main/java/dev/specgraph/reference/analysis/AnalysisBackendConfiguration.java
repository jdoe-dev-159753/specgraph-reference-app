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
