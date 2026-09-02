package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@Tag("VFY-ANALYSIS-CONTRACT-001")
@Tag("VFY-CONFIDENTIALITY-001")
final class SpringAiAnalysisAdapterTests {
    @Test
    void convertsStructuredResponseAndBuildsProvenanceFromTheSubmittedEnvelope() {
        AtomicReference<Prompt> capturedPrompt = new AtomicReference<>();
        ChatModel fakeModel = prompt -> {
            capturedPrompt.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                    {"riskLevel":"MEDIUM","findingsSummary":"Synthetic grounded finding.","recommendations":["Review the retained evidence."]}
                    """))));
        };
        var adapter = new SpringAiAnalysisAdapter(fakeModel, "synthetic-openai-model");
        AnalysisEvidenceEnvelope evidence = evidence();

        AnalysisModelOutput output = adapter.analyze(evidence);

        assertThat(output.result().riskLevel()).isEqualTo(AnalysisResult.RiskLevel.MEDIUM);
        assertThat(output.result().findingsSummary()).isEqualTo("Synthetic grounded finding.");
        assertThat(output.result().recommendations()).containsExactly("Review the retained evidence.");
        assertThat(output.provenance().backendIdentity()).isEqualTo("openai");
        assertThat(output.provenance().modelIdentity()).isEqualTo("synthetic-openai-model");
        assertThat(output.provenance().promptIdentity()).isEqualTo("openai-grounded-analysis-v1");
        assertThat(output.provenance().metadata())
                .containsEntry("externalTransmission", "true")
                .containsEntry("dataPolicy", "synthetic-demo-only");
        assertThat(output.provenance().evidenceReferences())
                .containsExactlyElementsOf(AnalysisEvidenceReferences.from(evidence));

        String submitted = capturedPrompt.get().getUserMessage().getText();
        assertThat(submitted)
                .contains("SOURCE ACTIVITIES", "SOURCE RISK ASSESSMENTS", "DERIVED DETECTOR SIGNALS", "RETRIEVED SYNTHETIC POLICY")
                .contains("Synthetic Merchant", "receiverBankCountry=DE", "blockchain=Ethereum", "synthetic-policy:test")
                .doesNotContain(
                        "4111111111111111",
                        "AUTH-SECRET",
                        "CH93-SECRET-SENDER",
                        "DE89-SECRET-RECEIVER",
                        "0xSECRET_FROM",
                        "0xSECRET_TO",
                        "SECRET_TX_HASH");
    }

    private AnalysisEvidenceEnvelope evidence() {
        UUID cardId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID paymentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
        UUID cryptoId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
        List<Activity> activities = List.of(
                new Activity(
                        cardId,
                        Activity.ActivityType.CARD,
                        new BigDecimal("248.50"),
                        "CHF",
                        "Declined",
                        Instant.parse("2026-08-30T08:00:00Z"),
                        new Activity.CardDetails(
                                "4111111111111111",
                                "VISA",
                                "Synthetic Merchant",
                                "5734",
                                false,
                                "AUTH-SECRET",
                                "Synthetic decline")),
                new Activity(
                        paymentId,
                        Activity.ActivityType.PAYMENT,
                        new BigDecimal("1250.00"),
                        "CHF",
                        "Completed",
                        Instant.parse("2026-08-30T08:01:00Z"),
                        new Activity.PaymentDetails(
                                "BANK_TRANSFER",
                                "CH93-SECRET-SENDER",
                                "DE89-SECRET-RECEIVER",
                                "DE")),
                new Activity(
                        cryptoId,
                        Activity.ActivityType.CRYPTO,
                        new BigDecimal("1.25"),
                        "ETH",
                        "Completed",
                        Instant.parse("2026-08-30T08:02:00Z"),
                        new Activity.CryptoDetails(
                                "Ethereum",
                                "0xSECRET_FROM",
                                "0xSECRET_TO",
                                "SECRET_TX_HASH",
                                "Synthetic Exchange")));
        RiskEvidence sourceRisk = new RiskEvidence(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                cardId,
                "SRC-RULE-1",
                "Synthetic source rule",
                Instant.parse("2026-08-30T08:03:00Z"),
                new BigDecimal("0.30"));
        RiskSignalEvidence detector = new RiskSignalEvidence(
                "beta-binomial-review-elevation-v1",
                "posterior-review-elevation-rate",
                0.82,
                Map.of("posterior", "6.000000,4.000000"));
        PolicyEvidence policy = new PolicyEvidence(
                "synthetic-policy:test",
                "Synthetic policy says review context before escalation.",
                Map.of("adapter", "test"));
        return new AnalysisEvidenceEnvelope(
                new CustomerSnapshot(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        activities,
                        List.of(sourceRisk)),
                List.of(detector),
                List.of(policy));
    }
}
