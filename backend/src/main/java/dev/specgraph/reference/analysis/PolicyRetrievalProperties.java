package dev.specgraph.reference.analysis;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.policy")
record PolicyRetrievalProperties(
        String corpusIndex,
        int topK,
        double similarityThreshold,
        String vectorTable,
        Chunking chunking,
        Embedding embedding) {

    PolicyRetrievalProperties {
        corpusIndex = requireText(corpusIndex, "corpusIndex");
        vectorTable = requireText(vectorTable, "vectorTable");
        Objects.requireNonNull(chunking, "chunking");
        Objects.requireNonNull(embedding, "embedding");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (!Double.isFinite(similarityThreshold) || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be in [0, 1]");
        }
    }

    record Chunking(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks) {
        Chunking {
            if (chunkSize <= 0 || minChunkSizeChars < 0 || minChunkLengthToEmbed < 0 || maxNumChunks <= 0) {
                throw new IllegalArgumentException("chunking parameters must be non-negative and bounded");
            }
        }
    }

    record Embedding(int dimensions, String modelIdentity, String tokenizerUri, String modelUri, String cacheDirectory) {
        Embedding {
            if (dimensions <= 0) {
                throw new IllegalArgumentException("embedding dimensions must be positive");
            }
            modelIdentity = requireText(modelIdentity, "modelIdentity");
            tokenizerUri = requireText(tokenizerUri, "tokenizerUri");
            modelUri = requireText(modelUri, "modelUri");
            cacheDirectory = requireText(cacheDirectory, "cacheDirectory");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
