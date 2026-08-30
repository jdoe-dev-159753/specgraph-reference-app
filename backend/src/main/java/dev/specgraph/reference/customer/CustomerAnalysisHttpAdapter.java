package dev.specgraph.reference.customer;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerAnalysisHttpAdapter {
    private final CustomerActivityPort activityPort;

    CustomerAnalysisHttpAdapter(CustomerActivityPort activityPort) {
        this.activityPort = activityPort;
    }

    @GetMapping("/{customerId}")
    ResponseEntity<CustomerSnapshot> customer(@PathVariable UUID customerId) {
        return activityPort.loadSnapshot(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
