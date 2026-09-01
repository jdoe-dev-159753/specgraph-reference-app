package dev.specgraph.reference.customer.persistence;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Primary
class JdbcCustomerActivityAdapter implements CustomerActivityPort {
    private static final String CUSTOMER_EXISTS_SQL = """
            SELECT customer_id
            FROM customers
            WHERE customer_id = :customerId
            """;

    private static final String ACTIVITIES_SQL = """
            SELECT
                t.transaction_id,
                t.activity_type::text AS activity_type,
                t.amount,
                t.currency,
                t.status,
                t.created_at,
                c.transaction_id AS card_transaction_id,
                c.card_pan,
                c.card_type,
                c.merchant_name,
                c.mcc_code,
                c.card_present,
                c.authorization_code,
                c.decline_reason,
                p.transaction_id AS payment_transaction_id,
                p.payment_method,
                p.sender_account,
                p.receiver_account,
                p.receiver_bank_country,
                x.transaction_id AS crypto_transaction_id,
                x.blockchain,
                x.wallet_address_from,
                x.wallet_address_to,
                x.tx_hash,
                x.exchange_name
            FROM transactions t
            LEFT JOIN card_activity c ON c.transaction_id = t.transaction_id
            LEFT JOIN payment_activity p ON p.transaction_id = t.transaction_id
            LEFT JOIN crypto_activity x ON x.transaction_id = t.transaction_id
            WHERE t.customer_id = :customerId
            ORDER BY t.created_at, t.transaction_id
            """;

    private static final String RISK_EVIDENCE_SQL = """
            SELECT
                ra.assessment_id,
                ra.transaction_id,
                rr.rule_id,
                rr.rule_name,
                ra.triggered_at,
                ra.score_contribution
            FROM risk_assessments ra
            JOIN risk_rules rr ON rr.rule_id = ra.rule_id
            JOIN transactions t ON t.transaction_id = ra.transaction_id
            WHERE t.customer_id = :customerId
            ORDER BY ra.triggered_at, ra.assessment_id
            """;

    private final JdbcClient jdbc;
    private final ZoneId sourceTimeZone;

    JdbcCustomerActivityAdapter(
            JdbcClient jdbc,
            @Value("${specgraph.source-time-zone:UTC}") String sourceTimeZone) {
        this.jdbc = jdbc;
        this.sourceTimeZone = ZoneId.of(sourceTimeZone);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
        boolean customerExists = !jdbc.sql(CUSTOMER_EXISTS_SQL)
                .param("customerId", customerId)
                .query((rs, rowNum) -> rs.getObject("customer_id", UUID.class))
                .list()
                .isEmpty();
        if (!customerExists) {
            return Optional.empty();
        }

        List<Activity> activities = jdbc.sql(ACTIVITIES_SQL)
                .param("customerId", customerId)
                .query(this::mapActivity)
                .list();
        List<RiskEvidence> riskEvidence = jdbc.sql(RISK_EVIDENCE_SQL)
                .param("customerId", customerId)
                .query(this::mapRiskEvidence)
                .list();

        return Optional.of(new CustomerSnapshot(customerId, activities, riskEvidence));
    }

    private Activity mapActivity(ResultSet rs, int rowNum) throws SQLException {
        UUID transactionId = rs.getObject("transaction_id", UUID.class);
        Activity.ActivityType type;
        try {
            type = Activity.ActivityType.valueOf(rs.getString("activity_type"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported activity type for transaction " + transactionId, exception);
        }

        boolean hasCard = rs.getObject("card_transaction_id") != null;
        boolean hasPayment = rs.getObject("payment_transaction_id") != null;
        boolean hasCrypto = rs.getObject("crypto_transaction_id") != null;
        int specializationCount = (hasCard ? 1 : 0) + (hasPayment ? 1 : 0) + (hasCrypto ? 1 : 0);
        if (specializationCount != 1) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " has " + specializationCount + " specialization rows");
        }

        Activity.ActivityDetails details = switch (type) {
            case CARD -> {
                requireSpecialization(transactionId, hasCard, "CARD");
                yield new Activity.CardDetails(
                        rs.getString("card_pan"),
                        rs.getString("card_type"),
                        rs.getString("merchant_name"),
                        rs.getString("mcc_code"),
                        rs.getBoolean("card_present"),
                        rs.getString("authorization_code"),
                        rs.getString("decline_reason"));
            }
            case PAYMENT -> {
                requireSpecialization(transactionId, hasPayment, "PAYMENT");
                yield new Activity.PaymentDetails(
                        rs.getString("payment_method"),
                        rs.getString("sender_account"),
                        rs.getString("receiver_account"),
                        rs.getString("receiver_bank_country"));
            }
            case CRYPTO -> {
                requireSpecialization(transactionId, hasCrypto, "CRYPTO");
                yield new Activity.CryptoDetails(
                        rs.getString("blockchain"),
                        rs.getString("wallet_address_from"),
                        rs.getString("wallet_address_to"),
                        rs.getString("tx_hash"),
                        rs.getString("exchange_name"));
            }
        };

        BigDecimal amount = rs.getBigDecimal("amount");
        return new Activity(
                transactionId,
                type,
                amount,
                rs.getString("currency"),
                rs.getString("status"),
                sourceInstant(rs, "created_at"),
                details);
    }

    private RiskEvidence mapRiskEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new RiskEvidence(
                rs.getObject("assessment_id", UUID.class),
                rs.getObject("transaction_id", UUID.class),
                rs.getObject("rule_id", UUID.class).toString(),
                rs.getString("rule_name"),
                sourceInstant(rs, "triggered_at"),
                rs.getBigDecimal("score_contribution"));
    }

    private java.time.Instant sourceInstant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).atZone(sourceTimeZone).toInstant();
    }

    private static void requireSpecialization(UUID transactionId, boolean present, String type) {
        if (!present) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " declares " + type + " without matching row");
        }
    }
}
