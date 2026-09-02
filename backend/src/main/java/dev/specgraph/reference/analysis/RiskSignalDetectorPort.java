package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;

public interface RiskSignalDetectorPort {
    List<RiskSignalEvidence> detect(CustomerSnapshot snapshot);
}
