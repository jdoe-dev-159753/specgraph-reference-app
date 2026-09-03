package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Tag("VFY-CONFIDENTIALITY-001")
@Tag("VFY-FAILURE-PATHS-001")
final class AnalysisBackendConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisBackendConfiguration.class)
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
    void reservedLocalBackendFailsClosedUntilItsOwnedAdapterExists() {
        contextRunner
                .withPropertyValues("specgraph.analysis.backend=local")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Analysis backend LOCAL is not available");
                });
    }
}
