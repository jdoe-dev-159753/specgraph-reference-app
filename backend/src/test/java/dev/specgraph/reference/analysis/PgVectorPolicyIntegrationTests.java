package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.risk.RiskEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles({"r4", "test"})
@Import(PgVectorPolicyIntegrationTests.DeterministicEmbeddingConfiguration.class)
final class PgVectorPolicyIntegrationTests {
    private static final int DIMENSIONS = 384;
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("specgraph")
            .withUsername("specgraph")
            .withPassword("specgraph");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("specgraph.policy.similarity-threshold", () -> "0.05");
    }

    @Autowired
    private PolicyKnowledgePort policyKnowledgePort;

    @Autowired
    private SyntheticPolicyCorpusLoader corpusLoader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore policyVectorStore;

    @Test
    void retrievesGroundedTypedPolicyEvidenceFromRealPgvector() {
        List<PolicyEvidence> evidence = policyKnowledgePort.retrieveRelevant(crossBorderPaymentSnapshot());

        assertThat(evidence).isNotEmpty();
        assertThat(evidence)
                .allSatisfy(item -> {
                    assertThat(item.kind())
                            .isEqualTo(AnalysisPipelineArtifact.Kind.POLICY_RETRIEVAL_EVIDENCE);
                    assertThat(item.metadata())
                            .containsEntry("adapter", "pgvector")
                            .containsEntry("corpus", "synthetic")
                            .containsEntry("revision", "r4")
                            .containsKey("similarityScore");
                });
        assertThat(evidence)
                .anySatisfy(item -> assertThat(item.metadata().get("sourceDocument"))
                        .endsWith("cross-border-payment-review.md"));
    }

    @Test
    void flywayOwnsVectorSchemaAndCorpusIngestionIsIdempotent() {
        String vectorVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        assertThat(vectorVersion).isNotBlank();

        List<String> firstIds = storedDocumentIds();
        List<String> expectedIds = corpusLoader.loadDocuments().stream()
                .map(Document::getId)
                .sorted()
                .toList();
        int firstCount = firstIds.size();

        assertThat(firstCount).isPositive();
        assertThat(firstIds).containsExactlyElementsOf(expectedIds);
        assertThat(firstIds).allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());

        policyVectorStore.add(corpusLoader.loadDocuments());

        assertThat(storedDocumentIds()).containsExactlyElementsOf(firstIds);
        assertThat(storedDocumentIds()).hasSize(firstCount);
    }

    private List<String> storedDocumentIds() {
        return jdbcTemplate.queryForList(
                "select id from policy_vector_store order by id", String.class);
    }

    private CustomerSnapshot crossBorderPaymentSnapshot() {
        UUID transactionId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        Activity payment = new Activity(
                transactionId,
                Activity.ActivityType.PAYMENT,
                new BigDecimal("25000.00"),
                "CHF",
                "COMPLETED",
                Instant.parse("2026-08-25T10:15:30Z"),
                new Activity.PaymentDetails(
                        "BANK_TRANSFER",
                        "CH-DEMO-SENDER",
                        "DE-DEMO-RECEIVER",
                        "DE"));
        RiskEvidence risk = new RiskEvidence(
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                transactionId,
                "RISK-CROSS-BORDER-GROWTH",
                "Cross Border Payment Growth",
                Instant.parse("2026-08-25T10:16:00Z"),
                new BigDecimal("0.42"));
        return new CustomerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000900"),
                List.of(payment),
                List.of(risk));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicEmbeddingConfiguration {
        @Bean("policyEmbeddingModel")
        EmbeddingModel policyEmbeddingModel() {
            return new DeterministicHashEmbeddingModel();
        }
    }

    /** Test-only hashing-trick embedding: deterministic, bounded and network-free. */
    static final class DeterministicHashEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(this::vector)
                    .map(vector -> new Embedding(vector, 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private float[] vector(String text) {
            float[] vector = new float[DIMENSIONS];
            if (text == null || text.isBlank()) {
                return vector;
            }
            Set<String> tokens = Set.copyOf(Arrays.asList(
                    text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                int dimension = Math.floorMod(token.hashCode(), DIMENSIONS);
                vector[dimension] += 1.0f;
            }
            double norm = 0.0;
            for (float value : vector) {
                norm += value * value;
            }
            if (norm > 0.0) {
                float scale = (float) (1.0 / Math.sqrt(norm));
                for (int index = 0; index < vector.length; index++) {
                    vector[index] *= scale;
                }
            }
            return vector;
        }
    }
}
