package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;

/**
 * Outbound analysis boundary for deriving advisory risk signals from source customer evidence.
 *
 * <p>Detector scores and explanations are derived evidence: they may inform a model and operator,
 * but they are neither source risk truth nor calibrated probabilities. Implementations expose
 * execution failures as runtime failures for application-level translation.
 */
public interface RiskSignalDetectorPort {
    /** Derives zero or more provenance-bearing signals from the complete snapshot. */
    List<RiskSignalEvidence> detect(CustomerSnapshot snapshot);
}
