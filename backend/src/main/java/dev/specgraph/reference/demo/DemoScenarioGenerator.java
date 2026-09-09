package dev.specgraph.reference.demo;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.demo.DemoScenarioPersistencePort.ScenarioData;
import dev.specgraph.reference.demo.DemoScenarioUseCase.ScenarioFamily;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/** Pure deterministic generator for replayable source-shaped demonstration scenarios. */
final class DemoScenarioGenerator {
    private static final String GENERATOR_ID = "jdk-splittable-random-v1";
    private static final UUID RULE_CROSS_BORDER = rule("3"), RULE_FAILURES = rule("4"),
            RULE_HIGH_VALUE = rule("5"), RULE_CRYPTO = rule("2");

    ScenarioData generate(long seed, ScenarioFamily family) {
        String identity = GENERATOR_ID + ":" + family + ":" + seed;
        SplittableRandom random = new SplittableRandom(seed ^ ((long) family.ordinal() << 32));
        Instant anchor = Instant.parse("2026-08-01T08:00:00Z").plus(Math.floorMod(seed, 21), ChronoUnit.DAYS);
        List<Activity> activities = new ArrayList<>();
        for (int position = 0; position < 6; position++) {
            activities.add(activity(identity, seed, family, random, anchor, position));
        }
        UUID customerId = stableId(identity + ":customer");
        CustomerSnapshot snapshot = new CustomerSnapshot(customerId, activities, risks(identity, family, activities));
        return new ScenarioData(seed, family, GENERATOR_ID, anchor, snapshot);
    }

    private static Activity activity(
            String identity, long seed, ScenarioFamily family, SplittableRandom random, Instant anchor, int position) {
        UUID id = stableId(identity + ":transaction:" + position);
        Instant at = anchor.plus(position * 18L + random.nextInt(7), ChronoUnit.HOURS);
        int offset = Math.floorMod(seed, family == ScenarioFamily.ORDINARY_LOCAL ? 2 : 6);
        int storyIndex = Math.floorMod(position + offset, 6);
        if (family == ScenarioFamily.ORDINARY_LOCAL) {
            if (storyIndex % 2 == 0) {
                return new Activity(id, Activity.ActivityType.CARD, money(2_000 + random.nextInt(18_000)),
                        "CHF", "Completed", at, new Activity.CardDetails("**** **** **** 1100", "VISA",
                        "Local merchant " + position, "5411", true, "L" + seedPart(random), null));
            }
            return payment(id, money(10_000 + random.nextInt(90_000)), "CHF", at, "CH", random);
        }
        if (family == ScenarioFamily.CROSS_BORDER_GROWTH && storyIndex < 5) {
            String country = List.of("CH", "CH", "DE", "NL", "GB").get(storyIndex);
            String currency = country.equals("CH") ? "CHF" : foreignCurrency(seed);
            return payment(id, money((storyIndex + 1) * 70_000 + random.nextInt(20_000)), currency, at,
                    country, random);
        }
        if (family == ScenarioFamily.MIXED_RED_FLAGS && storyIndex < 2) {
            return new Activity(id, Activity.ActivityType.CARD, money(300_000 + random.nextInt(150_000)),
                    "USD", "Declined", at, new Activity.CardDetails("**** **** **** 9009", "VISA",
                    "Remote electronics", "5732", false, "D" + seedPart(random), "Issuer declined"));
        }
        if (family == ScenarioFamily.MIXED_RED_FLAGS && storyIndex < 5) {
            return payment(id, money(1_800_000 + random.nextInt(900_000)), foreignCurrency(seed), at,
                    List.of("GB", "AE", "NL").get(storyIndex - 2), random);
        }
        return new Activity(id, Activity.ActivityType.CRYPTO, new BigDecimal("8.00"), "ETH", "Completed", at,
                new Activity.CryptoDetails("Ethereum", "0x-demo-source", "0x-new-" + seedPart(random),
                        "eth-" + seedPart(random), "Synthetic Exchange"));
    }

    private static Activity payment(
            UUID id, BigDecimal amount, String currency, Instant at, String country, SplittableRandom random) {
        return new Activity(id, Activity.ActivityType.PAYMENT, amount, currency, "Completed", at,
                new Activity.PaymentDetails("BANK_TRANSFER", "CH00-GENERATED-SOURCE",
                        country + "00-COUNTERPARTY-" + seedPart(random), country));
    }

    private static List<RiskEvidence> risks(String identity, ScenarioFamily family, List<Activity> activities) {
        if (family == ScenarioFamily.ORDINARY_LOCAL) {
            return List.of();
        }
        List<RiskEvidence> risks = new ArrayList<>();
        Activity crossBorder = activities.stream()
                .filter(activity -> activity.details() instanceof Activity.PaymentDetails details
                        && !details.receiverBankCountry().equals("CH"))
                .findFirst()
                .orElseThrow();
        risks.add(risk(identity, crossBorder, RULE_CROSS_BORDER, "Growing cross-border payment activity", 15));
        if (family == ScenarioFamily.MIXED_RED_FLAGS) {
            Activity failed = first(activities, Activity.ActivityType.CARD);
            Activity highValue = first(activities, Activity.ActivityType.PAYMENT);
            Activity crypto = first(activities, Activity.ActivityType.CRYPTO);
            risks.add(risk(identity, failed, RULE_FAILURES, "Repeated card failures", 20));
            risks.add(risk(identity, highValue, RULE_HIGH_VALUE, "High-value transfer anomaly", 25));
            risks.add(risk(identity, crypto, RULE_CRYPTO, "New crypto destination", 18));
        }
        return List.copyOf(risks);
    }

    private static Activity first(List<Activity> activities, Activity.ActivityType type) {
        return activities.stream().filter(activity -> activity.type() == type).findFirst().orElseThrow();
    }

    private static RiskEvidence risk(String identity, Activity activity, UUID rule, String name, int score) {
        return new RiskEvidence(stableId(identity + ":risk:" + rule), activity.transactionId(), rule.toString(), name,
                activity.createdAt().plusSeconds(1), BigDecimal.valueOf(score));
    }

    private static String foreignCurrency(long seed) {
        return Math.floorMod(seed, 2) == 0 ? "EUR" : "USD";
    }

    private static UUID rule(String suffix) {
        return UUID.fromString("10000000-0000-0000-0000-00000000000" + suffix);
    }

    private static UUID stableId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal money(int cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private static int seedPart(SplittableRandom random) {
        return random.nextInt(100_000, 999_999);
    }
}
