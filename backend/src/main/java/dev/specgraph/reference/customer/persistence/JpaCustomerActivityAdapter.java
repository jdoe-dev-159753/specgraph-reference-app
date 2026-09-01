package dev.specgraph.reference.customer.persistence;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
final class JpaCustomerActivityAdapter implements CustomerActivityPort {
    private final EntityManager entityManager;

    JpaCustomerActivityAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        if (entityManager.find(CustomerEntity.class, customerId) == null) {
            return Optional.empty();
        }

        List<TransactionEntity> transactions = entityManager.createQuery(
                        "select t from TransactionEntity t where t.customerId = :customerId "
                                + "order by t.createdAt, t.transactionId",
                        TransactionEntity.class)
                .setParameter("customerId", customerId)
                .getResultList();
        if (transactions.isEmpty()) {
            return Optional.of(new CustomerSnapshot(customerId, List.of(), List.of()));
        }

        List<UUID> transactionIds = transactions.stream().map(t -> t.transactionId).toList();
        Map<UUID, CardActivityEntity> cards = byTransaction(loadByIds(CardActivityEntity.class, transactionIds));
        Map<UUID, PaymentActivityEntity> payments = byTransaction(loadByIds(PaymentActivityEntity.class, transactionIds));
        Map<UUID, CryptoActivityEntity> crypto = byTransaction(loadByIds(CryptoActivityEntity.class, transactionIds));

        List<Activity> activities = transactions.stream()
                .map(transaction -> mapActivity(transaction, cards, payments, crypto))
                .toList();

        List<RiskAssessmentEntity> assessments = entityManager.createQuery(
                        "select r from RiskAssessmentEntity r where r.transactionId in :transactionIds "
                                + "order by r.triggeredAt, r.assessmentId",
                        RiskAssessmentEntity.class)
                .setParameter("transactionIds", transactionIds)
                .getResultList();
        Map<UUID, RiskRuleEntity> rules = assessments.isEmpty()
                ? Map.of()
                : entityManager.createQuery(
                                "select r from RiskRuleEntity r where r.ruleId in :ruleIds",
                                RiskRuleEntity.class)
                        .setParameter("ruleIds", assessments.stream().map(a -> a.ruleId).distinct().toList())
                        .getResultList().stream()
                        .collect(Collectors.toMap(rule -> rule.ruleId, Function.identity()));

        List<RiskEvidence> riskEvidence = assessments.stream().map(assessment -> {
            RiskRuleEntity rule = rules.get(assessment.ruleId);
            if (rule == null) {
                throw new IllegalStateException("Missing risk rule " + assessment.ruleId);
            }
            return new RiskEvidence(
                    assessment.transactionId,
                    rule.ruleId.toString(),
                    rule.ruleName,
                    assessment.triggeredAt.toInstant(ZoneOffset.UTC),
                    assessment.scoreContribution);
        }).toList();

        return Optional.of(new CustomerSnapshot(customerId, activities, riskEvidence));
    }

    private <T extends TransactionSpecialization> List<T> loadByIds(Class<T> entityType, Collection<UUID> transactionIds) {
        return entityManager.createQuery(
                        "select e from " + entityType.getSimpleName() + " e where e.transactionId in :transactionIds",
                        entityType)
                .setParameter("transactionIds", transactionIds)
                .getResultList();
    }

    private static <T extends TransactionSpecialization> Map<UUID, T> byTransaction(List<T> rows) {
        return rows.stream().collect(Collectors.toMap(TransactionSpecialization::transactionId, Function.identity()));
    }

    private static Activity mapActivity(
            TransactionEntity transaction,
            Map<UUID, CardActivityEntity> cards,
            Map<UUID, PaymentActivityEntity> payments,
            Map<UUID, CryptoActivityEntity> crypto) {
        UUID id = transaction.transactionId;
        int specializationCount = (cards.containsKey(id) ? 1 : 0)
                + (payments.containsKey(id) ? 1 : 0)
                + (crypto.containsKey(id) ? 1 : 0);
        if (specializationCount != 1) {
            throw new IllegalStateException("Transaction " + id + " has " + specializationCount + " specialization rows");
        }

        Activity.ActivityType type;
        try {
            type = Activity.ActivityType.valueOf(transaction.activityType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported activity type " + transaction.activityType, exception);
        }

        Activity.ActivityDetails details = switch (type) {
            case CARD -> {
                CardActivityEntity card = cards.get(id);
                requireMatchingSpecialization(id, card, "CARD");
                yield new Activity.CardDetails(
                        card.cardPan,
                        card.cardType,
                        card.merchantName,
                        card.mccCode,
                        card.cardPresent,
                        card.authorizationCode,
                        card.declineReason);
            }
            case PAYMENT -> {
                PaymentActivityEntity payment = payments.get(id);
                requireMatchingSpecialization(id, payment, "PAYMENT");
                yield new Activity.PaymentDetails(
                        payment.paymentMethod,
                        payment.senderAccount,
                        payment.receiverAccount,
                        payment.receiverBankCountry);
            }
            case CRYPTO -> {
                CryptoActivityEntity cryptoActivity = crypto.get(id);
                requireMatchingSpecialization(id, cryptoActivity, "CRYPTO");
                yield new Activity.CryptoDetails(
                        cryptoActivity.blockchain,
                        cryptoActivity.walletAddressFrom,
                        cryptoActivity.walletAddressTo,
                        cryptoActivity.txHash,
                        cryptoActivity.exchangeName);
            }
        };

        return new Activity(
                id,
                type,
                transaction.amount,
                transaction.currency,
                transaction.status,
                transaction.createdAt.toInstant(ZoneOffset.UTC),
                details);
    }

    private static void requireMatchingSpecialization(UUID transactionId, Object row, String type) {
        if (row == null) {
            throw new IllegalStateException("Transaction " + transactionId + " declares " + type + " without matching row");
        }
    }
}

