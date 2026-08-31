package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application-service facade between inbound adapters and the outbound activity port. */
@Service
final class CustomerReviewService implements CustomerReviewUseCase {
    private final CustomerActivityPort activityPort;

    CustomerReviewService(CustomerActivityPort activityPort) {
        this.activityPort = activityPort;
    }

    @Override
    public Optional<CustomerSnapshot> findCustomer(UUID customerId) {
        return activityPort.loadSnapshot(customerId);
    }
}
