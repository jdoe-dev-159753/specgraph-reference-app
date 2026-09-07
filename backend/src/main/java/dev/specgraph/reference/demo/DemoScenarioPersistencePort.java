package dev.specgraph.reference.demo;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.time.Instant;
/** Atomically retains optional generated source-shaped demo evidence. */
public interface DemoScenarioPersistencePort {
    void save(ScenarioData scenario);
    /** Immutable persistence command carrying seed, generator identity, anchor, and generated source snapshot. */
    record ScenarioData(
            long seed, DemoScenarioUseCase.ScenarioFamily family, String generatorIdentity,
            Instant scenarioAnchor, CustomerSnapshot snapshot) {}
}
