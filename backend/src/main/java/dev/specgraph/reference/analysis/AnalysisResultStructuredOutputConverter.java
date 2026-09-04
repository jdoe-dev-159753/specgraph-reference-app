package dev.specgraph.reference.analysis;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

/** Exact provider and response-side schema for the application-owned Stage-3 result. */
final class AnalysisResultStructuredOutputConverter implements StructuredOutputConverter<AnalysisResult> {
    static final AnalysisResultStructuredOutputConverter INSTANCE = new AnalysisResultStructuredOutputConverter();

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
        if (result.findingsSummary().length() > 500) {
            throw new InvalidAnalysisResultException("live findings summary must not exceed 500 characters");
        }
        if (result.recommendations().size() > 3) {
            throw new InvalidAnalysisResultException("live analysis must not contain more than 3 recommendations");
        }
        if (result.recommendations().stream().anyMatch(recommendation -> recommendation.length() > 140)) {
            throw new InvalidAnalysisResultException("live recommendations must not exceed 140 characters");
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
