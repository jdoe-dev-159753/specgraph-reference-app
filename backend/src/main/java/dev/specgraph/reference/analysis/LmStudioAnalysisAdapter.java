package dev.specgraph.reference.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/** Local LM Studio Stage-3 leaf over its OpenAI-compatible Chat Completions endpoint. */
final class LmStudioAnalysisAdapter implements AnalysisModelPort {
    static final String BACKEND_IDENTITY = "local";
    static final String RUNTIME_IDENTITY = "lmstudio/llama.cpp";

    private final ChatClient chatClient;
    private final String modelIdentity;

    LmStudioAnalysisAdapter(ChatModel chatModel, String modelIdentity) {
        this.chatClient = ChatClient.create(chatModel);
        this.modelIdentity = modelIdentity;
    }

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
        metadata.put("runtime", RUNTIME_IDENTITY);
        metadata.put("externalTransmission", "false");
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
