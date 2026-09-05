package dev.specgraph.reference.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Explicit opt-in OpenAI Stage-3 adapter using provider-native structured output validation.
 * It marks external transmission and the synthetic-data policy in provenance; merely having Spring
 * AI on the classpath cannot select this adapter.
 */
@Component
@ConditionalOnProperty(prefix = "specgraph.analysis", name = "backend", havingValue = "openai")
final class SpringAiAnalysisAdapter implements AnalysisModelPort {
    static final String BACKEND_IDENTITY = "openai";

    private final ChatClient chatClient;
    private final String modelIdentity;

    SpringAiAnalysisAdapter(
            ChatModel chatModel,
            @Value("${spring.ai.openai.chat.model:gpt-5-mini}") String modelIdentity) {
        this.chatClient = ChatClient.create(chatModel);
        this.modelIdentity = modelIdentity;
    }

    /** Submits bounded synthetic evidence and marks the resulting provenance as externally transmitted. */
    @Override
    public AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence) {
        AnalysisResult result = chatClient.prompt()
                .system(GroundedAnalysisPrompt.SYSTEM)
                .user(GroundedAnalysisPrompt.render(evidence))
                .call()
                .entity(AnalysisResultStructuredOutputConverter.INSTANCE, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());

        Map<String, String> metadata = new LinkedHashMap<>(evidence.contextDiagnostics());
        metadata.put("externalTransmission", "true");
        metadata.put("dataPolicy", "synthetic-demo-only");
        metadata.put("structuredOutput", "provider-native+schema-validation");

        return new AnalysisModelOutput(
                result,
                new AnalysisModelProvenance(
                        BACKEND_IDENTITY,
                        modelIdentity,
                        GroundedAnalysisPrompt.IDENTITY,
                        AnalysisEvidenceReferences.from(evidence),
                        Map.copyOf(metadata)));
    }

}
