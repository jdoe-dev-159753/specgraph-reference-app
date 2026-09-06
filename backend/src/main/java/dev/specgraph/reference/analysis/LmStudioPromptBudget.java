package dev.specgraph.reference.analysis;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * Conservative preflight for the complete OpenAI-compatible LM Studio request.
 *
 * <p>CL100K is a deterministic estimate, not an exact Ministral token count. Every estimate is
 * inflated by 25%, and the separately configured transport margin reserves room for message and
 * JSON framing plus remaining tokenizer variance.
 */
final class LmStudioPromptBudget {
    private static final int ESTIMATE_SAFETY_NUMERATOR = 5;
    private static final int ESTIMATE_SAFETY_DENOMINATOR = 4;

    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final int transportMarginTokens;
    private final TokenCountEstimator tokenCountEstimator;
    private final int systemTokens;
    private final int schemaTokens;

    LmStudioPromptBudget(int contextWindowTokens, int maxOutputTokens, int transportMarginTokens) {
        this(contextWindowTokens, maxOutputTokens, transportMarginTokens, new JTokkitTokenCountEstimator());
    }

    /**
     * Precomputes fixed-envelope cost and rejects configurations that leave no possible user input.
     */
    LmStudioPromptBudget(
            int contextWindowTokens,
            int maxOutputTokens,
            int transportMarginTokens,
            TokenCountEstimator tokenCountEstimator) {
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.transportMarginTokens = transportMarginTokens;
        this.tokenCountEstimator = tokenCountEstimator;
        this.systemTokens = estimate(GroundedAnalysisPrompt.SYSTEM);
        this.schemaTokens = estimate(AnalysisResultStructuredOutputConverter.JSON_SCHEMA);

        int fixedTokens = systemTokens + schemaTokens + transportMarginTokens + maxOutputTokens;
        if (fixedTokens > contextWindowTokens) {
            throw new IllegalStateException(
                    "LM Studio fixed request envelope exceeds the configured context window: fixed="
                            + fixedTokens + ", contextWindow=" + contextWindowTokens);
        }
    }

    /** Computes and returns the provenance-ready budget, failing before any network request. */
    RequestBudget requireFits(String userPrompt) {
        int userTokens = estimate(userPrompt);
        int estimatedTotalTokens = systemTokens
                + userTokens
                + schemaTokens
                + transportMarginTokens
                + maxOutputTokens;
        var budget = new RequestBudget(
                contextWindowTokens,
                systemTokens,
                userTokens,
                schemaTokens,
                transportMarginTokens,
                maxOutputTokens,
                estimatedTotalTokens);
        if (estimatedTotalTokens > contextWindowTokens) {
            throw new InvalidAnalysisResultException(
                    "LM Studio request exceeds the configured context window: " + budget.diagnostic());
        }
        return budget;
    }

    int maxOutputTokens() {
        return maxOutputTokens;
    }

    private int estimate(String text) {
        int cl100kEstimate = tokenCountEstimator.estimate(text);
        return Math.toIntExact(
                (cl100kEstimate * (long) ESTIMATE_SAFETY_NUMERATOR + ESTIMATE_SAFETY_DENOMINATOR - 1)
                        / ESTIMATE_SAFETY_DENOMINATOR);
    }

    /** Immutable estimate retained in provenance and checked before the request is transmitted. */
    record RequestBudget(
            int contextWindowTokens,
            int systemTokens,
            int userTokens,
            int schemaTokens,
            int transportMarginTokens,
            int maxOutputTokens,
            int estimatedTotalTokens) {
        String diagnostic() {
            return "system=" + systemTokens
                    + ", user=" + userTokens
                    + ", schema=" + schemaTokens
                    + ", transportMargin=" + transportMarginTokens
                    + ", maxOutput=" + maxOutputTokens
                    + ", estimatedTotal=" + estimatedTotalTokens
                    + ", contextWindow=" + contextWindowTokens;
        }
    }
}
