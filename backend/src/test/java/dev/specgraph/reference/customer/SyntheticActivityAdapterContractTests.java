package dev.specgraph.reference.customer;

import org.junit.jupiter.api.Tag;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("port_contract")
/** Applies the shared customer port contract to the deterministic in-memory baseline adapter. */
final class SyntheticActivityAdapterContractTests extends CustomerActivityPortContract {
    private final CustomerActivityPort adapter = new SyntheticActivityAdapter();

    @Override
    protected CustomerActivityPort activityPort() {
        return adapter;
    }
}
