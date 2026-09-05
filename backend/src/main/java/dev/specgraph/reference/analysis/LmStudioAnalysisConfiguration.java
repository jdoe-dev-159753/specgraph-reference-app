package dev.specgraph.reference.analysis;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the local OpenAI-compatible client only when the LOCAL backend is selected.
 * Retries are disabled to keep a reviewer action observable as one bounded LM Studio request.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "specgraph.analysis", name = "backend", havingValue = "local")
@EnableConfigurationProperties({LmStudioAnalysisProperties.class, LmStudioPromptBudgetProperties.class})
class LmStudioAnalysisConfiguration {
    /** Builds a zero-retry local client only after endpoint, model, timeout, and budget validation. */
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
