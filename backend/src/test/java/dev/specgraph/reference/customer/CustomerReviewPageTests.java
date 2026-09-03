package dev.specgraph.reference.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
final class CustomerReviewPageTests {

    @Test
    void maximumPageIndexCannotOverflowNextPageMetadata() {
        var page = new CustomerReviewPage(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                List.of(),
                List.of(),
                Integer.MAX_VALUE,
                1,
                Integer.MAX_VALUE,
                0);

        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }
}
