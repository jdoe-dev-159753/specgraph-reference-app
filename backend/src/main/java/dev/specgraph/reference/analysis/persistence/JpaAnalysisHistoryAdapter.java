package dev.specgraph.reference.analysis.persistence;

import dev.specgraph.reference.analysis.AnalysisHistoryCreateCommand;
import dev.specgraph.reference.analysis.AnalysisHistoryEntry;
import dev.specgraph.reference.analysis.AnalysisHistoryPage;
import dev.specgraph.reference.analysis.AnalysisHistoryPort;
import dev.specgraph.reference.analysis.AnalysisHistoryQuery;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.identity.OperatorId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
class JpaAnalysisHistoryAdapter implements AnalysisHistoryPort {
    private static final String BY_CUSTOMER = """
            select history
            from PersistedAnalysisHistory history
            where history.customerId = :customerId
            order by history.generatedAt desc, history.id desc
            """;

    private final EntityManager entityManager;

    JpaAnalysisHistoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
        UUID analysisId = UUID.randomUUID();
        AnalysisHistoryEntity entity = new AnalysisHistoryEntity(
                analysisId,
                command.customerId(),
                command.operatorId().value(),
                command.generatedAt(),
                command.result().riskLevel(),
                command.result().findingsSummary(),
                command.result().recommendations(),
                command.evidenceProvenance(),
                command.detectorProvenance(),
                command.modelProvenance());
        entityManager.persist(entity);
        return map(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
        return entityManager.createQuery(BY_CUSTOMER, AnalysisHistoryEntity.class)
                .setParameter("customerId", customerId)
                .getResultList()
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalysisHistoryPage pageByCustomer(UUID customerId, AnalysisHistoryQuery query) {
        long totalEntries = entityManager.createQuery(
                        "select count(history) from PersistedAnalysisHistory history "
                                + "where history.customerId = :customerId",
                        Long.class)
                .setParameter("customerId", customerId)
                .getSingleResult();
        List<AnalysisHistoryEntry> entries = entityManager.createQuery(BY_CUSTOMER, AnalysisHistoryEntity.class)
                .setParameter("customerId", customerId)
                .setFirstResult(Math.toIntExact(query.offset()))
                .setMaxResults(query.pageSize())
                .getResultList()
                .stream()
                .map(this::map)
                .toList();
        return new AnalysisHistoryPage(entries, query.page(), query.pageSize(), totalEntries);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId) {
        return entityManager.createQuery(
                        """
                        select history
                        from PersistedAnalysisHistory history
                        where history.customerId = :customerId and history.id = :analysisId
                        """,
                        AnalysisHistoryEntity.class)
                .setParameter("customerId", customerId)
                .setParameter("analysisId", analysisId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(this::map);
    }

    private AnalysisHistoryEntry map(AnalysisHistoryEntity entity) {
        AnalysisResult result = new AnalysisResult(
                entity.riskLevel(), entity.findingsSummary(), entity.recommendations());
        return new AnalysisHistoryEntry(
                entity.id(),
                entity.customerId(),
                new OperatorId(entity.operatorId()),
                entity.generatedAt(),
                result,
                entity.evidenceProvenance(),
                entity.detectorProvenance(),
                entity.modelProvenance());
    }
}
