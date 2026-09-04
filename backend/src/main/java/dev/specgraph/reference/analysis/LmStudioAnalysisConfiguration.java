package dev.specgraph.reference.analysis;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LmStudioAnalysisProperties.class)
class LmStudioAnalysisConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "specgraph.analysis", name = "backend", havingValue = "local")
    LmStudioAnalysisAdapter lmStudioAnalysisAdapter(LmStudioAnalysisProperties properties) {
        String model = properties.validatedModel();
        var options = OpenAiChatOptions.builder()
                .baseUrl(properties.validatedBaseUrl())
                .apiKey(properties.apiKey())
                .model(model)
                .timeout(properties.validatedTimeout())
                .maxRetries(0)
                .build();
        return new LmStudioAnalysisAdapter(OpenAiChatModel.builder().options(options).build(), model);
    }
}
