package dev.specgraph.reference.customer.web;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerReviewPage;
import dev.specgraph.reference.customer.CustomerReviewQuery;
import dev.specgraph.reference.customer.CustomerReviewUseCase;
import dev.specgraph.reference.risk.RiskEvidence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
final class CustomerReviewHttpAdapter {
    private final CustomerReviewUseCase customerReview;

    CustomerReviewHttpAdapter(CustomerReviewUseCase customerReview) {
        this.customerReview = customerReview;
    }

    @GetMapping("/{customerId}")
    ResponseEntity<CustomerSnapshotResponse> customer(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) Activity.ActivityType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo) {
        CustomerReviewQuery query;
        try {
            query = new CustomerReviewQuery(page, pageSize, type, status, createdFrom, createdTo);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }

        return customerReview.findCustomer(customerId, query)
                .map(CustomerSnapshotResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record CustomerSnapshotResponse(
            UUID customerId,
            List<ActivityResponse> activities,
            List<RiskEvidence> riskEvidence,
            int page,
            int pageSize,
            long totalActivities,
            long totalRiskEvidence,
            long totalPages,
            boolean hasPrevious,
            boolean hasNext) {
        static CustomerSnapshotResponse from(CustomerReviewPage reviewPage) {
            return new CustomerSnapshotResponse(
                    reviewPage.customerId(),
                    reviewPage.activities().stream().map(ActivityResponse::from).toList(),
                    reviewPage.riskEvidence(),
                    reviewPage.page(),
                    reviewPage.pageSize(),
                    reviewPage.totalActivities(),
                    reviewPage.totalRiskEvidence(),
                    reviewPage.totalPages(),
                    reviewPage.hasPrevious(),
                    reviewPage.hasNext());
        }
    }

    record ActivityResponse(
            UUID transactionId,
            Activity.ActivityType type,
            String amount,
            String currency,
            String status,
            Instant createdAt,
            Activity.ActivityDetails details) {
        static ActivityResponse from(Activity activity) {
            return new ActivityResponse(
                    activity.transactionId(),
                    activity.type(),
                    activity.amount().toPlainString(),
                    activity.currency(),
                    activity.status(),
                    activity.createdAt(),
                    activity.details());
        }
    }
}
