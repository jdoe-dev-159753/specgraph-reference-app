package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Tag("VFY-CONFIDENTIALITY-001")
@Tag("VFY-FAILURE-PATHS-001")
/**
 * Proves fail-closed backend binding and that only explicit typed selection may enable transmission.
 * Application-context fixtures validate wiring policy, not provider availability.
 */
final class AnalysisBackendConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisBackendConfiguration.class, LmStudioAnalysisConfiguration.class)
            .withBean(DeterministicAnalysisAdapter.class);

    @Test
    void invalidBackendIdentifierFailsDuringConfigurationBinding() {
        contextRunner
                .withPropertyValues("specgraph.analysis.backend=unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("AnalysisBackendProperties")
                            .hasStackTraceContaining("specgraph.analysis.backend");
                });
    }

    @Test
    void localBackendWithoutEndpointFailsClosed() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=local",
                        "spring.ai.model.chat=local",
                        "specgraph.analysis.local.model=ministral-test",
                        "specgraph.analysis.local.timeout=60s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("SPECGRAPH_LOCAL_BASE_URL is required");
                });
    }

    @Test
    void localBackendWithInvalidEndpointFailsClosed() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=local",
                        "spring.ai.model.chat=local",
                        "specgraph.analysis.local.base-url=https://api.openai.com/v1",
                        "specgraph.analysis.local.model=ministral-test")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("must target a loopback or private-network LM Studio IP literal");
                });
    }

    @Test
    void localBackendRejectsAContextWindowThatCannotHoldItsFixedRequestEnvelope() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=local",
                        "spring.ai.model.chat=local",
                        "specgraph.analysis.local.base-url=http://127.0.0.1:1234/v1",
                        "specgraph.analysis.local.model=ministral-test",
                        "specgraph.analysis.local.budget.context-window-tokens=512",
                        "specgraph.analysis.local.budget.max-output-tokens=256",
                        "specgraph.analysis.local.budget.transport-margin-tokens=128")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("fixed request envelope exceeds the configured context window");
                });
    }

    @Test
    void localBackendAcceptsPrivateIpv6Literals() {
        assertThat(new LmStudioAnalysisProperties(
                                "http://[::1]:1234/v1", "ministral-test", "", Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isEqualTo("http://[::1]:1234/v1");
        assertThat(new LmStudioAnalysisProperties(
                                "http://[fd00::1]:1234/v1", "ministral-test", "", Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isEqualTo("http://[fd00::1]:1234/v1");
        assertThat(new LmStudioAnalysisProperties(
                                "http://[0:0:0:0:0:0:0:1]:1234/v1",
                                "ministral-test",
                                "",
                                Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isEqualTo("http://[0:0:0:0:0:0:0:1]:1234/v1");
        assertThat(new LmStudioAnalysisProperties(
                                "http://[fe80::1%25Ethernet]:1234/v1",
                                "ministral-test",
                                "",
                                Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isEqualTo("http://[fe80::1%25Ethernet]:1234/v1");
    }

    @Test
    void localBackendRejectsHostnamesToPreventDnsRebinding() {
        assertThatThrownBy(() -> new LmStudioAnalysisProperties(
                                "http://localhost:1234/v1", "ministral-test", "", Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must target a loopback or private-network LM Studio IP literal");
        assertThatThrownBy(() -> new LmStudioAnalysisProperties(
                                "http://lmstudio.internal:1234/v1",
                                "ministral-test",
                                "",
                                Duration.ofSeconds(60))
                        .validatedBaseUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must target a loopback or private-network LM Studio IP literal");
    }

    @Test
    void inactiveLocalPropertiesAreNotBound() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=deterministic",
                        "spring.ai.model.chat=deterministic",
                        "specgraph.analysis.local.timeout=not-a-duration")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(LmStudioAnalysisProperties.class)).isEmpty();
                });
    }

    @Test
    void externallyEnablingOpenAiCannotOverrideDeterministicBackendSelection() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=deterministic",
                        "spring.ai.model.chat=openai")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("spring.ai.model.chat must match")
                            .hasStackTraceContaining("expected deterministic, got openai");
                });
    }

    @Test
    void externallyDisablingChatCannotOverrideOpenAiBackendSelection() {
        contextRunner
                .withPropertyValues(
                        "specgraph.analysis.backend=openai",
                        "spring.ai.model.chat=none")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("spring.ai.model.chat must match")
                            .hasStackTraceContaining("expected openai, got none");
                });
    }
}
