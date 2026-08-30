package dev.specgraph.reference.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SyntheticActivityAdapter implements CustomerActivityPort {
    @Override
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        return Optional.empty();
    }
}
