package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("VFY-REPRODUCIBILITY-001")
@Tag("unit_property")
final class SyntheticActivityInvariantTests {
    @Test
    void freshAdaptersProduceTheSameDeterministicSnapshot() {
        var first = new SyntheticActivityAdapter().loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID);
        var second = new SyntheticActivityAdapter().loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID);

        assertThat(first).isPresent();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void eachActivityCarriesOnlyItsMatchingSourceSpecialization() {
        CustomerSnapshot snapshot = new SyntheticActivityAdapter()
                .loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                .orElseThrow();

        for (Activity activity : snapshot.activities()) {
            Set<String> detailKeys = activity.details().keySet();
            switch (activity.type()) {
                case CARD -> {
                    assertThat(detailKeys).contains("cardPan", "merchantName", "cardPresent");
                    assertThat(detailKeys).doesNotContain("paymentMethod", "blockchain");
                }
                case PAYMENT -> {
                    assertThat(detailKeys).contains("paymentMethod", "receiverBankCountry");
                    assertThat(detailKeys).doesNotContain("cardPan", "blockchain");
                }
                case CRYPTO -> {
                    assertThat(detailKeys).contains("blockchain", "walletAddressFrom", "walletAddressTo", "txHash");
                    assertThat(detailKeys).doesNotContain("cardPan", "paymentMethod");
                }
            }
        }
    }

    @Test
    void everySourceRiskSignalReferencesAnActivityInTheSelectedCustomerSnapshot() {
        CustomerSnapshot snapshot = new SyntheticActivityAdapter()
                .loadSnapshot(SyntheticActivityAdapter.SEEDED_CUSTOMER_ID)
                .orElseThrow();
        Set<UUID> transactionIds = snapshot.activities().stream()
                .map(Activity::transactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(snapshot.riskEvidence()).isNotEmpty();
        assertThat(snapshot.riskEvidence())
                .allSatisfy(evidence -> assertThat(transactionIds).contains(evidence.transactionId()));
    }
}
