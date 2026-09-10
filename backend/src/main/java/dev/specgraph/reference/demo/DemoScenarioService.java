package dev.specgraph.reference.demo;

import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.demo.DemoScenarioPersistencePort.ScenarioData;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Persists one generated replayable scenario and returns its reviewer-visible summary. */
@Service
final class DemoScenarioService implements DemoScenarioUseCase {
    private final DemoScenarioPersistencePort persistence;
    private final DemoScenarioGenerator generator;

    DemoScenarioService(DemoScenarioPersistencePort persistence) {
        this(persistence, new DemoScenarioGenerator());
    }

    DemoScenarioService(DemoScenarioPersistencePort persistence, DemoScenarioGenerator generator) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public ScenarioResult generate(long seed, ScenarioFamily family) {
        ScenarioData scenario = generator.generate(seed, family);
        persistence.save(scenario);
        CustomerSnapshot snapshot = scenario.snapshot();
        return new ScenarioResult(
                snapshot.customerId(),
                Long.toString(seed),
                family,
                scenario.generatorIdentity(),
                snapshot.activities().size(),
                snapshot.riskEvidence().size());
    }
}
