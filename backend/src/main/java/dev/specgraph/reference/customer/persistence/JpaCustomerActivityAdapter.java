package dev.specgraph.reference.customer.persistence;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerReviewPage;
import dev.specgraph.reference.customer.CustomerReviewQuery;
import dev.specgraph.reference.customer.CustomerReviewQueryPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for complete analysis snapshots and independently bounded operator review pages.
 * Database-side filters, counts and pagination avoid loading an entire history for the UI; a
 * repeatable-read transaction keeps each multi-query projection internally consistent.
 */
@Component
@Primary
class JpaCustomerActivityAdapter implements CustomerActivityPort, CustomerReviewQueryPort {
    private final EntityManager entityManager;
    private final ZoneId sourceTimeZone;

    JpaCustomerActivityAdapter(
            EntityManager entityManager,
            @Value("${specgraph.source-time-zone:UTC}") String sourceTimeZone) {
        this.entityManager = entityManager;
        this.sourceTimeZone = ZoneId.of(sourceTimeZone);
    }

    /** Loads source facts and risk evidence inside one database snapshot for analysis. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        if (!customerExists(customerId)) {
            return Optional.empty();
        }
        List<SourceTransactionEntity> transactions = selectTransactions(customerId, null, false);
        return Optional.of(new CustomerSnapshot(
                customerId,
                transactions.stream().map(this::mapActivity).toList(),
                selectRiskEvidence(customerId, null, null)));
    }

    /** Returns one filtered page while keeping totals and evidence consistent with its snapshot. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<CustomerReviewPage> loadReviewPage(UUID customerId, CustomerReviewQuery query) {
        if (!customerExists(customerId)) {
            return Optional.empty();
        }

        long totalActivities = countTransactions(customerId, query);
        List<SourceTransactionEntity> transactions = selectTransactions(customerId, query, true);
        List<UUID> pageTransactionIds = transactions.stream().map(SourceTransactionEntity::id).toList();
        long totalRiskEvidence = countRiskEvidence(customerId, query);

        return Optional.of(new CustomerReviewPage(
                customerId,
                transactions.stream().map(this::mapActivity).toList(),
                selectRiskEvidence(customerId, null, pageTransactionIds),
                query.page(),
                query.pageSize(),
                totalActivities,
                totalRiskEvidence));
    }

    private boolean customerExists(UUID customerId) {
        return entityManager.find(PersistenceCustomerEntity.class, customerId) != null;
    }

    /** Applies stable source ordering and optional database pagination after all review filters. */
    private List<SourceTransactionEntity> selectTransactions(
            UUID customerId, CustomerReviewQuery review, boolean paged) {
        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<SourceTransactionEntity> query = criteria.createQuery(SourceTransactionEntity.class);
        Root<SourceTransactionEntity> transaction = query.from(SourceTransactionEntity.class);
        transaction.fetch("card", JoinType.LEFT);
        transaction.fetch("payment", JoinType.LEFT);
        transaction.fetch("crypto", JoinType.LEFT);
        query.select(transaction)
                .where(reviewPredicates(criteria, transaction, customerId, review).toArray(Predicate[]::new))
                .orderBy(criteria.asc(transaction.get("createdAt")), criteria.asc(transaction.get("id")));
        var typedQuery = entityManager.createQuery(query);
        if (paged) {
            typedQuery.setFirstResult(Math.toIntExact(review.offset()));
            typedQuery.setMaxResults(review.pageSize());
        }
        return typedQuery.getResultList();
    }

    private long countTransactions(UUID customerId, CustomerReviewQuery review) {
        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = criteria.createQuery(Long.class);
        Root<SourceTransactionEntity> transaction = query.from(SourceTransactionEntity.class);
        query.select(criteria.count(transaction))
                .where(reviewPredicates(criteria, transaction, customerId, review).toArray(Predicate[]::new));
        return entityManager.createQuery(query).getSingleResult();
    }

    private long countRiskEvidence(UUID customerId, CustomerReviewQuery review) {
        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = criteria.createQuery(Long.class);
        Root<RiskAssessmentEntity> risk = query.from(RiskAssessmentEntity.class);
        Join<RiskAssessmentEntity, SourceTransactionEntity> transaction = risk.join("transaction");
        query.select(criteria.count(risk))
                .where(reviewPredicates(criteria, transaction, customerId, review).toArray(Predicate[]::new));
        return entityManager.createQuery(query).getSingleResult();
    }

