package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class StaticPolicyAdapter implements PolicyKnowledgePort {
    @Override
    public List<PolicyEvidence> retrieveRelevant(CustomerSnapshot snapshot) {
        return List.of(new PolicyEvidence(
                "synthetic-policy:r3-review-baseline",
                "Review persisted customer activity together with source-derived risk signals. Escalation remains a human decision. This is synthetic demonstration policy, not institutional policy.",
                Map.of(
                        "adapter", "static",
                        "corpus", "synthetic",
                        "revision", "r3")));
    }
}
