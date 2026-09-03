package dev.specgraph.reference.analysis;

import java.util.EnumMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
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
            ObjectProvider<SpringAiAnalysisAdapter> openAi) {
        var adapters = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        adapters.put(AnalysisBackendId.DETERMINISTIC, () -> deterministic);
        adapters.put(AnalysisBackendId.OPENAI, openAi::getIfAvailable);
        return new AnalysisBackendFactory(adapters);
    }

    @Bean
    @Primary
    AnalysisModelPort selectedAnalysisModel(
            AnalysisBackendProperties properties,
            AnalysisBackendFactory factory) {
        return factory.resolve(properties.backend());
    }
}
