package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.CustomerActivityPort;
import dev.specgraph.reference.customer.CustomerReviewPage;
import dev.specgraph.reference.customer.CustomerReviewQuery;
import dev.specgraph.reference.customer.CustomerReviewQueryPort;
import dev.specgraph.reference.customer.CustomerSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** Port-level failures compiled only into the isolated degradation-test image. */
@Configuration(proxyBeanMethods = false)
@Profile("r4-degradation-test")
class DegradationTestConfiguration {

    @Bean
    static BeanFactoryPostProcessor degradationDelegatesAreNotPrimary() {
        return beanFactory -> {
            beanFactory.getBeanDefinition("selectedRiskSignalDetector").setPrimary(false);
            beanFactory.getBeanDefinition("selectedAnalysisModel").setPrimary(false);
            beanFactory.getBeanDefinition("jpaAnalysisHistoryAdapter").setPrimary(false);
            beanFactory.getBeanDefinition("jpaCustomerActivityAdapter").setPrimary(false);
        };
    }

    @Bean
    ScenarioPlan degradationScenarioPlan() {
        return new ScenarioPlan();
    }

    @Bean
    @Primary
    FailureInjectingCustomerActivity degradationCustomerActivity(
            @Qualifier("jpaCustomerActivityAdapter") CustomerActivityPort activityDelegate,
            @Qualifier("jpaCustomerActivityAdapter") CustomerReviewQueryPort reviewDelegate,
            ScenarioPlan plan) {
        return new FailureInjectingCustomerActivity(activityDelegate, reviewDelegate, plan);
    }

    @Bean
    @Primary
    RiskSignalDetectorPort degradationRiskSignalDetector(
            @Qualifier("selectedRiskSignalDetector") RiskSignalDetectorPort delegate,
            ScenarioPlan plan) {
        return snapshot -> {
            plan.fail(FailureStage.DETECTOR, "Injected detector failure");
            return delegate.detect(snapshot);
        };
    }

    @Bean
    @Primary
    PolicyKnowledgePort degradationPolicyKnowledge(
            @Qualifier("pgVectorPolicyAdapter") PolicyKnowledgePort delegate,
            ScenarioPlan plan) {
        return snapshot -> {
            if (plan.failWithEmptyGrounding()) {
                return List.of();
            }
            plan.fail(FailureStage.GROUNDING, "Injected policy retrieval failure");
            return delegate.retrieveRelevant(snapshot);
        };
    }

    @Bean
    @Primary
    AnalysisModelPort degradationAnalysisModel(
            @Qualifier("selectedAnalysisModel") AnalysisModelPort delegate,
            ScenarioPlan plan) {
        return evidence -> {
            plan.fail(FailureStage.MODEL, "Injected analysis model failure");
            if (plan.claim(FailureStage.INVALID_RESULT)) {
                throw new InvalidAnalysisResultException("Injected invalid structured result");
            }
            return delegate.analyze(evidence);
        };
    }

    @Bean
    @Primary
    AnalysisHistoryPort degradationAnalysisHistory(
            @Qualifier("jpaAnalysisHistoryAdapter") AnalysisHistoryPort delegate,
            ScenarioPlan plan) {
        return new FailureInjectingHistory(delegate, plan);
    }

    /** Ordered failure/recovery steps traversed by the browser degradation scenario. */
    private enum FailureStage {
        DATABASE,
        DETECTOR,
        INSUFFICIENT_GROUNDING,
        GROUNDING,
        MODEL,
        INVALID_RESULT,
        PERSISTENCE
    }

    /**
     * Synchronizes one injected failure with its successful retry before advancing to the next stage.
     * This state is fixture-local and is not an application lifecycle model.
     */
    static final class ScenarioPlan {
        private final FailureStage[] stages = FailureStage.values();
        private int next;
        private boolean awaitingRecovery;

        /** Claims only the next scheduled failure and requires a successful recovery before advancing. */
        synchronized boolean claim(FailureStage stage) {
            if (awaitingRecovery || next >= stages.length || stages[next] != stage) {
                return false;
            }
            awaitingRecovery = true;
            return true;
        }

        void fail(FailureStage stage, String message) {
            if (claim(stage)) {
                throw new IllegalStateException(message);
            }
        }

        boolean failWithEmptyGrounding() {
            return claim(FailureStage.INSUFFICIENT_GROUNDING);
        }

        /** Advances a port-level stage only after its real delegate completes successfully. */
        synchronized void recoveryCompleted(FailureStage stage) {
            if (awaitingRecovery && next < stages.length && stages[next] == stage) {
                awaitingRecovery = false;
                next++;
            }
        }

        /** Advances analysis stages only once the successful retry has reached durable history. */
        synchronized void analysisRecoveryPersisted() {
            if (awaitingRecovery && next < stages.length && stages[next] != FailureStage.DATABASE) {
                awaitingRecovery = false;
                next++;
            }
        }
    }

    /** Decorates both customer ports so database failure and recovery use the real delegate path. */
    private record FailureInjectingCustomerActivity(
            CustomerActivityPort activityDelegate,
            CustomerReviewQueryPort reviewDelegate,
            ScenarioPlan plan) implements CustomerActivityPort, CustomerReviewQueryPort {

        /** Injects the database failure before delegation and records only a successful recovery. */
        @Override
        public Optional<CustomerSnapshot> loadSnapshot(UUID customerId) {
            plan.fail(FailureStage.DATABASE, "Injected customer database query failure");
            Optional<CustomerSnapshot> snapshot = activityDelegate.loadSnapshot(customerId);
            plan.recoveryCompleted(FailureStage.DATABASE);
            return snapshot;
        }

        /** Applies the same database degradation semantics to the paged review boundary. */
        @Override
        public Optional<CustomerReviewPage> loadReviewPage(UUID customerId, CustomerReviewQuery query) {
            plan.fail(FailureStage.DATABASE, "Injected customer database query failure");
            Optional<CustomerReviewPage> page = reviewDelegate.loadReviewPage(customerId, query);
            plan.recoveryCompleted(FailureStage.DATABASE);
            return page;
        }
    }

    /** Decorates history persistence while leaving read behavior on the real JPA adapter. */
    private record FailureInjectingHistory(
            AnalysisHistoryPort delegate,
            ScenarioPlan plan) implements AnalysisHistoryPort {

        /** Fails before persistence, then advances only after the real adapter commits the retry. */
        @Override
        public AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
            plan.fail(FailureStage.PERSISTENCE, "Injected analysis history persistence failure");
            AnalysisHistoryEntry persisted = delegate.persist(command);
            plan.analysisRecoveryPersisted();
            return persisted;
        }

        @Override
        public List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
            return delegate.listByCustomer(customerId);
        }

        @Override
        public AnalysisHistoryPage pageByCustomer(UUID customerId, AnalysisHistoryQuery query) {
            return delegate.pageByCustomer(customerId, query);
        }

        @Override
        public Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId) {
            return delegate.findByCustomerAndId(customerId, analysisId);
        }
    }
}
