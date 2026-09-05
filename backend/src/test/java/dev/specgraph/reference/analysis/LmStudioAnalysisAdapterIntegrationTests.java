package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.specgraph.reference.customer.Activity;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Tag("VFY-CONFIDENTIALITY-001")
@Tag("VFY-ANALYSIS-CONTRACT-001")
@Tag("VFY-FAILURE-PATHS-001")
final class LmStudioAnalysisAdapterIntegrationTests {
    private static final String MODEL = "ministral-3-8b-instruct-2512";
    private static final AtomicReference<String> RESPONSE = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST = new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final AtomicInteger REQUEST_COUNT = new AtomicInteger();
    private static HttpServer server;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisBackendConfiguration.class, LmStudioAnalysisConfiguration.class)
            .withBean(DeterministicAnalysisAdapter.class);

    @BeforeAll
    static void startServer() throws IOException {
        InetAddress ipv4Loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        server = HttpServer.create(new InetSocketAddress(ipv4Loopback, 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            REQUEST_PATH.set(exchange.getRequestURI().getPath());
            AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            REQUEST_COUNT.incrementAndGet();
            byte[] body = RESPONSE.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetServer() {
        REQUEST.set(null);
        REQUEST_PATH.set(null);
        AUTHORIZATION.set(null);
        REQUEST_COUNT.set(0);
    }

    @Test
    void selectsLocalBackendAndUsesStructuredOpenAiCompatibleRequestWithoutExternalCredential() {
        RESPONSE.set(chatCompletion("""
                {"riskLevel":"MEDIUM","findingsSummary":"Synthetic activity matches the supplied review policy.","recommendations":["Route the activity for manual review."]}
                """.trim()));
        AnalysisEvidenceEnvelope evidence = r5SizedEvidence();

        localContext().run(context -> {
            assertThat(context).hasNotFailed();
            AnalysisModelPort selected = context.getBean(AnalysisModelPort.class);
            assertThat(selected).isInstanceOf(LmStudioAnalysisAdapter.class);
            assertThat(context.getBeansOfType(SpringAiAnalysisAdapter.class)).isEmpty();
            assertThat(context.getBeansOfType(ChatModel.class))
                    .as("the local client is adapter-private and OpenAI auto-configuration remains disabled")
                    .isEmpty();

            AnalysisModelOutput output = selected.analyze(evidence);
            AnalysisGroundingValidator.validate(evidence, output.provenance());

            assertThat(output.result().riskLevel()).isEqualTo(AnalysisResult.RiskLevel.MEDIUM);
            assertThat(output.provenance().backendIdentity()).isEqualTo("local");
            assertThat(output.provenance().modelIdentity()).isEqualTo(MODEL);
            assertThat(output.provenance().promptIdentity()).isEqualTo(GroundedAnalysisPrompt.IDENTITY);
            assertThat(output.provenance().evidenceReferences())
                    .containsExactlyElementsOf(AnalysisEvidenceReferences.from(evidence));
            assertThat(output.provenance().metadata())
                    .containsEntry("runtime", "lmstudio/llama.cpp")
                    .containsEntry("externalTransmission", "false")
                    .containsEntry("dataPolicy", "synthetic-demo-only")
                    .containsEntry("structuredOutput", "provider-native+schema-validation")
                    .containsEntry("request.contextWindowTokens", "4096")
                    .containsEntry("request.maxOutputTokens", "512")
                    .containsEntry("request.transportMarginTokens", "256")
                    .containsEntry("request.tokenEstimator", "cl100k-plus-25-percent");
            int estimatedTotal = Integer.parseInt(
                    output.provenance().metadata().get("request.estimatedTotalTokens"));
            assertThat(estimatedTotal).isLessThanOrEqualTo(4096);
            assertThat(estimatedTotal).isEqualTo(
                    Integer.parseInt(output.provenance().metadata().get("request.estimatedSystemTokens"))
                            + Integer.parseInt(output.provenance().metadata().get("request.estimatedUserTokens"))
                            + Integer.parseInt(output.provenance().metadata().get("request.estimatedSchemaTokens"))
                            + 256
                            + 512);
        });

        assertThat(REQUEST_PATH).hasValue("/v1/chat/completions");
        assertThat(AUTHORIZATION.get()).isNull();
        assertThat(REQUEST_COUNT).hasValue(1);
        assertThat(REQUEST.get())
                .contains(
                        "\"model\":\"" + MODEL + "\"",
                        "\"max_tokens\":512",
                        "Always respond in English",
                        "SOURCE ACTIVITIES",
                        "beta-binomial-review-elevation-v1:posterior-review-elevation-rate",
                        "synthetic-policy:test",
                        "\"response_format\"",
                        "\"type\":\"json_schema\"",
                        "\"additionalProperties\":false",
                        "\"required\":[\"riskLevel\",\"findingsSummary\",\"recommendations\"]")
                .doesNotContain("\"maxLength\"", "\"minItems\"", "\"maxItems\"")
                .doesNotContain("4111111111111111", "AUTH-SECRET");
    }

    @Test
    void rejectsAnOversizedEvidenceEnvelopeBeforeSendingAnyRequest() {
        AnalysisEvidenceEnvelope source = SpringAiAnalysisAdapterTests.evidence();
        AnalysisEvidenceEnvelope oversized = new AnalysisEvidenceEnvelope(
                source.customerId(),
                source.totalActivityCount(),
                source.totalSourceRiskCount(),
                source.totalDetectorEvidenceCount(),
                source.totalPolicyEvidenceCount(),
                source.activities(),
                source.sourceRiskEvidence(),
                source.detectorEvidence(),
                List.of(new PolicyEvidence(
                        "synthetic-policy:oversized",
                        "oversized policy content ".repeat(2_000),
                        source.policyEvidence().getFirst().retrievalMetadata())));

        localContext().run(context -> assertThatThrownBy(() -> context.getBean(AnalysisModelPort.class)
                        .analyze(oversized))
                .isInstanceOf(InvalidAnalysisResultException.class)
                .hasMessageContaining("LM Studio request exceeds the configured context window")
                .hasMessageContaining("contextWindow=4096"));

        assertThat(REQUEST_COUNT).hasValue(0);
    }

    @Test
    void rejectsResponseThatViolatesTheApplicationSchema() {
        RESPONSE.set(chatCompletion("""
                {"riskLevel":"CRITICAL","findingsSummary":"Invalid.","recommendations":["Review."]}
                """.trim()));

        localContext().run(context -> assertThatThrownBy(() -> context.getBean(AnalysisModelPort.class)
                        .analyze(SpringAiAnalysisAdapterTests.evidence()))
                .isInstanceOf(RuntimeException.class));

        assertThat(REQUEST_COUNT.get()).isPositive();
    }

    @Test
    void rejectsResponseThatExceedsTheLiveProviderBounds() {
        RESPONSE.set(chatCompletion("""
                {"riskLevel":"LOW","findingsSummary":"%s","recommendations":["Review."]}
                """.formatted("x".repeat(AnalysisResultStructuredOutputConverter.MAX_FINDINGS_SUMMARY_CHARACTERS + 1))
                .trim()));

        localContext().run(context -> assertThatThrownBy(() -> context.getBean(AnalysisModelPort.class)
                        .analyze(SpringAiAnalysisAdapterTests.evidence()))
                .isInstanceOf(RuntimeException.class));

        assertThat(REQUEST_COUNT.get()).isPositive();
    }

    @Test
    void acceptsDetailedOperationalOutputWithinTheProviderTokenBudget() {
        String detailedSummary = "s".repeat(643);
        String detailedRecommendation = "r".repeat(257);
        RESPONSE.set(chatCompletion("""
                {"riskLevel":"MEDIUM","findingsSummary":"%s","recommendations":["%s"]}
                """.formatted(detailedSummary, detailedRecommendation).trim()));

        localContext().run(context -> {
            AnalysisModelOutput output = context.getBean(AnalysisModelPort.class)
                    .analyze(SpringAiAnalysisAdapterTests.evidence());

            assertThat(output.result().findingsSummary()).hasSize(643);
            assertThat(output.result().recommendations().getFirst()).hasSize(257);
        });

        assertThat(REQUEST_COUNT).hasValue(1);
    }

    private ApplicationContextRunner localContext() {
        return contextRunner.withPropertyValues(
                "specgraph.analysis.backend=local",
                "spring.ai.model.chat=local",
                "specgraph.analysis.local.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "specgraph.analysis.local.model=" + MODEL,
                "specgraph.analysis.local.api-key=",
                "specgraph.analysis.local.timeout=60s");
    }

    private static AnalysisEvidenceEnvelope r5SizedEvidence() {
        AnalysisEvidenceEnvelope source = SpringAiAnalysisAdapterTests.evidence();
        List<Activity> activities = new ArrayList<>(source.activities());
        activities.add(new Activity(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4"),
                Activity.ActivityType.CARD,
                new BigDecimal("4200.00"),
                "USD",
                "Declined",
                source.activities().getFirst().createdAt(),
                source.activities().getFirst().details()));
        activities.add(new Activity(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5"),
                Activity.ActivityType.PAYMENT,
                new BigDecimal("25000.00"),
                "EUR",
                "Completed",
                source.activities().get(1).createdAt(),
                source.activities().get(1).details()));
        List<RiskSignalEvidence> detectors = List.of(
                source.detectorEvidence().getFirst(),
                new RiskSignalEvidence(
                        "graded-review-fuzzy-v1", "fuzzy-review-elevation", 0.73, Map.of("scope", "synthetic")),
                new RiskSignalEvidence(
                        "random-forest-review-v1",
                        "random-forest-review-elevation-vote",
                        0.66,
                        Map.of("scope", "synthetic")));
        String policyContent = "Synthetic policy guidance for bounded human review only. ".repeat(18);
        List<PolicyEvidence> policies = List.of(
                source.policyEvidence().getFirst(),
                new PolicyEvidence("synthetic-policy:payment", policyContent, Map.of("rank", "2")),
                new PolicyEvidence("synthetic-policy:crypto", policyContent, Map.of("rank", "3")));
        return new AnalysisEvidenceEnvelope(
                source.customerId(),
                5,
                source.totalSourceRiskCount(),
                3,
                3,
                activities,
                source.sourceRiskEvidence(),
                detectors,
                policies);
    }

    private static String chatCompletion(String analysisJson) {
        String escaped = analysisJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id":"chatcmpl-local-test","object":"chat.completion","created":1,"model":"%s","choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(MODEL, escaped).trim();
    }
}
