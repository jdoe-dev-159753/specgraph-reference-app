package dev.specgraph.reference.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Proves stable chunk identity and transactional replacement/rollback using in-memory collaborators. */
final class SyntheticPolicyCorpusLoaderTests {
    @Test
    void corpusIdentityIsStableUuidAndIngestionReplacesTheOwnedSnapshotInOneTransaction() {
        VectorStore vectorStore = mock(VectorStore.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        SyntheticPolicyCorpusLoader loader = loader(vectorStore, transactionManager);

        List<String> firstIds = loader.loadDocuments().stream().map(Document::getId).toList();
        List<String> secondIds = loader.loadDocuments().stream().map(Document::getId).toList();

        assertThat(firstIds).isNotEmpty().containsExactlyElementsOf(secondIds);
        assertThat(firstIds).allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());

        loader.run(mock(ApplicationArguments.class));

        InOrder ordered = inOrder(vectorStore);
        ordered.verify(vectorStore).delete("corpus == 'synthetic'");
        ordered.verify(vectorStore).add(anyList());
        verify(transactionManager).commit(transaction);
    }

    @Test
    void failedReplacementRollsBackInsteadOfCommittingAnEmptyCorpus() {
        VectorStore vectorStore = mock(VectorStore.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        doThrow(new IllegalStateException("embedding failed")).when(vectorStore).add(anyList());
        SyntheticPolicyCorpusLoader loader = loader(vectorStore, transactionManager);

        assertThatThrownBy(() -> loader.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding failed");

        verify(vectorStore).delete("corpus == 'synthetic'");
        verify(transactionManager).rollback(transaction);
    }

    private SyntheticPolicyCorpusLoader loader(
            VectorStore vectorStore,
            PlatformTransactionManager transactionManager) {
        return new SyntheticPolicyCorpusLoader(
                vectorStore,
                new DefaultResourceLoader(),
                properties(),
                new TransactionTemplate(transactionManager));
    }

    /** Provides a stable small chunking configuration so tests isolate replacement semantics. */
    private PolicyRetrievalProperties properties() {
        return new PolicyRetrievalProperties(
                "classpath:policy/synthetic/index.txt",
                3,
                0.35,
                "policy_vector_store",
                new PolicyRetrievalProperties.Chunking(180, 80, 20, 20),
                new PolicyRetrievalProperties.Embedding(
                        384,
                        "test-embedding",
                        "https://example.invalid/tokenizer.json",
                        "https://example.invalid/model.onnx",
                        "/tmp/model"));
    }
}
