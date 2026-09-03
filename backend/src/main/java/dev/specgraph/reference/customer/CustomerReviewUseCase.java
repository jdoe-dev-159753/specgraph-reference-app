package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;

/** Inbound application contract for the operator customer-review capability. */
public interface CustomerReviewUseCase {
    /** Complete snapshot retained for non-HTTP application consumers and compatibility. */
    Optional<CustomerSnapshot> findCustomer(UUID customerId);

    /** Bounded/filterable operator-facing view used by the HTTP review surface. */
    Optional<CustomerReviewPage> findCustomer(UUID customerId, CustomerReviewQuery query);
}
