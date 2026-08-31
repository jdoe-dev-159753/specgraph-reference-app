package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Stable executable contract for every CustomerActivityPort adapter.
 *
 * <p>The canonical seeded customer deliberately exercises all three source activity families so the
 * synthetic R1 adapter and the relational R2 adapter can be checked against the same application-owned
 * semantics.
 */
@Tag("VFY-CUSTOMER-READ-001")
@Tag("port_contract")
public abstract class CustomerActivityPortContract {
    protected static final UUID SEEDED_CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID UNKNOWN_CUSTOMER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    protected abstract CustomerActivityPort activityPort();

    @Test
    void seededCustomerPreservesTheApplicationOwnedSnapshotContract() {
        CustomerSnapshot snapshot = activityPort().loadSnapshot(SEEDED_CUSTOMER_ID).orElseThrow();

        assertThat(snapshot.customerId()).isEqualTo(SEEDED_CUSTOMER_ID);
        assertThat(snapshot.activities())
                .extracting(Activity::type)
                .containsExactlyInAnyOrder(
                        Activity.ActivityType.CARD,
                        Activity.ActivityType.PAYMENT,
                        Activity.ActivityType.CRYPTO);

        Set<UUID> transactionIds = new HashSet<>();
        for (Activity activity : snapshot.activities()) {
            assertThat(transactionIds.add(activity.transactionId()))
                    .as("transaction identity must be unique inside one customer snapshot")
                    .isTrue();
            assertThat(activity.amount()).isNotNull().isGreaterThan(BigDecimal.ZERO);
            assertThat(activity.currency()).isNotBlank();
            assertThat(activity.status()).isNotBlank();
            assertThat(activity.createdAt()).isNotNull();

            switch (activity.type()) {
                case CARD -> assertThat(activity.details()).containsKeys(
                        "cardPan", "cardType", "merchantName", "mccCode", "cardPresent", "authorizationCode");
                case PAYMENT -> assertThat(activity.details()).containsKeys(
                        "paymentMethod", "senderAccount", "receiverAccount", "receiverBankCountry");
                case CRYPTO -> assertThat(activity.details()).containsKeys(
                        "blockchain", "walletAddressFrom", "walletAddressTo", "txHash");
            }
        }

        assertThat(snapshot.riskEvidence()).isNotEmpty();
        for (RiskEvidence evidence : snapshot.riskEvidence()) {
            assertThat(transactionIds)
                    .as("persisted/source risk evidence must remain attached to a displayed transaction")
                    .contains(evidence.transactionId());
            assertThat(evidence.ruleId()).isNotBlank();
            assertThat(evidence.ruleName()).isNotBlank();
            assertThat(evidence.triggeredAt()).isNotNull();
            assertThat(evidence.scoreContribution()).isNotNull();
        }
    }

    @Test
    void unknownCustomerIsNotFabricatedByAnAdapter() {
        assertThat(activityPort().loadSnapshot(UNKNOWN_CUSTOMER_ID)).isEmpty();
    }
}
