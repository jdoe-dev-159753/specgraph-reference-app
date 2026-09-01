package dev.specgraph.reference.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Activity(
        UUID transactionId,
        ActivityType type,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        ActivityDetails details) {
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

    public enum ActivityType { CARD, PAYMENT, CRYPTO }

    public sealed interface ActivityDetails permits CardDetails, PaymentDetails, CryptoDetails {}

    public record CardDetails(
            String cardPan,
            String cardType,
            String merchantName,
            String mccCode,
            boolean cardPresent,
            String authorizationCode,
            String declineReason) implements ActivityDetails {}

    public record PaymentDetails(
            String paymentMethod,
            String senderAccount,
            String receiverAccount,
            String receiverBankCountry) implements ActivityDetails {}

    public record CryptoDetails(
            String blockchain,
            String walletAddressFrom,
            String walletAddressTo,
            String txHash,
            String exchangeName) implements ActivityDetails {}
}
