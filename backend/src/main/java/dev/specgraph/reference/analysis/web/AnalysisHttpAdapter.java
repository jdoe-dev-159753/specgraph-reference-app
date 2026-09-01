package dev.specgraph.reference.analysis.web;

import dev.specgraph.reference.analysis.AnalysisFailureException;
import dev.specgraph.reference.analysis.AnalysisHistoryEntry;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.analysis.AnalysisUseCase;
import dev.specgraph.reference.analysis.PolicyEvidence;
import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/analyses")
final class AnalysisHttpAdapter {
    private static final OperatorId R3_DEMO_OPERATOR = new OperatorId("r3-demo-operator");

    private final AnalysisUseCase analysis;

    AnalysisHttpAdapter(AnalysisUseCase analysis) {
        this.analysis = analysis;
    }

    @PostMapping
    ResponseEntity<AnalysisResponse> analyze(@PathVariable UUID customerId) {
        AnalysisHistoryEntry completed = analysis.analyze(customerId, R3_DEMO_OPERATOR);
        return ResponseEntity.status(HttpStatus.CREATED).body(AnalysisResponse.from(completed));
    }

    @GetMapping
    List<AnalysisResponse> history(@PathVariable UUID customerId) {
        return analysis.listHistory(customerId).stream().map(AnalysisResponse::from).toList();
    }

    @GetMapping("/{analysisId}")
    ResponseEntity<AnalysisResponse> historyEntry(
            @PathVariable UUID customerId,
            @PathVariable UUID analysisId) {
        return analysis.findHistory(customerId, analysisId)
                .map(AnalysisResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(AnalysisFailureException.class)
    ResponseEntity<ProblemDetail> analysisFailure(AnalysisFailureException exception) {
        HttpStatus status = switch (exception.reason()) {
            case CUSTOMER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INSUFFICIENT_GROUNDING -> HttpStatus.UNPROCESSABLE_ENTITY;
            case GROUNDING_FAILURE, MODEL_FAILURE, INVALID_RESULT -> HttpStatus.BAD_GATEWAY;
            case PERSISTENCE_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle("Analysis request failed");
        problem.setProperty("reason", exception.reason().name());
        return ResponseEntity.status(status).body(problem);
    }

    record AnalysisResponse(
            UUID analysisId,
            UUID customerId,
            String operatorId,
            Instant generatedAt,
            AnalysisResult.RiskLevel riskLevel,
            String findingsSummary,
            List<String> recommendations,
            List<PolicyEvidence> evidenceProvenance) {
        static AnalysisResponse from(AnalysisHistoryEntry entry) {
            return new AnalysisResponse(
                    entry.analysisId(),
                    entry.customerId(),
                    entry.operatorId().value(),
                    entry.generatedAt(),
                    entry.result().riskLevel(),
                    entry.result().findingsSummary(),
                    entry.result().recommendations(),
                    entry.evidenceProvenance());
        }
    }
}
