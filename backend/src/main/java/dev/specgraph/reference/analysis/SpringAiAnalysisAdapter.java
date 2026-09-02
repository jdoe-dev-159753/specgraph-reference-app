package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("openai-model")
final class SpringAiAnalysisAdapter implements AnalysisModelPort {
    static final String BACKEND_IDENTITY = "openai";
    static final String PROMPT_IDENTITY = "openai-grounded-analysis-v1";

    private static final String SYSTEM_PROMPT = """
            You are an advisory customer-activity analyst operating only on synthetic demonstration data.
            Use only the evidence in the user message. Do not invent transactions, source risk assessments,
            detector signals or policy facts. Source risk assessments are persisted source evidence;
            detector signals are derived advisory evidence; retrieved policy is grounding context.
            Return a concise structured analysis with riskLevel LOW, MEDIUM or HIGH, a non-empty findingsSummary,
            and one or more concrete review recommendations. Do not allege criminal conduct and do not present
            generated conclusions as pre-existing source facts.
            """;

    private final ChatClient chatClient;
    private final String modelIdentity;

    SpringAiAnalysisAdapter(
            ChatModel chatModel,
            @Value("${spring.ai.openai.chat.model:gpt-5-mini}") String modelIdentity) {
        this.chatClient = ChatClient.create(chatModel);
        this.modelIdentity = modelIdentity;
    }

    @Override
    public AnalysisModelOutput analyze(AnalysisEvidenceEnvelope evidence) {
        AnalysisResult result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(renderBoundedEvidence(evidence))
                .call()
                .entity(AnalysisResult.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());

        return new AnalysisModelOutput(
                result,
                new AnalysisModelProvenance(
                        BACKEND_IDENTITY,
                        modelIdentity,
                        PROMPT_IDENTITY,
                        AnalysisEvidenceReferences.from(evidence),
                        Map.of(
                                "externalTransmission", "true",
                                "dataPolicy", "synthetic-demo-only",
                                "structuredOutput", "provider-native+schema-validation")));
    }

    private String renderBoundedEvidence(AnalysisEvidenceEnvelope evidence) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("customerId=").append(evidence.snapshot().customerId()).append('\n');

        prompt.append("\nSOURCE ACTIVITIES\n");
        evidence.snapshot().activities().forEach(activity -> prompt
                .append("- transactionId=").append(activity.transactionId())
                .append(" type=").append(activity.type())
                .append(" amount=").append(activity.amount().toPlainString())
                .append(' ').append(activity.currency())
                .append(" status=").append(activity.status())
                .append(" createdAt=").append(activity.createdAt())
                .append(" details=").append(safeActivityDetails(activity))
                .append('\n'));

        prompt.append("\nSOURCE RISK ASSESSMENTS\n");
        evidence.snapshot().riskEvidence().forEach(risk -> prompt
                .append("- assessmentId=").append(risk.assessmentId())
                .append(" transactionId=").append(risk.transactionId())
                .append(" ruleId=").append(risk.ruleId())
                .append(" ruleName=").append(risk.ruleName())
                .append(" scoreContribution=").append(risk.scoreContribution())
                .append('\n'));

        prompt.append("\nDERIVED DETECTOR SIGNALS\n");
        evidence.detectorEvidence().forEach(signal -> prompt
                .append("- artifactId=").append(signal.artifactIdentity())
                .append(" detector=").append(signal.detectorIdentity())
                .append(" signal=").append(signal.signalIdentity())
                .append(" score=").append(String.format(Locale.ROOT, "%.6f", signal.score()))
                .append(" provenance=").append(signal.provenance())
                .append('\n'));

        prompt.append("\nRETRIEVED SYNTHETIC POLICY\n");
        evidence.policyEvidence().forEach(policy -> prompt
                .append("- artifactId=").append(policy.artifactIdentity())
                .append(" content=").append(policy.content())
                .append(" retrievalMetadata=").append(policy.retrievalMetadata())
                .append('\n'));

        return prompt.toString();
    }

    private String safeActivityDetails(Activity activity) {
        return switch (activity.details()) {
            case Activity.CardDetails card -> "cardType=" + card.cardType()
                    + ",merchantName=" + card.merchantName()
                    + ",mccCode=" + card.mccCode()
                    + ",cardPresent=" + card.cardPresent()
                    + ",declineReason=" + valueOrDash(card.declineReason());
            case Activity.PaymentDetails payment -> "paymentMethod=" + payment.paymentMethod()
                    + ",receiverBankCountry=" + payment.receiverBankCountry();
            case Activity.CryptoDetails crypto -> "blockchain=" + crypto.blockchain()
                    + ",exchangeName=" + valueOrDash(crypto.exchangeName());
        };
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
