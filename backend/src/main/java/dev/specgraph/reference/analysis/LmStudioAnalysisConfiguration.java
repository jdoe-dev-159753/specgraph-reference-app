package dev.specgraph.reference.analysis;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "specgraph.analysis", name = "backend", havingValue = "local")
@EnableConfigurationProperties({LmStudioAnalysisProperties.class, LmStudioPromptBudgetProperties.class})
class LmStudioAnalysisConfiguration {
    @Bean
    LmStudioAnalysisAdapter lmStudioAnalysisAdapter(
            LmStudioAnalysisProperties properties,
            LmStudioPromptBudgetProperties budgetProperties) {
        String model = properties.validatedModel();
        LmStudioPromptBudget promptBudget = budgetProperties.validatedBudget();
        var options = OpenAiChatOptions.builder()
                .baseUrl(properties.validatedBaseUrl())
                .apiKey(properties.apiKey())
                .model(model)
                .timeout(properties.validatedTimeout())
                .maxRetries(0)
                .maxTokens(promptBudget.maxOutputTokens())
                .build();
        return new LmStudioAnalysisAdapter(
                OpenAiChatModel.builder().options(options).build(), model, promptBudget);
    }
}
