package dev.specgraph.reference.demo;
import java.util.UUID;
/** Application contract for optional, exactly replayable demonstration scenarios. */
public interface DemoScenarioUseCase {
    ScenarioResult generate(long seed, ScenarioFamily family);
    /** Coherent bounded synthetic story families available through the optional demo boundary. */
    enum ScenarioFamily { ORDINARY_LOCAL, CROSS_BORDER_GROWTH, MIXED_RED_FLAGS }
    /** Reviewer-visible provenance returned after a generated scenario is materialized. */
    record ScenarioResult(
            UUID customerId, String seed, ScenarioFamily family, String generatorIdentity,
            int activityCount, int riskEvidenceCount) {}
}
