package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerActivityPort {
    Optional<CustomerSnapshot> loadSnapshot(UUID customerId);
}
