package dev.specgraph.reference.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerReviewUseCase;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("architecture")
class CustomerHttpBoundaryArchitectureTests {
    @Test
    void httpAdapterDependsOnInboundUseCaseAndNeverOnOutboundActivityPort() {
        var constructors = CustomerAnalysisHttpAdapter.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes()).containsExactly(CustomerReviewUseCase.class);

        var fieldTypes = Arrays.stream(CustomerAnalysisHttpAdapter.class.getDeclaredFields())
                .map(Field::getType)
                .toList();
        assertThat(fieldTypes).doesNotContain(CustomerActivityPort.class);
    }
}
