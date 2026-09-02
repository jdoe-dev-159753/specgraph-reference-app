package dev.specgraph.reference.analysis.persistence;

import dev.specgraph.reference.analysis.AnalysisHistoryCreateCommand;
import dev.specgraph.reference.analysis.AnalysisHistoryEntry;
import dev.specgraph.reference.analysis.AnalysisHistoryPort;
import dev.specgraph.reference.analysis.AnalysisModelProvenance;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.analysis.PolicyEvidence;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import dev.specgraph.reference.identity.OperatorId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@Primary
class JdbcAnalysisHistoryAdapter implements AnalysisHistoryPort {
    private static final String INSERT_SQL = """
            INSERT INTO analysis_history(
                analysis_id,
                customer_id,
                operator_id,
                generated_at,
                risk_level,
                findings_summary,
                recommendations,
                evidence_provenance,
                detector_provenance,
                model_provenance
            ) VALUES (
                :analysisId,
                :customerId,
                :operatorId,
                :generatedAt,
                :riskLevel,
                :findingsSummary,
                CAST(:recommendations AS jsonb),
                CAST(:evidenceProvenance AS jsonb),
                CAST(:detectorProvenance AS jsonb),
                CAST(:modelProvenance AS jsonb)
            )
            """;

    private static final String SELECT_BASE = """
            SELECT
                analysis_id,
                customer_id,
                operator_id,
                generated_at,
                risk_level,
                findings_summary,
                recommendations::text AS recommendations,
                evidence_provenance::text AS evidence_provenance,
                detector_provenance::text AS detector_provenance,
                model_provenance::text AS model_provenance
            FROM analysis_history
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcAnalysisHistoryAdapter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public AnalysisHistoryEntry persist(AnalysisHistoryCreateCommand command) {
        UUID analysisId = UUID.randomUUID();
        jdbc.sql(INSERT_SQL)
                .param("analysisId", analysisId)
                .param("customerId", command.customerId())
                .param("operatorId", command.operatorId().value())
                .param("generatedAt", OffsetDateTime.ofInstant(command.generatedAt(), ZoneOffset.UTC))
                .param("riskLevel", command.result().riskLevel().name())
                .param("findingsSummary", command.result().findingsSummary())
                .param("recommendations", writeJson(command.result().recommendations()))
                .param("evidenceProvenance", writeJson(command.evidenceProvenance()))
                .param("detectorProvenance", writeJson(command.detectorProvenance()))
                .param("modelProvenance", writeJson(command.modelProvenance()))
                .update();
        return new AnalysisHistoryEntry(
                analysisId,
                command.customerId(),
                command.operatorId(),
                command.generatedAt(),
                command.result(),
                command.evidenceProvenance(),
                command.detectorProvenance(),
                command.modelProvenance());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisHistoryEntry> listByCustomer(UUID customerId) {
        return jdbc.sql(SELECT_BASE + " WHERE customer_id = :customerId ORDER BY generated_at DESC, analysis_id DESC")
                .param("customerId", customerId)
                .query(this::mapHistoryEntry)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisHistoryEntry> findByCustomerAndId(UUID customerId, UUID analysisId) {
        return jdbc.sql(SELECT_BASE + " WHERE customer_id = :customerId AND analysis_id = :analysisId")
                .param("customerId", customerId)
                .param("analysisId", analysisId)
                .query(this::mapHistoryEntry)
                .optional();
    }

    private AnalysisHistoryEntry mapHistoryEntry(ResultSet rs, int rowNum) throws SQLException {
        List<String> recommendations = readJson(
                rs.getString("recommendations"),
                new TypeReference<List<String>>() {});
        List<PolicyEvidence> evidence = readJson(
                rs.getString("evidence_provenance"),
                new TypeReference<List<PolicyEvidence>>() {});
        List<RiskSignalEvidence> detectorEvidence = readJson(
                rs.getString("detector_provenance"),
                new TypeReference<List<RiskSignalEvidence>>() {});
        AnalysisModelProvenance modelProvenance = readJson(
                rs.getString("model_provenance"),
                new TypeReference<AnalysisModelProvenance>() {});
        AnalysisResult result = new AnalysisResult(
                AnalysisResult.RiskLevel.valueOf(rs.getString("risk_level")),
                rs.getString("findings_summary"),
                recommendations);
        return new AnalysisHistoryEntry(
                rs.getObject("analysis_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                new OperatorId(rs.getString("operator_id")),
                rs.getTimestamp("generated_at").toInstant(),
                result,
                evidence,
                detectorEvidence,
                modelProvenance);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize analysis history JSON", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) throws SQLException {
        try {
            return json.readValue(value, type);
        } catch (JacksonException exception) {
            throw new SQLException("Could not deserialize analysis history JSON", exception);
        }
    }
}
