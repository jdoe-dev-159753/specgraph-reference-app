package dev.specgraph.reference.demo.persistence;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.demo.DemoScenarioPersistencePort;
import dev.specgraph.reference.risk.RiskEvidence;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/** Atomically materializes generated evidence in the existing source schema. */
@Component
final class JdbcDemoScenarioPersistenceAdapter implements DemoScenarioPersistencePort {
    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final ZoneId sourceTimeZone;
    JdbcDemoScenarioPersistenceAdapter(JdbcTemplate jdbc, TransactionOperations transactions,
            @Value("${specgraph.source-time-zone:UTC}") String sourceTimeZone) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.sourceTimeZone = ZoneId.of(sourceTimeZone);
    }
    @Override public void save(ScenarioData scenario) { transactions.executeWithoutResult(status -> persist(scenario)); }
    /** Writes customer, activities, source risks, and replay provenance in one transaction. */
    private void persist(ScenarioData scenario) {
        CustomerSnapshot snapshot = scenario.snapshot();
        jdbc.update("INSERT INTO customers(customer_id) VALUES (?) ON CONFLICT DO NOTHING", snapshot.customerId());
        for (Activity activity : snapshot.activities()) {
            jdbc.update("""
                    INSERT INTO transactions(transaction_id, customer_id, activity_type, amount, currency, status, created_at)
                    VALUES (?, ?, CAST(? AS activity_type_enum), ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, activity.transactionId(), snapshot.customerId(), activity.type().name(), activity.amount(),
                    activity.currency(), activity.status(), sourceTime(activity.createdAt()));
            persistDetails(activity);
        }
        for (RiskEvidence risk : snapshot.riskEvidence()) {
            jdbc.update("""
                    INSERT INTO risk_assessments(assessment_id, transaction_id, rule_id, triggered_at, score_contribution)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, risk.assessmentId(), risk.transactionId(), UUID.fromString(risk.ruleId()),
                    sourceTime(risk.triggeredAt()), risk.scoreContribution());
        }
        jdbc.update("""
                INSERT INTO generated_scenarios(customer_id, seed, family, generator_identity, scenario_anchor_at,
                    activity_count, risk_evidence_count) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, snapshot.customerId(), scenario.seed(), scenario.family().name(), scenario.generatorIdentity(),
                scenario.scenarioAnchor().atOffset(ZoneOffset.UTC),
                snapshot.activities().size(), snapshot.riskEvidence().size());
    }
    /** Routes each generated activity into its existing CARD, PAYMENT, or CRYPTO detail table. */
    private void persistDetails(Activity activity) {
        if (activity.details() instanceof Activity.CardDetails card) {
            jdbc.update("INSERT INTO card_activity VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    activity.transactionId(), card.cardPan(), card.cardType(), card.merchantName(), card.mccCode(),
                    card.cardPresent(), card.authorizationCode(), card.declineReason());
        } else if (activity.details() instanceof Activity.PaymentDetails payment) {
            jdbc.update("INSERT INTO payment_activity VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    activity.transactionId(), payment.paymentMethod(), payment.senderAccount(), payment.receiverAccount(),
                    payment.receiverBankCountry());
        } else if (activity.details() instanceof Activity.CryptoDetails crypto) {
            jdbc.update("INSERT INTO crypto_activity VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    activity.transactionId(), crypto.blockchain(), crypto.walletAddressFrom(), crypto.walletAddressTo(),
                    crypto.txHash(), crypto.exchangeName());
        }
    }
    private LocalDateTime sourceTime(Instant instant) { return LocalDateTime.ofInstant(instant, sourceTimeZone); }
}
