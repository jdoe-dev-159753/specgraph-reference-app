package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;

public interface AnalysisModelPort {
    AnalysisResult analyze(CustomerSnapshot snapshot, List<PolicyEvidence> evidence);
}
