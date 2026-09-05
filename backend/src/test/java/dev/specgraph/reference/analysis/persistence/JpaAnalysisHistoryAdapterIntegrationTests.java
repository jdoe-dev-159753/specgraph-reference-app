package dev.specgraph.reference.analysis.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.PostgresIntegrationTestSupport;
import dev.specgraph.reference.ReferenceApplication;
import dev.specgraph.reference.analysis.AnalysisHistoryCreateCommand;
import dev.specgraph.reference.analysis.AnalysisHistoryEntry;
import dev.specgraph.reference.analysis.AnalysisHistoryPort;
import dev.specgraph.reference.analysis.AnalysisHistoryQuery;
import dev.specgraph.reference.analysis.AnalysisModelProvenance;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.analysis.PolicyEvidence;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.identity.OperatorId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("VFY-ANALYSIS-HISTORY-001")
@SpringBootTest(classes = ReferenceApplication.class)
/**
 * Proves JSONB provenance round trips and deterministic newest-first paging against migrated PostgreSQL.
 * It validates the JPA adapter boundary rather than general database durability operations.
 */
final class JpaAnalysisHistoryAdapterIntegrationTests extends PostgresIntegrationTestSupport {
    private static final UUID FIRST_CUSTOMER =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_CUSTOMER =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant SHARED_TIME = Instant.parse("2026-09-04T12:00:00Z");

    @Autowired AnalysisHistoryPort history;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearHistory() {
        jdbc.update("DELETE FROM analysis_history");
    }

    @Test
    void roundTripsAllJsonbProvenanceThroughTheJpaAdapter() {
        AnalysisHistoryEntry persisted = history.persist(command(FIRST_CUSTOMER, "operator-alpha"));

        assertThat(history.findByCustomerAndId(FIRST_CUSTOMER, persisted.analysisId()))
                .contains(persisted);
        assertThat(history.listByCustomer(FIRST_CUSTOMER)).containsExactly(persisted);
        assertThat(history.findByCustomerAndId(SECOND_CUSTOMER, persisted.analysisId())).isEmpty();
    }

    @Test
    void pagesStableTimestampTiesByDescendingAnalysisIdentity() {
        AnalysisHistoryEntry first = history.persist(command(FIRST_CUSTOMER, "operator-alpha"));
        AnalysisHistoryEntry second = history.persist(command(FIRST_CUSTOMER, "operator-beta"));
        List<UUID> expectedIds = jdbc.queryForList(
                """
                SELECT analysis_id
                FROM analysis_history
                WHERE customer_id = ?
                ORDER BY generated_at DESC, analysis_id DESC
                """,
                UUID.class,
                FIRST_CUSTOMER);

        var firstPage = history.pageByCustomer(FIRST_CUSTOMER, new AnalysisHistoryQuery(0, 1));
        var secondPage = history.pageByCustomer(FIRST_CUSTOMER, new AnalysisHistoryQuery(1, 1));

        assertThat(firstPage.totalEntries()).isEqualTo(2);
        assertThat(firstPage.entries()).extracting(AnalysisHistoryEntry::analysisId)
                .containsExactly(expectedIds.get(0));
        assertThat(secondPage.entries()).extracting(AnalysisHistoryEntry::analysisId)
                .containsExactly(expectedIds.get(1));
        assertThat(expectedIds).containsExactlyInAnyOrder(first.analysisId(), second.analysisId());
    }

    /** Keeps generated timestamps tied so the scenario specifically proves identity tie-breaking. */
    private AnalysisHistoryCreateCommand command(UUID customerId, String operatorId) {
        return new AnalysisHistoryCreateCommand(
                customerId,
                new OperatorId(operatorId),
                SHARED_TIME,
                new AnalysisResult(AnalysisResult.RiskLevel.MEDIUM, "Synthetic review", List.of("Review evidence")),
                List.of(new PolicyEvidence(
                        "synthetic-policy:test", "Synthetic policy evidence", Map.of("rank", "1"))),
                List.of(new RiskSignalEvidence(
                        "synthetic-detector-v1", "REVIEW_ELEVATED", 0.75, Map.of("scope", "test"))),
                new AnalysisModelProvenance(
                        "deterministic", "test-model-v1", "test-prompt-v1", List.of(), Map.of("scope", "test")));
    }
}
