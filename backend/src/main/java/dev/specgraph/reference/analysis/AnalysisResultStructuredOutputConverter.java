package dev.specgraph.reference.analysis;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

/** Exact provider and response-side schema for the application-owned Stage-3 result. */
final class AnalysisResultStructuredOutputConverter implements StructuredOutputConverter<AnalysisResult> {
    static final AnalysisResultStructuredOutputConverter INSTANCE = new AnalysisResultStructuredOutputConverter();
    static final int MAX_FINDINGS_SUMMARY_CHARACTERS = 2_000;
    static final int MAX_RECOMMENDATION_CHARACTERS = 1_000;
    static final int MAX_RECOMMENDATIONS = 3;

    static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "riskLevel": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                "findingsSummary": {"type": "string"},
                "recommendations": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": ["riskLevel", "findingsSummary", "recommendations"],
              "additionalProperties": false
            }
            """;

    private final BeanOutputConverter<AnalysisResult> delegate = new BeanOutputConverter<>(AnalysisResult.class);

    private AnalysisResultStructuredOutputConverter() {}

    @Override
    public AnalysisResult convert(String source) {
        AnalysisResult result = delegate.convert(source);
        if (result.findingsSummary().length() > MAX_FINDINGS_SUMMARY_CHARACTERS) {
            throw new InvalidAnalysisResultException(
                    "live findings summary must not exceed " + MAX_FINDINGS_SUMMARY_CHARACTERS + " characters");
        }
        if (result.recommendations().size() > MAX_RECOMMENDATIONS) {
            throw new InvalidAnalysisResultException(
                    "live analysis must not contain more than " + MAX_RECOMMENDATIONS + " recommendations");
        }
        if (result.recommendations().stream()
                .anyMatch(recommendation -> recommendation.length() > MAX_RECOMMENDATION_CHARACTERS)) {
            throw new InvalidAnalysisResultException(
                    "live recommendations must not exceed " + MAX_RECOMMENDATION_CHARACTERS + " characters");
        }
        return result;
    }

    @Override
    public String getFormat() {
        return "Return JSON conforming to this schema:\n" + JSON_SCHEMA;
    }

    @Override
    public String getJsonSchema() {
        return JSON_SCHEMA;
    }
}
