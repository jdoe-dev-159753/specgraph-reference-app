package dev.specgraph.reference.analysis;

import dev.specgraph.reference.identity.OperatorId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound application contract for generating and reviewing customer analyses.
 *
 * <p>Generation requires an already authenticated {@link OperatorId}, derives and retrieves
 * evidence, validates model grounding, and persists only the completed result. Failures are
 * exposed as {@link AnalysisFailureException} with a stable application reason.
 */
public interface AnalysisUseCase {
    /** Generates and retains a new analysis for the customer and operator. */
    AnalysisHistoryEntry analyze(UUID customerId, OperatorId operatorId);

    /** Returns complete retained history for compatibility consumers. */
    List<AnalysisHistoryEntry> listHistory(UUID customerId);

    /** Returns one bounded, zero-based page of retained history. */
    AnalysisHistoryPage listHistory(UUID customerId, AnalysisHistoryQuery query);

    /** Finds one retained analysis scoped to its owning customer. */
    Optional<AnalysisHistoryEntry> findHistory(UUID customerId, UUID analysisId);
}
