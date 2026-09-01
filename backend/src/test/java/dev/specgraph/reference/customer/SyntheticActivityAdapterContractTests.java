package dev.specgraph.reference.customer;

import org.junit.jupiter.api.Tag;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("port_contract")
final class SyntheticActivityAdapterContractTests extends CustomerActivityPortContract {
    private final CustomerActivityPort adapter = new SyntheticActivityAdapter();

    @Override
    protected CustomerActivityPort activityPort() {
        return adapter;
    }
}
