package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
final class CustomerReviewQueryTests {

    @Test
    void defaultPageIsBoundedAndDeterministic() {
        var query = CustomerReviewQuery.firstPage();

        assertThat(query.page()).isZero();
        assertThat(query.pageSize()).isEqualTo(50);
        assertThat(query.offset()).isZero();
        assertThat(query.activityType()).isNull();
        assertThat(query.status()).isNull();
    }

    @Test
    void validatesPageBoundsAndNormalizesStatus() {
        assertThat(new CustomerReviewQuery(2, 50, null, "  Completed  ", null, null).status())
                .isEqualTo("Completed");
        assertThat(new CustomerReviewQuery(0, 50, null, "  ", null, null).status()).isNull();
        assertThat(new CustomerReviewQuery(2, 50, null, null, null, null).offset()).isEqualTo(100);
        assertThat(new CustomerReviewQuery(10_737_418, 200, null, null, null, null).offset())
                .isEqualTo(2_147_483_600L);

        assertThatThrownBy(() -> new CustomerReviewQuery(-1, 50, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerReviewQuery(0, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerReviewQuery(0, 201, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerReviewQuery(10_737_419, 200, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagination offset");
    }

    @Test
    void requiresAProperHalfOpenDateWindow() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");

        assertThat(new CustomerReviewQuery(0, 50, null, null, from, to).createdTo()).isEqualTo(to);
        assertThatThrownBy(() -> new CustomerReviewQuery(0, 50, null, null, from, from))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerReviewQuery(0, 50, null, null, to, from))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
