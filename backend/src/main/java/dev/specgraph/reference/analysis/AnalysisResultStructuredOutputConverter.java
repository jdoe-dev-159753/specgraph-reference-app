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
                "findingsSummary": {"type": "string", "maxLength": 500},
                "recommendations": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 3,
                  "items": {"type": "string", "maxLength": 140}
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
        return delegate.convert(source);
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
