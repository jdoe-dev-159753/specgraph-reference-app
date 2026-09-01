package dev.specgraph.reference.customer.web;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerReviewUseCase;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
final class CustomerReviewHttpAdapter {
    private final CustomerReviewUseCase customerReview;

    CustomerReviewHttpAdapter(CustomerReviewUseCase customerReview) {
        this.customerReview = customerReview;
    }

    @GetMapping("/{customerId}")
    ResponseEntity<CustomerSnapshotResponse> customer(@PathVariable UUID customerId) {
        return customerReview.findCustomer(customerId)
                .map(CustomerSnapshotResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record CustomerSnapshotResponse(
            UUID customerId,
            List<ActivityResponse> activities,
            List<RiskEvidence> riskEvidence) {
        static CustomerSnapshotResponse from(CustomerSnapshot snapshot) {
            return new CustomerSnapshotResponse(
                    snapshot.customerId(),
                    snapshot.activities().stream().map(ActivityResponse::from).toList(),
                    snapshot.riskEvidence());
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
