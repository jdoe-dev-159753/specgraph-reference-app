package dev.specgraph.reference.analysis;

import java.util.Map;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("r4")
@EnableConfigurationProperties(PolicyRetrievalProperties.class)
class PgVectorPolicyConfiguration {

    @Bean("policyEmbeddingModel")
    @Profile("!test")
    EmbeddingModel policyEmbeddingModel(PolicyRetrievalProperties properties) {
        var model = new TransformersEmbeddingModel();
        model.setTokenizerResource(properties.embedding().tokenizerUri());
        model.setModelResource(properties.embedding().modelUri());
        model.setResourceCacheDirectory(properties.embedding().cacheDirectory());
        model.setTokenizerOptions(Map.of("padding", "true"));
        return model;
    }

    @Bean("policyVectorStore")
    VectorStore policyVectorStore(
            JdbcTemplate jdbcTemplate,
            @Qualifier("policyEmbeddingModel") EmbeddingModel embeddingModel,
            PolicyRetrievalProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName(properties.vectorTable())
                .idType(PgVectorStore.PgIdType.UUID)
                .dimensions(properties.embedding().dimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
    }

    @Bean("policyCorpusTransactions")
    TransactionOperations policyCorpusTransactions(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
