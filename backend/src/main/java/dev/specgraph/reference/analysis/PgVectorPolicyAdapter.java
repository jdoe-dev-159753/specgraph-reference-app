package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Pgvector retrieval adapter that derives a bounded lexical query from the complete customer
 * snapshot and maps matching Spring AI documents back to project-owned policy evidence.
 * Query terms are bounded independently of the later model-context selection.
 */
@Component
@Profile("r4")
final class PgVectorPolicyAdapter implements PolicyKnowledgePort {
    private static final int MAX_QUERY_CHARS = 4_000;
    private static final int MAX_ACTIVITY_TERMS = 50;
    private static final int MAX_RISK_TERMS = 20;

    private final VectorStore vectorStore;
    private final PolicyRetrievalProperties properties;

    PgVectorPolicyAdapter(
            @Qualifier("policyVectorStore") VectorStore vectorStore,
            PolicyRetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /** Maps only non-blank matches and preserves retriever/model identity as grounding metadata. */
    @Override
    public List<PolicyEvidence> retrieveRelevant(CustomerSnapshot snapshot) {
        SearchRequest request = SearchRequest.builder()
                .query(buildQuery(snapshot))
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .build();

        List<Document> matches = vectorStore.similaritySearch(request);
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<PolicyEvidence> evidence = new ArrayList<>(matches.size());
        for (Document document : matches) {
            String content = document.getText();
            if (content == null || content.isBlank()) {
                continue;
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            document.getMetadata().forEach((key, value) -> metadata.put(key, String.valueOf(value)));
            metadata.put("adapter", "pgvector");
            metadata.put("embeddingModel", properties.embedding().modelIdentity());
            if (document.getScore() != null) {
                metadata.put("similarityScore", document.getScore().toString());
            }
            evidence.add(new PolicyEvidence(document.getId(), content, metadata));
        }
        return List.copyOf(evidence);
    }

    /** Builds a bounded, newest-first lexical projection without exposing the complete history. */
    private String buildQuery(CustomerSnapshot snapshot) {
        StringJoiner terms = new StringJoiner(" ", "customer activity review policy ", "");

        snapshot.riskEvidence().stream()
                .sorted(Comparator.comparing(RiskEvidence::triggeredAt)
                        .reversed()
                        .thenComparing(RiskEvidence::assessmentId))
                .limit(MAX_RISK_TERMS)
                .forEach(risk -> {
                    terms.add("risk-rule");
                    addWords(terms, risk.ruleName());
                });

        snapshot.activities().stream()
                .sorted(Comparator.comparing(Activity::createdAt)
                        .reversed()
                        .thenComparing(Activity::transactionId))
                .limit(MAX_ACTIVITY_TERMS)
                .forEach(activity -> addActivityTerms(terms, activity));

        String query = terms.toString();
        return query.length() <= MAX_QUERY_CHARS ? query : query.substring(0, MAX_QUERY_CHARS);
    }

    /** Emits only review-relevant categorical terms from the closed activity variants. */
    private void addActivityTerms(StringJoiner terms, Activity activity) {
        terms.add(activity.type().name().toLowerCase(Locale.ROOT));
        terms.add(activity.status().toLowerCase(Locale.ROOT));

        switch (activity.details()) {
            case Activity.CardDetails card -> {
                terms.add(card.cardPresent() ? "card-present" : "card-not-present");
                if (card.declineReason() != null && !card.declineReason().isBlank()) {
                    terms.add("declined-card");
                }
            }
            case Activity.PaymentDetails payment -> {
                terms.add("payment");
                addWords(terms, payment.receiverBankCountry());
            }
            case Activity.CryptoDetails crypto -> {
                terms.add("crypto");
                addWords(terms, crypto.blockchain());
                addWords(terms, crypto.exchangeName());
            }
        }
    }

    /** Tokenizes free text into lowercase alphanumerics to keep the vector query deterministic. */
    private void addWords(StringJoiner terms, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
    }
}
