package dev.specgraph.reference.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Rebuilds the owned synthetic policy-vector snapshot from repository resources at startup.
 * Stable content-derived UUIDv8 identifiers make repeated ingestion idempotent, while one
 * transaction prevents a failed replacement from committing an empty corpus.
 */
@Component
@Profile("r4")
final class SyntheticPolicyCorpusLoader implements ApplicationRunner {
    private static final String CORPUS = "synthetic";
    private static final String REVISION = "r4";

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final PolicyRetrievalProperties properties;
    private final TransactionOperations transactions;

    SyntheticPolicyCorpusLoader(
            @Qualifier("policyVectorStore") VectorStore vectorStore,
            ResourceLoader resourceLoader,
            PolicyRetrievalProperties properties,
            @Qualifier("policyCorpusTransactions") TransactionOperations transactions) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.transactions = transactions;
    }

    /** Replaces the loader-owned corpus snapshot atomically, including the intentional empty case. */
    @Override
    public void run(ApplicationArguments args) {
        List<Document> documents = loadDocuments();
        transactions.executeWithoutResult(status -> {
            vectorStore.delete("corpus == '" + CORPUS + "'");
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
            }
        });
    }

    List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();
        for (String sourcePath : readIndex()) {
            documents.addAll(chunk(sourcePath, readResource(resourceLoader.getResource(sourcePath))));
        }
        return List.copyOf(documents);
    }

    private List<String> readIndex() {
        return readResource(resourceLoader.getResource(properties.corpusIndex())).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .toList();
    }

    /** Splits one policy resource and assigns stable content identities to usable chunks. */
    private List<Document> chunk(String sourcePath, String content) {
        var chunking = properties.chunking();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunking.chunkSize())
                .withMinChunkSizeChars(chunking.minChunkSizeChars())
                .withMinChunkLengthToEmbed(chunking.minChunkLengthToEmbed())
                .withMaxNumChunks(chunking.maxNumChunks())
                .build();

        Document source = Document.builder()
                .text(content)
                .metadata(Map.of(
                        "sourceDocument", sourcePath,
                        "corpus", CORPUS,
                        "revision", REVISION))
                .build();
        List<Document> chunks = splitter.apply(List.of(source));

        List<Document> stableChunks = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            String chunkText = chunks.get(index).getText();
            if (chunkText == null || chunkText.isBlank()) {
                continue;
            }
            String id = deterministicId(sourcePath, index, chunkText);
            stableChunks.add(Document.builder()
                    .id(id)
                    .text(chunkText)
                    .metadata(Map.of(
                            "sourceDocument", sourcePath,
                            "corpus", CORPUS,
                            "revision", REVISION,
                            "chunkIndex", index,
                            "totalChunks", chunks.size()))
                    .build());
        }
        return stableChunks;
    }

    /** Derives an RFC-variant UUIDv8 from source path, chunk position, and exact chunk content. */
    private String deterministicId(String sourcePath, int chunkIndex, String content) {
        String stableSeed = sourcePath + "\n" + chunkIndex + "\n" + content;
        byte[] digest = sha256(stableSeed);
        ByteBuffer bytes = ByteBuffer.wrap(digest);
        long mostSignificantBits = bytes.getLong();
        long leastSignificantBits = bytes.getLong();

        // UUIDv8 leaves the payload semantics application-defined. Here the payload is
        // the first 128 bits of the SHA-256 content identity, with RFC variant bits set.
        mostSignificantBits = (mostSignificantBits & 0xffffffffffff0fffL) | 0x0000000000008000L;
        leastSignificantBits = (leastSignificantBits & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available for deterministic policy identities", exception);
        }
    }

    private String readResource(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read synthetic policy resource " + resource, exception);
        }
    }
}
