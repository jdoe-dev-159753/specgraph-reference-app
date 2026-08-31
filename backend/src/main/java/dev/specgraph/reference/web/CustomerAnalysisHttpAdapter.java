package dev.specgraph.reference.web;

import dev.specgraph.reference.customer.CustomerReviewUseCase;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
final class CustomerAnalysisHttpAdapter {
    private final CustomerReviewUseCase customerReview;

    CustomerAnalysisHttpAdapter(CustomerReviewUseCase customerReview) {
        this.customerReview = customerReview;
    }

    @GetMapping("/{customerId}")
    ResponseEntity<CustomerSnapshot> customer(@PathVariable UUID customerId) {
        return customerReview.findCustomer(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
