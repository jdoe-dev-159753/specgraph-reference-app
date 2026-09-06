package dev.specgraph.reference.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.specgraph.reference.customer.CustomerReviewQuery;
import dev.specgraph.reference.customer.CustomerReviewUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MVC evidence distinguishing malformed client input from bounded data-source failure problems.
 * A throwing use-case double proves disclosure behavior without a database.
 */
class CustomerDataFailureHttpTests {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final CustomerReviewUseCase customerReview = mock(CustomerReviewUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CustomerReviewHttpAdapter(customerReview)).build();

    @Test
    void malformedPathAndFiltersRemainBadRequests() throws Exception {
        for (String request : List.of(
                "/api/customers/not-a-uuid",
                "/api/customers/" + CUSTOMER_ID + "?type=NOT_AN_ACTIVITY",
                "/api/customers/" + CUSTOMER_ID + "?createdFrom=not-an-instant",
                "/api/customers/" + CUSTOMER_ID + "?page=not-an-integer",
                "/api/customers/" + CUSTOMER_ID + "?page=-1",
                "/api/customers/" + CUSTOMER_ID + "?pageSize=201",
                "/api/customers/" + CUSTOMER_ID
                        + "?createdFrom=2026-09-05T12:00:00Z&createdTo=2026-09-05T11:00:00Z")) {
            mvc.perform(get(request)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(customerReview);
    }

    @Test
    void dataReadFailureReturnsBoundedProblemWithoutExceptionDetails() throws Exception {
        when(customerReview.findCustomer(eq(CUSTOMER_ID), any(CustomerReviewQuery.class)))
                .thenThrow(new IllegalStateException("secret-token from JpaCustomerActivityAdapter"));

        String body = mvc.perform(get("/api/customers/{customerId}", CUSTOMER_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Customer data unavailable"))
                .andExpect(jsonPath("$.detail").value("Customer data could not be loaded"))
                .andExpect(jsonPath("$.reason").value("CUSTOMER_DATA_FAILURE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("secret-token")
                .doesNotContain("IllegalStateException")
                .doesNotContain("JpaCustomerActivityAdapter")
                .doesNotContain("stackTrace");
    }
}
