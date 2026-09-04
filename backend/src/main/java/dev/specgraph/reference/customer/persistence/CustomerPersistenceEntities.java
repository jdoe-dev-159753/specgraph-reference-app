package dev.specgraph.reference.customer.persistence;

import dev.specgraph.reference.customer.Activity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "PersistenceCustomer")
@Table(name = "customers")
class PersistenceCustomerEntity {
    @Id
    @Column(name = "customer_id", nullable = false)
    private UUID id;

    protected PersistenceCustomerEntity() {}
}

@Entity(name = "SourceTransaction")
@Table(name = "transactions")
class SourceTransactionEntity {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "activity_type", nullable = false, columnDefinition = "activity_type_enum")
    private Activity.ActivityType activityType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id", insertable = false, updatable = false)
    private CardActivityEntity card;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id", insertable = false, updatable = false)
    private PaymentActivityEntity payment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id", insertable = false, updatable = false)
    private CryptoActivityEntity crypto;

    protected SourceTransactionEntity() {}

    UUID id() { return id; }
    UUID customerId() { return customerId; }
    Activity.ActivityType activityType() { return activityType; }
    BigDecimal amount() { return amount; }
    String currency() { return currency; }
    String status() { return status; }
    LocalDateTime createdAt() { return createdAt; }
    CardActivityEntity card() { return card; }
    PaymentActivityEntity payment() { return payment; }
    CryptoActivityEntity crypto() { return crypto; }
}

@Entity(name = "CardActivityRow")
@Table(name = "card_activity")
class CardActivityEntity {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "card_pan", nullable = false)
    private String cardPan;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "mcc_code", nullable = false, length = 4)
    private String mccCode;

    @Column(name = "card_present", nullable = false)
    private boolean cardPresent;

    @Column(name = "authorization_code", nullable = false)
    private String authorizationCode;

    @Column(name = "decline_reason")
    private String declineReason;

    protected CardActivityEntity() {}

    String cardPan() { return cardPan; }
    String cardType() { return cardType; }
    String merchantName() { return merchantName; }
    String mccCode() { return mccCode; }
    boolean cardPresent() { return cardPresent; }
    String authorizationCode() { return authorizationCode; }
    String declineReason() { return declineReason; }
}

@Entity(name = "PaymentActivityRow")
@Table(name = "payment_activity")
class PaymentActivityEntity {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "sender_account", nullable = false)
    private String senderAccount;

    @Column(name = "receiver_account", nullable = false)
    private String receiverAccount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "receiver_bank_country", nullable = false, length = 2, columnDefinition = "char(2)")
    private String receiverBankCountry;

    protected PaymentActivityEntity() {}

    String paymentMethod() { return paymentMethod; }
    String senderAccount() { return senderAccount; }
    String receiverAccount() { return receiverAccount; }
    String receiverBankCountry() { return receiverBankCountry; }
}

@Entity(name = "CryptoActivityRow")
@Table(name = "crypto_activity")
class CryptoActivityEntity {
    @Id
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "blockchain", nullable = false)
    private String blockchain;

    @Column(name = "wallet_address_from", nullable = false)
    private String walletAddressFrom;

    @Column(name = "wallet_address_to", nullable = false)
    private String walletAddressTo;

    @Column(name = "tx_hash", nullable = false)
    private String txHash;

    @Column(name = "exchange_name")
    private String exchangeName;

    protected CryptoActivityEntity() {}

    String blockchain() { return blockchain; }
    String walletAddressFrom() { return walletAddressFrom; }
    String walletAddressTo() { return walletAddressTo; }
    String txHash() { return txHash; }
    String exchangeName() { return exchangeName; }
}

@Entity(name = "RiskRuleRow")
@Table(name = "risk_rules")
class RiskRuleEntity {
    @Id
    @Column(name = "rule_id", nullable = false)
    private UUID id;

    @Column(name = "rule_name", nullable = false)
    private String name;

    protected RiskRuleEntity() {}

    UUID id() { return id; }
    String name() { return name; }
}

@Entity(name = "RiskAssessmentRow")
@Table(name = "risk_assessments")
class RiskAssessmentEntity {
    @Id
    @Column(name = "assessment_id", nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, insertable = false, updatable = false)
    private SourceTransactionEntity transaction;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, insertable = false, updatable = false)
    private RiskRuleEntity rule;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "score_contribution", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreContribution;

    protected RiskAssessmentEntity() {}

    UUID id() { return id; }
    UUID transactionId() { return transactionId; }
    UUID ruleId() { return ruleId; }
    SourceTransactionEntity transaction() { return transaction; }
    RiskRuleEntity rule() { return rule; }
    LocalDateTime triggeredAt() { return triggeredAt; }
    BigDecimal scoreContribution() { return scoreContribution; }
}
