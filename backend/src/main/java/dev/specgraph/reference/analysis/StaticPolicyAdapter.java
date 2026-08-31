package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class StaticPolicyAdapter implements PolicyKnowledgePort {
    @Override
    public List<PolicyEvidence> retrieveRelevant(CustomerSnapshot snapshot) {
        return List.of(new PolicyEvidence("synthetic-policy:r0", "Synthetic policy evidence for the shell.",
                Map.of("adapter", "static")));
    }
}
