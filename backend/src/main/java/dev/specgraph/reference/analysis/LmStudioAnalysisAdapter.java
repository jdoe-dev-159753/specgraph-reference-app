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
    private final LmStudioPromptBudget promptBudget;

    LmStudioAnalysisAdapter(
            ChatModel chatModel,
            String modelIdentity,
            LmStudioPromptBudget promptBudget) {
        this.chatClient = ChatClient.create(chatModel);
        this.modelIdentity = modelIdentity;
        this.promptBudget = promptBudget;
    }

    @Override
    public AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence) {
        String userPrompt = GroundedAnalysisPrompt.render(evidence);
        LmStudioPromptBudget.RequestBudget requestBudget = promptBudget.requireFits(userPrompt);
        AnalysisResult result = chatClient.prompt()
                .system(GroundedAnalysisPrompt.SYSTEM)
                .user(userPrompt)
                .call()
                .entity(AnalysisResultStructuredOutputConverter.INSTANCE, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());

        Map<String, String> metadata = new LinkedHashMap<>(evidence.contextDiagnostics());
        metadata.put("runtime", RUNTIME_IDENTITY);
        metadata.put("externalTransmission", "false");
        metadata.put("dataPolicy", "synthetic-demo-only");
        metadata.put("structuredOutput", "provider-native+schema-validation");
        metadata.put("request.contextWindowTokens", Integer.toString(requestBudget.contextWindowTokens()));
        metadata.put("request.estimatedSystemTokens", Integer.toString(requestBudget.systemTokens()));
        metadata.put("request.estimatedUserTokens", Integer.toString(requestBudget.userTokens()));
        metadata.put("request.estimatedSchemaTokens", Integer.toString(requestBudget.schemaTokens()));
        metadata.put("request.transportMarginTokens", Integer.toString(requestBudget.transportMarginTokens()));
        metadata.put("request.maxOutputTokens", Integer.toString(requestBudget.maxOutputTokens()));
        metadata.put("request.estimatedTotalTokens", Integer.toString(requestBudget.estimatedTotalTokens()));
        metadata.put("request.tokenEstimator", "cl100k-plus-25-percent");

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
