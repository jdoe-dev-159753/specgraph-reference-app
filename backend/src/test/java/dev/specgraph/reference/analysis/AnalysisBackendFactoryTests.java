package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-ANALYSIS-CONTRACT-001")
final class AnalysisBackendFactoryTests {

    @Test
    void resolvesConfiguredBackendWithoutProviderConditionalsInApplicationCode() {
        AnalysisModelPort deterministic = evidence -> null;
        AnalysisModelPort openAi = evidence -> null;
        var adapters = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        adapters.put(AnalysisBackendId.DETERMINISTIC, () -> deterministic);
        adapters.put(AnalysisBackendId.OPENAI, () -> openAi);
        var factory = new AnalysisBackendFactory(adapters);

        assertThat(factory.resolve(AnalysisBackendId.DETERMINISTIC)).isSameAs(deterministic);
        assertThat(factory.resolve(AnalysisBackendId.OPENAI)).isSameAs(openAi);
    }

    @Test
    void unsupportedLocalBackendFailsClosedUntilItsAdapterExists() {
        var adapters = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        adapters.put(AnalysisBackendId.DETERMINISTIC, () -> evidence -> null);
        var factory = new AnalysisBackendFactory(adapters);

        assertThatThrownBy(() -> factory.resolve(AnalysisBackendId.LOCAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOCAL")
                .hasMessageContaining("not available");
    }

    @Test
    void selectedBackendWithUnavailableConditionalAdapterFailsClosed() {
        var adapters = new EnumMap<AnalysisBackendId, Supplier<? extends AnalysisModelPort>>(AnalysisBackendId.class);
        adapters.put(AnalysisBackendId.OPENAI, () -> null);
        var factory = new AnalysisBackendFactory(adapters);

        assertThatThrownBy(() -> factory.resolve(AnalysisBackendId.OPENAI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI")
                .hasMessageContaining("adapter is unavailable");
    }
}
