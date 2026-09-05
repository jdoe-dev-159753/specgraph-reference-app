package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Verifies bounded query construction and retrieval provenance through a mocked vector store.
 * Real PostgreSQL similarity semantics are owned by the separate integration suite.
 */
final class PgVectorPolicyAdapterTests {
    @Test
    void retrievalProvenanceUsesConfiguredEmbeddingModelIdentity() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder()
                        .id("policy-doc")
                        .text("Synthetic policy evidence")
                        .metadata(Map.of("corpus", "synthetic", "revision", "r4"))
                        .build()));

        PgVectorPolicyAdapter adapter = new PgVectorPolicyAdapter(vectorStore, properties("custom-embedding-v2"));
        List<PolicyEvidence> evidence = adapter.retrieveRelevant(new CustomerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000999"), List.of(), List.of()));

        assertThat(evidence).singleElement().satisfies(item -> assertThat(item.metadata())
                .containsEntry("adapter", "pgvector")
                .containsEntry("embeddingModel", "custom-embedding-v2"));
    }

    @Test
    void retrievalQueryKeepsNewestActivitiesWhenSnapshotExceedsBound() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        PgVectorPolicyAdapter adapter = new PgVectorPolicyAdapter(vectorStore, properties("test-embedding"));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<Activity> activities = IntStream.rangeClosed(0, 50)
                .mapToObj(index -> new Activity(
                        UUID.nameUUIDFromBytes(("activity-" + index).getBytes(StandardCharsets.UTF_8)),
                        Activity.ActivityType.CRYPTO,
                        BigDecimal.ONE,
                        "CHF",
                        "COMPLETED",
                        start.plusSeconds(index),
                        new Activity.CryptoDetails(
                                "ethereum",
                                "from-wallet",
                                "to-wallet",
                                "tx-" + index,
                                index == 0 ? "oldestmarker" : index == 50 ? "newestmarker" : "middlemarker")))
                .toList();

        adapter.retrieveRelevant(new CustomerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000998"), activities, List.of()));

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery())
                .contains("newestmarker")
                .doesNotContain("oldestmarker");
    }

    @Test
    void retrievalQueryKeepsNewestRiskEvidenceWhenSnapshotExceedsBound() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        PgVectorPolicyAdapter adapter = new PgVectorPolicyAdapter(vectorStore, properties("test-embedding"));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<RiskEvidence> riskEvidence = IntStream.rangeClosed(0, 20)
                .mapToObj(index -> new RiskEvidence(
                        UUID.nameUUIDFromBytes(("assessment-" + index).getBytes(StandardCharsets.UTF_8)),
                        UUID.nameUUIDFromBytes(("risk-transaction-" + index).getBytes(StandardCharsets.UTF_8)),
                        "RISK-" + index,
                        index == 0 ? "oldestriskmarker" : index == 20 ? "newestriskmarker" : "middleriskmarker",
                        start.plusSeconds(index),
                        BigDecimal.ONE))
                .toList();

        adapter.retrieveRelevant(new CustomerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000997"), List.of(), riskEvidence));

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery())
                .contains("newestriskmarker")
                .doesNotContain("oldestriskmarker");
    }

    /** Builds the minimal valid retrieval boundary while allowing provenance identity to vary. */
    private PolicyRetrievalProperties properties(String embeddingIdentity) {
        return new PolicyRetrievalProperties(
                "classpath:policy/synthetic/index.txt",
                3,
                0.35,
                "policy_vector_store",
                new PolicyRetrievalProperties.Chunking(180, 80, 20, 20),
                new PolicyRetrievalProperties.Embedding(
                        384,
                        embeddingIdentity,
                        "https://example.invalid/tokenizer.json",
                        "https://example.invalid/model.onnx",
                        "/tmp/model"));
    }
}
