package dev.specgraph.reference.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator-configurable LM Studio limits used to reject prompts that cannot fit the loaded model.
 * These provider/runtime limits remain separate from the application-owned evidence-count bounds.
 */
@ConfigurationProperties("specgraph.analysis.local.budget")
record LmStudioPromptBudgetProperties(
        Integer contextWindowTokens,
        Integer maxOutputTokens,
        Integer transportMarginTokens) {
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 4096;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 512;
    private static final int DEFAULT_TRANSPORT_MARGIN_TOKENS = 256;

    LmStudioPromptBudgetProperties {
        contextWindowTokens = defaulted(contextWindowTokens, DEFAULT_CONTEXT_WINDOW_TOKENS);
        maxOutputTokens = defaulted(maxOutputTokens, DEFAULT_MAX_OUTPUT_TOKENS);
        transportMarginTokens = defaulted(transportMarginTokens, DEFAULT_TRANSPORT_MARGIN_TOKENS);
    }

    /** Rejects non-positive operator limits before constructing the conservative request budget. */
    LmStudioPromptBudget validatedBudget() {
        if (contextWindowTokens <= 0) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_CONTEXT_WINDOW_TOKENS must be positive");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_MAX_OUTPUT_TOKENS must be positive");
        }
        if (transportMarginTokens < 0) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_TRANSPORT_MARGIN_TOKENS must not be negative");
        }
        return new LmStudioPromptBudget(contextWindowTokens, maxOutputTokens, transportMarginTokens);
    }

    private static int defaulted(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
