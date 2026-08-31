package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;

/** Inbound application contract for the operator customer-review capability. */
public interface CustomerReviewUseCase {
    Optional<CustomerSnapshot> findCustomer(UUID customerId);
}
