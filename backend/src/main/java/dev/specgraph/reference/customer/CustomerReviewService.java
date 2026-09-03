package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application-service facade between inbound adapters and outbound customer review ports. */
@Service
final class CustomerReviewService implements CustomerReviewUseCase {
    private final CustomerActivityPort activityPort;
    private final CustomerReviewQueryPort reviewQueryPort;

    CustomerReviewService(CustomerActivityPort activityPort, CustomerReviewQueryPort reviewQueryPort) {
        this.activityPort = activityPort;
        this.reviewQueryPort = reviewQueryPort;
    }

    @Override
    public Optional<CustomerSnapshot> findCustomer(UUID customerId) {
        return activityPort.loadSnapshot(customerId);
    }

    @Override
    public Optional<CustomerReviewPage> findCustomer(UUID customerId, CustomerReviewQuery query) {
        return reviewQueryPort.loadReviewPage(customerId, query);
    }
}
