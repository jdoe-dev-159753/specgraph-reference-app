package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound boundary for loading the complete source-evidence snapshot used by analysis.
 *
 * <p>An empty value means that the customer does not exist. Implementations must keep retrieved
 * activities and source risk evidence distinct from any detector output created later in the
 * analysis pipeline.
 */
public interface CustomerActivityPort {
    /** Loads the complete snapshot for a customer, or an empty value when it is unknown. */
    Optional<CustomerSnapshot> loadSnapshot(UUID customerId);
}
