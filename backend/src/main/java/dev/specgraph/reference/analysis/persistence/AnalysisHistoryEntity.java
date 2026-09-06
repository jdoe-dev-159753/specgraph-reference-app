package dev.specgraph.reference.analysis.persistence;

import dev.specgraph.reference.analysis.AnalysisModelProvenance;
import dev.specgraph.reference.analysis.AnalysisResult;
import dev.specgraph.reference.analysis.PolicyEvidence;
import dev.specgraph.reference.analysis.RiskSignalEvidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Adapter-private JPA representation of one retained analysis row.
 * JSONB columns preserve the three provenance families without leaking persistence annotations into
 * application records; Flyway, not Hibernate, remains schema authority.
 */
@Entity(name = "PersistedAnalysisHistory")
@Table(name = "analysis_history")
class AnalysisHistoryEntity {
    @Id
    @Column(name = "analysis_id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "operator_id", nullable = false, length = 128)
    private String operatorId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private AnalysisResult.RiskLevel riskLevel;

    @Column(name = "findings_summary", nullable = false, columnDefinition = "text")
    private String findingsSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", nullable = false, columnDefinition = "jsonb")
    private List<String> recommendations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_provenance", nullable = false, columnDefinition = "jsonb")
    private List<PolicyEvidence> evidenceProvenance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detector_provenance", nullable = false, columnDefinition = "jsonb")
    private List<RiskSignalEvidence> detectorProvenance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_provenance", nullable = false, columnDefinition = "jsonb")
    private AnalysisModelProvenance modelProvenance;

    protected AnalysisHistoryEntity() {}

    /** Takes defensive copies of JSONB collections before the entity enters the persistence context. */
    AnalysisHistoryEntity(
            UUID id,
            UUID customerId,
            String operatorId,
            Instant generatedAt,
            AnalysisResult.RiskLevel riskLevel,
            String findingsSummary,
            List<String> recommendations,
            List<PolicyEvidence> evidenceProvenance,
            List<RiskSignalEvidence> detectorProvenance,
            AnalysisModelProvenance modelProvenance) {
        this.id = id;
        this.customerId = customerId;
        this.operatorId = operatorId;
        this.generatedAt = generatedAt;
        this.riskLevel = riskLevel;
        this.findingsSummary = findingsSummary;
        this.recommendations = List.copyOf(recommendations);
        this.evidenceProvenance = List.copyOf(evidenceProvenance);
        this.detectorProvenance = List.copyOf(detectorProvenance);
        this.modelProvenance = modelProvenance;
    }

    UUID id() { return id; }
    UUID customerId() { return customerId; }
    String operatorId() { return operatorId; }
    Instant generatedAt() { return generatedAt; }
    AnalysisResult.RiskLevel riskLevel() { return riskLevel; }
    String findingsSummary() { return findingsSummary; }
    List<String> recommendations() { return List.copyOf(recommendations); }
    List<PolicyEvidence> evidenceProvenance() { return List.copyOf(evidenceProvenance); }
    List<RiskSignalEvidence> detectorProvenance() { return List.copyOf(detectorProvenance); }
    AnalysisModelProvenance modelProvenance() { return modelProvenance; }
}
