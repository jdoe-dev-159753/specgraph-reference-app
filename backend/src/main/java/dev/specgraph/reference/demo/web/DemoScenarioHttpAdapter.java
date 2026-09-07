package dev.specgraph.reference.demo.web;

import dev.specgraph.reference.demo.DemoScenarioUseCase;
import dev.specgraph.reference.demo.DemoScenarioUseCase.ScenarioFamily;
import dev.specgraph.reference.demo.DemoScenarioUseCase.ScenarioResult;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conditional input adapter for the optional replayable-scenario boundary. */
@RestController
@RequestMapping("/api/demo/scenarios")
@ConditionalOnProperty(name = "specgraph.demo.scenarios.enabled", havingValue = "true")
final class DemoScenarioHttpAdapter {
    private final DemoScenarioUseCase scenarios;
    DemoScenarioHttpAdapter(DemoScenarioUseCase scenarios) { this.scenarios = scenarios; }
    /** Validates replay input and materializes one generated scenario through the application use case. */
    @PostMapping
    ResponseEntity<ScenarioResponse> generate(@RequestBody GenerateScenarioRequest request) {
        if (request.family() == null) return ResponseEntity.badRequest().build();
        long seed;
        try {
            seed = Long.parseLong(request.seed());
        } catch (NumberFormatException exception) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ScenarioResponse.from(scenarios.generate(seed, request.family())));
    }
    /** HTTP request contract retaining the seed as text so malformed values fail explicitly. */
    record GenerateScenarioRequest(String seed, ScenarioFamily family) {}
    /** HTTP response exposing replay identity and generated-evidence counts to the reviewer. */
    record ScenarioResponse(
            UUID customerId, String seed, ScenarioFamily family, String generatorIdentity,
            int activityCount, int riskEvidenceCount) {
        private static ScenarioResponse from(ScenarioResult result) {
            return new ScenarioResponse(result.customerId(), result.seed(), result.family(),
                    result.generatorIdentity(), result.activityCount(), result.riskEvidenceCount());
        }
    }
}
