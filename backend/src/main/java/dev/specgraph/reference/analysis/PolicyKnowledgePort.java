package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;

/**
 * Outbound retrieval boundary for policy material relevant to a customer snapshot.
 *
 * <p>Returned excerpts are grounding candidates, not source customer evidence. An empty list means
 * that the application cannot ground an analysis; retrieval failures are exposed as runtime
 * failures for application-level translation.
 */
public interface PolicyKnowledgePort {
    /** Retrieves provider-neutral policy evidence relevant to the supplied snapshot. */
    List<PolicyEvidence> retrieveRelevant(CustomerSnapshot snapshot);
}