    /** Selects evidence either for an entire filtered review or strictly for the current page. */
    private List<RiskEvidence> selectRiskEvidence(
            UUID customerId, CustomerReviewQuery review, List<UUID> transactionIds) {
        if (transactionIds != null && transactionIds.isEmpty()) {
            return List.of();
        }
        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<RiskAssessmentEntity> query = criteria.createQuery(RiskAssessmentEntity.class);
        Root<RiskAssessmentEntity> risk = query.from(RiskAssessmentEntity.class);
        risk.fetch("rule", JoinType.INNER);
        List<Predicate> predicates = new ArrayList<>();
        if (transactionIds != null) {
            predicates.add(risk.get("transactionId").in(transactionIds));
        } else {
            Join<RiskAssessmentEntity, SourceTransactionEntity> transaction = risk.join("transaction");
            predicates.addAll(reviewPredicates(criteria, transaction, customerId, review));
        }
        query.select(risk)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(criteria.asc(risk.get("triggeredAt")), criteria.asc(risk.get("id")));
        return entityManager.createQuery(query).getResultList().stream().map(this::mapRiskEvidence).toList();
    }

    /** Centralizes identical customer, type, status, and half-open time filters for rows and counts. */
    private List<Predicate> reviewPredicates(
            CriteriaBuilder criteria,
            From<?, SourceTransactionEntity> transaction,
            UUID customerId,
            CustomerReviewQuery review) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteria.equal(transaction.get("customerId"), customerId));
        if (review == null) {
            return predicates;
        }
        if (review.activityType() != null) {
            predicates.add(criteria.equal(transaction.get("activityType"), review.activityType()));
        }
        if (review.status() != null) {
            predicates.add(criteria.equal(
                    criteria.lower(transaction.get("status")), review.status().toLowerCase(Locale.ROOT)));
        }
        if (review.createdFrom() != null) {
            predicates.add(criteria.greaterThanOrEqualTo(
                    transaction.get("createdAt"), sourceLocalDateTime(review.createdFrom())));
        }
        if (review.createdTo() != null) {
            predicates.add(criteria.lessThan(transaction.get("createdAt"), sourceLocalDateTime(review.createdTo())));
        }
        return predicates;
    }

    /** Fails closed unless persistence contains exactly one specialization matching the closed type. */
    private Activity mapActivity(SourceTransactionEntity transaction) {
        int specializationCount = (transaction.card() != null ? 1 : 0)
                + (transaction.payment() != null ? 1 : 0)
                + (transaction.crypto() != null ? 1 : 0);
        if (specializationCount != 1) {
            throw new IllegalStateException(
                    "Transaction " + transaction.id() + " has " + specializationCount + " specialization rows");
        }

        Activity.ActivityDetails details = switch (transaction.activityType()) {
            case CARD -> mapCard(transaction);
            case PAYMENT -> mapPayment(transaction);
            case CRYPTO -> mapCrypto(transaction);
        };
        return new Activity(
                transaction.id(),
                transaction.activityType(),
                transaction.amount(),
                transaction.currency(),
                transaction.status(),
                sourceInstant(transaction.createdAt()),
                details);
    }

    private Activity.CardDetails mapCard(SourceTransactionEntity transaction) {
        CardActivityEntity card = requireSpecialization(transaction.id(), transaction.card(), "CARD");
        return new Activity.CardDetails(
                card.cardPan(), card.cardType(), card.merchantName(), card.mccCode(), card.cardPresent(),
                card.authorizationCode(), card.declineReason());
    }

    private Activity.PaymentDetails mapPayment(SourceTransactionEntity transaction) {
        PaymentActivityEntity payment = requireSpecialization(transaction.id(), transaction.payment(), "PAYMENT");
        return new Activity.PaymentDetails(
                payment.paymentMethod(), payment.senderAccount(), payment.receiverAccount(), payment.receiverBankCountry());
    }

    private Activity.CryptoDetails mapCrypto(SourceTransactionEntity transaction) {
        CryptoActivityEntity crypto = requireSpecialization(transaction.id(), transaction.crypto(), "CRYPTO");
        return new Activity.CryptoDetails(
                crypto.blockchain(), crypto.walletAddressFrom(), crypto.walletAddressTo(), crypto.txHash(),
                crypto.exchangeName());
    }

    private RiskEvidence mapRiskEvidence(RiskAssessmentEntity risk) {
        return new RiskEvidence(
                risk.id(),
                risk.transactionId(),
                risk.ruleId().toString(),
                risk.rule().name(),
                sourceInstant(risk.triggeredAt()),
                risk.scoreContribution());
    }

    private Instant sourceInstant(LocalDateTime value) {
        return value.atZone(sourceTimeZone).toInstant();
    }

    private LocalDateTime sourceLocalDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, sourceTimeZone);
    }

    private static <T> T requireSpecialization(UUID transactionId, T value, String type) {
        if (value == null) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " declares " + type + " without matching row");
        }
        return value;
    }
}
