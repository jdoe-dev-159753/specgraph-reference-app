package dev.specgraph.reference.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.demo.DemoScenarioPersistencePort.ScenarioData;
import dev.specgraph.reference.demo.DemoScenarioUseCase.ScenarioFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that the application service only coordinates generation, persistence and summary projection. */
class DemoScenarioServiceTests {
    @Test
    void persistsGeneratedScenarioAndReturnsItsSummary() {
        List<ScenarioData> saved = new ArrayList<>();
        DemoScenarioService service = new DemoScenarioService(saved::add, new DemoScenarioGenerator());

        DemoScenarioUseCase.ScenarioResult result = service.generate(41, ScenarioFamily.ORDINARY_LOCAL);

        assertThat(saved).hasSize(1);
        ScenarioData scenario = saved.getFirst();
        assertThat(result.customerId()).isEqualTo(scenario.snapshot().customerId());
        assertThat(result.seed()).isEqualTo("41");
        assertThat(result.family()).isEqualTo(ScenarioFamily.ORDINARY_LOCAL);
        assertThat(result.generatorIdentity()).isEqualTo(scenario.generatorIdentity());
        assertThat(result.activityCount()).isEqualTo(scenario.snapshot().activities().size());
        assertThat(result.riskEvidenceCount()).isEqualTo(scenario.snapshot().riskEvidence().size());
    }
}
