package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;

public interface PolicyKnowledgePort {
    List<PolicyEvidence> retrieveRelevant(CustomerSnapshot snapshot);
}
