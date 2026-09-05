package dev.specgraph.reference.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Source transaction activity belonging to a customer snapshot.
 *
 * <p>The sealed detail variant must match {@link ActivityType}; construction rejects mismatches so
 * downstream detectors can exhaustively interpret card, payment and crypto-specific evidence.
 * This record carries observed source data and must not be confused with a derived risk signal.
 */
public record Activity(
        UUID transactionId,
        ActivityType type,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        ActivityDetails details) {
    /** Enforces the discriminator/detail invariant before an activity enters any downstream stage. */
    public Activity {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(details, "details");

        boolean matchingDetails = switch (type) {
            case CARD -> details instanceof CardDetails;
            case PAYMENT -> details instanceof PaymentDetails;
            case CRYPTO -> details instanceof CryptoDetails;
        };
        if (!matchingDetails) {
            throw new IllegalArgumentException("activity details must match activity type " + type);
        }
    }

    /** Source activity family and discriminator for the corresponding detail variant. */
    public enum ActivityType { CARD, PAYMENT, CRYPTO }

    /** Closed provider-neutral detail family selected by {@link ActivityType}. */
    public sealed interface ActivityDetails permits CardDetails, PaymentDetails, CryptoDetails {}

    /** Card-transaction evidence; nullable provider fields remain unnormalized source values. */
    public record CardDetails(
            String cardPan,
            String cardType,
            String merchantName,
            String mccCode,
            boolean cardPresent,
            String authorizationCode,
            String declineReason) implements ActivityDetails {}

    /** Account-payment evidence retained from the source activity provider. */
    public record PaymentDetails(
            String paymentMethod,
            String senderAccount,
            String receiverAccount,
            String receiverBankCountry) implements ActivityDetails {}

    /** Crypto-transaction evidence retained from the source activity provider. */
    public record CryptoDetails(
            String blockchain,
            String walletAddressFrom,
            String walletAddressTo,
            String txHash,
            String exchangeName) implements ActivityDetails {}
}
