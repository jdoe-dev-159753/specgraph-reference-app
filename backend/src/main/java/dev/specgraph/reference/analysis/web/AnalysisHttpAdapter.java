package dev.specgraph.reference.analysis.web;

import dev.specgraph.reference.analysis.AnalysisFailureException;
import dev.specgraph.reference.analysis.AnalysisHistoryEntry;
import dev.specgraph.reference.analysis.AnalysisHistoryPage;
import dev.specgraph.reference.analysis.AnalysisHistoryQuery;
import dev.specgraph.reference.analysis.AnalysisModelProvenance;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.analysis.AnalysisUseCase;
import dev.specgraph.reference.analysis.PolicyEvidence;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.identity.OperatorContextPort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/analyses")
final class AnalysisHttpAdapter {
    private final AnalysisUseCase analysis;
    private final OperatorContextPort operatorContext;

    AnalysisHttpAdapter(AnalysisUseCase analysis, OperatorContextPort operatorContext) {
        this.analysis = analysis;
        this.operatorContext = operatorContext;
    }

    @PostMapping
    ResponseEntity<AnalysisResponse> analyze(@PathVariable UUID customerId) {
        AnalysisHistoryEntry completed = analysis.analyze(customerId, operatorContext.requireAuthenticated());
        return ResponseEntity.status(HttpStatus.CREATED).body(AnalysisResponse.from(completed));
    }

    @GetMapping
    ResponseEntity<List<AnalysisResponse>> history(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AnalysisHistoryQuery query;
        try {
            query = new AnalysisHistoryQuery(page, pageSize);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        AnalysisHistoryPage historyPage = analysis.listHistory(customerId, query);
        return ResponseEntity.ok()
                .header("X-Page", Integer.toString(historyPage.page()))
                .header("X-Page-Size", Integer.toString(historyPage.pageSize()))
                .header("X-Total-Count", Long.toString(historyPage.totalEntries()))
                .header("X-Total-Pages", Long.toString(historyPage.totalPages()))
                .header("X-Has-Previous", Boolean.toString(historyPage.hasPrevious()))
                .header("X-Has-Next", Boolean.toString(historyPage.hasNext()))
                .body(historyPage.entries().stream().map(AnalysisResponse::from).toList());
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
            case DETECTOR_FAILURE, GROUNDING_FAILURE, MODEL_FAILURE, INVALID_RESULT -> HttpStatus.BAD_GATEWAY;
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
            List<PolicyEvidence> evidenceProvenance,
            List<RiskSignalEvidence> detectorProvenance,
            AnalysisModelProvenance modelProvenance) {
        static AnalysisResponse from(AnalysisHistoryEntry entry) {
            return new AnalysisResponse(
                    entry.analysisId(),
                    entry.customerId(),
                    entry.operatorId().value(),
                    entry.generatedAt(),
                    entry.result().riskLevel(),
                    entry.result().findingsSummary(),
                    entry.result().recommendations(),
                    entry.evidenceProvenance(),
                    entry.detectorProvenance(),
                    entry.modelProvenance());
        }
    }
}
