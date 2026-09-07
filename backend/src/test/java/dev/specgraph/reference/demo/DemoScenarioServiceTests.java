package dev.specgraph.reference.demo;
import static org.assertj.core.api.Assertions.assertThat;
import dev.specgraph.reference.customer.Activity;
import dev.specgraph.reference.customer.CustomerSnapshot;
import dev.specgraph.reference.demo.DemoScenarioPersistencePort.ScenarioData;
import dev.specgraph.reference.demo.DemoScenarioUseCase.ScenarioFamily;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
/** Verifies exact replay, bounded variation, and source-story coherence for every generated family. */
@Tag("VFY-REPRODUCIBILITY-001")
class DemoScenarioServiceTests {
    /** Proves same-seed identity and different-seed variation without relaxing coherence invariants. */
    @ParameterizedTest
    @EnumSource(ScenarioFamily.class)
    void familyReplaysExactlyAndVariesBoundedCoherentEvidence(ScenarioFamily family) {
        List<ScenarioData> saved = new ArrayList<>();
        DemoScenarioService service = new DemoScenarioService(saved::add);
        assertThat(service.generate(41, family)).isEqualTo(service.generate(41, family));
        service.generate(42, family);
        assertThat(saved.get(0)).isEqualTo(saved.get(1));
        CustomerSnapshot first = saved.get(0).snapshot();
        CustomerSnapshot second = saved.get(2).snapshot();
        assertCoherent(first);
        assertCoherent(second);
        assertThat(first.customerId()).isNotEqualTo(second.customerId());
        assertThat(first.activities()).extracting(Activity::amount).isNotEqualTo(second.activities().stream().map(Activity::amount).toList());
        assertThat(first.activities()).extracting(Activity::createdAt).isNotEqualTo(second.activities().stream().map(Activity::createdAt).toList());
        assertThat(first.activities()).extracting(Activity::type).isNotEqualTo(second.activities().stream().map(Activity::type).toList());
        assertThat(counterparties(first)).isNotEqualTo(counterparties(second));
        List<String> currencies = java.util.stream.Stream.concat(first.activities().stream(), second.activities().stream())
                .map(Activity::currency).toList();
        switch (family) {
            case ORDINARY_LOCAL -> { assertThat(currencies).containsOnly("CHF"); assertThat(first.riskEvidence()).isEmpty(); }
            case CROSS_BORDER_GROWTH -> { assertThat(currencies).contains("CHF", "EUR", "USD", "ETH"); assertThat(first.riskEvidence()).hasSize(1); }
            case MIXED_RED_FLAGS -> { assertThat(currencies).contains("EUR", "USD", "ETH"); assertThat(first.riskEvidence()).hasSize(4); }
        }
    }
    private static void assertCoherent(CustomerSnapshot snapshot) {
        assertThat(snapshot.activities()).hasSize(6).isSortedAccordingTo(Comparator.comparing(Activity::createdAt));
        assertThat(snapshot.activities()).extracting(Activity::transactionId).doesNotHaveDuplicates();
        var ids = snapshot.activities().stream().map(Activity::transactionId).collect(java.util.stream.Collectors.toSet());
        snapshot.riskEvidence().forEach(risk -> {
            assertThat(ids).contains(risk.transactionId());
            Activity source = snapshot.activities().stream().filter(a -> a.transactionId().equals(risk.transactionId())).findFirst().orElseThrow();
            assertThat(risk.triggeredAt()).isAfter(source.createdAt());
        });
    }
    private static List<String> counterparties(CustomerSnapshot snapshot) {
        return snapshot.activities().stream().filter(a -> a.details() instanceof Activity.PaymentDetails)
                .map(a -> ((Activity.PaymentDetails) a.details()).receiverAccount()).toList();
    }
}