interface TransactionSpecialization {
    UUID transactionId();
}

@Entity(name = "CustomerEntity")
@Table(name = "customers")
class CustomerEntity {
    @Id
    @Column(name = "customer_id")
    UUID customerId;
}

@Entity(name = "TransactionEntity")
@Table(name = "transactions")
class TransactionEntity {
    @Id
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "customer_id", nullable = false)
    UUID customerId;
    @Column(name = "activity_type", nullable = false)
    String activityType;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    BigDecimal amount;
    @Column(name = "currency", length = 10, nullable = false)
    String currency;
    @Column(name = "status", nullable = false)
    String status;
    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;
}

@Entity(name = "CardActivityEntity")
@Table(name = "card_activity")
class CardActivityEntity implements TransactionSpecialization {
    @Id
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "card_pan", nullable = false)
    String cardPan;
    @Column(name = "card_type", nullable = false)
    String cardType;
    @Column(name = "merchant_name", nullable = false)
    String merchantName;
    @Column(name = "mcc_code", length = 4, nullable = false)
    String mccCode;
    @Column(name = "card_present", nullable = false)
    boolean cardPresent;
    @Column(name = "authorization_code", nullable = false)
    String authorizationCode;
    @Column(name = "decline_reason")
    String declineReason;

    @Override
    public UUID transactionId() { return transactionId; }
}

@Entity(name = "PaymentActivityEntity")
@Table(name = "payment_activity")
class PaymentActivityEntity implements TransactionSpecialization {
    @Id
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "payment_method", nullable = false)
    String paymentMethod;
    @Column(name = "sender_account", nullable = false)
    String senderAccount;
    @Column(name = "receiver_account", nullable = false)
    String receiverAccount;
    @Column(name = "receiver_bank_country", length = 2, nullable = false)
    String receiverBankCountry;

    @Override
    public UUID transactionId() { return transactionId; }
}

@Entity(name = "CryptoActivityEntity")
@Table(name = "crypto_activity")
class CryptoActivityEntity implements TransactionSpecialization {
    @Id
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "blockchain", nullable = false)
    String blockchain;
    @Column(name = "wallet_address_from", nullable = false)
    String walletAddressFrom;
    @Column(name = "wallet_address_to", nullable = false)
    String walletAddressTo;
    @Column(name = "tx_hash", nullable = false)
    String txHash;
    @Column(name = "exchange_name")
    String exchangeName;

    @Override
    public UUID transactionId() { return transactionId; }
}

@Entity(name = "RiskRuleEntity")
@Table(name = "risk_rules")
class RiskRuleEntity {
    @Id
    @Column(name = "rule_id")
    UUID ruleId;
    @Column(name = "rule_name", nullable = false)
    String ruleName;
    @Column(name = "applies_to", nullable = false)
    String appliesTo;
    @Column(name = "threshold_logic", nullable = false)
    String thresholdLogic;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "weight", precision = 5, scale = 2, nullable = false)
    BigDecimal weight;
}

@Entity(name = "RiskAssessmentEntity")
@Table(name = "risk_assessments")
class RiskAssessmentEntity {
    @Id
    @Column(name = "assessment_id")
    UUID assessmentId;
    @Column(name = "transaction_id", nullable = false)
    UUID transactionId;
    @Column(name = "rule_id", nullable = false)
    UUID ruleId;
    @Column(name = "triggered_at", nullable = false)
    LocalDateTime triggeredAt;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "score_contribution", precision = 5, scale = 2, nullable = false)
    BigDecimal scoreContribution;
}
