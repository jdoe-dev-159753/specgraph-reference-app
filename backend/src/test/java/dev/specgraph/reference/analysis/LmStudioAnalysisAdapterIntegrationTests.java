package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
        AnalysisEvidenceEnvelope evidence = SpringAiAnalysisAdapterTests.evidence();

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
                    .containsEntry("structuredOutput", "provider-native+schema-validation");
        });

        assertThat(REQUEST_PATH).hasValue("/v1/chat/completions");
        assertThat(AUTHORIZATION.get()).isNull();
        assertThat(REQUEST_COUNT).hasValue(1);
        assertThat(REQUEST.get())
                .contains(
                        "\"model\":\"" + MODEL + "\"",
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
                """.formatted("x".repeat(501)).trim()));

        localContext().run(context -> assertThatThrownBy(() -> context.getBean(AnalysisModelPort.class)
                        .analyze(SpringAiAnalysisAdapterTests.evidence()))
                .isInstanceOf(RuntimeException.class));

        assertThat(REQUEST_COUNT.get()).isPositive();
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

    private static String chatCompletion(String analysisJson) {
        String escaped = analysisJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {"id":"chatcmpl-local-test","object":"chat.completion","created":1,"model":"%s","choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(MODEL, escaped).trim();
    }
}
