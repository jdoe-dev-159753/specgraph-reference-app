package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;

/** Outbound query boundary dedicated to bounded operator-facing review. */
public interface CustomerReviewQueryPort {
    Optional<CustomerReviewPage> loadReviewPage(UUID customerId, CustomerReviewQuery query);
}
