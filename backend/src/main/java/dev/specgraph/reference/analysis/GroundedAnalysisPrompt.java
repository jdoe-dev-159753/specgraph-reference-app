package dev.specgraph.reference.analysis;

import dev.specgraph.reference.customer.Activity;
import java.util.Locale;

/** Shared bounded-evidence prompt used by every live Stage-3 chat adapter. */
final class GroundedAnalysisPrompt {
    static final String IDENTITY = "grounded-analysis-v2";

    static final String SYSTEM = """
            You are an advisory customer-activity analyst operating exclusively on synthetic demonstration data.
            Always respond in English.
            Use only the evidence explicitly provided in the user message. Never invent transactions, amounts,
            thresholds, reports, policies, sources, motives, customer attributes, or criminal behavior.
            SOURCE ACTIVITIES are synthetic input facts. SOURCE RISK ASSESSMENTS are persisted source evidence.
            DERIVED DETECTOR SIGNALS are advisory model outputs, not facts. RETRIEVED SYNTHETIC POLICY is the only
            policy grounding available.
            Missing evidence is not evidence. Never fill missing information with plausible examples. Terms such as
            fraud, money laundering, stolen credentials, shell accounts, CVV mismatch, thresholds, or criminal intent
            may only appear if they occur verbatim in the supplied evidence.
            Risk calibration for this demonstration: use LOW when no supplied policy review trigger matches; use
            MEDIUM when a supplied policy manual-review trigger matches; use HIGH only when supplied evidence explicitly
            establishes a severe risk classification through corroborating source or detector evidence. A manual-review
            trigger alone is never HIGH.
            Do not recommend freezing, blocking, reporting, escalating, monitoring, or any other operational action
            unless the retrieved synthetic policy explicitly supports that action. When no retrieved policy supports an
            action, the sole recommendation must state that no policy-grounded action is available.
            Return only the requested JSON object. Do not use Markdown, headings, citations, code fences, or text outside
            the JSON object. The findingsSummary must be concise, factual, and written in English. Recommendations must
            be proportionate review actions grounded in the supplied evidence.
            """;

    private GroundedAnalysisPrompt() {}

    /** Serializes only the bounded envelope, preserving evidence-family labels used by grounding. */
    static String render(AnalysisEvidenceEnvelope evidence) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("customerId=").append(evidence.customerId()).append('\n');
        prompt.append("fullHistoryActivityCount=").append(evidence.totalActivityCount()).append('\n');
        prompt.append("fullHistorySourceRiskCount=").append(evidence.totalSourceRiskCount()).append('\n');
        prompt.append("fullHistoryDetectorEvidenceCount=").append(evidence.totalDetectorEvidenceCount()).append('\n');
        prompt.append("fullHistoryPolicyEvidenceCount=").append(evidence.totalPolicyEvidenceCount()).append('\n');
        prompt.append("selectedActivityCount=").append(evidence.activities().size()).append('\n');
        prompt.append("selectedSourceRiskCount=").append(evidence.sourceRiskEvidence().size()).append('\n');
        prompt.append("selectedDetectorEvidenceCount=").append(evidence.detectorEvidence().size()).append('\n');
        prompt.append("selectedPolicyEvidenceCount=").append(evidence.policyEvidence().size()).append('\n');

        prompt.append("\nSOURCE ACTIVITIES\n");
        evidence.activities().forEach(activity -> prompt
                .append("- transactionId=").append(activity.transactionId())
                .append(" type=").append(activity.type())
                .append(" amount=").append(activity.amount().toPlainString())
                .append(' ').append(activity.currency())
                .append(" status=").append(activity.status())
                .append(" createdAt=").append(activity.createdAt())
                .append(" details=").append(safeActivityDetails(activity))
                .append('\n'));

        prompt.append("\nSOURCE RISK ASSESSMENTS\n");
        evidence.sourceRiskEvidence().forEach(risk -> prompt
                .append("- assessmentId=").append(risk.assessmentId())
                .append(" transactionId=").append(risk.transactionId())
                .append(" ruleId=").append(risk.ruleId())
                .append(" ruleName=").append(risk.ruleName())
                .append(" scoreContribution=").append(risk.scoreContribution())
                .append('\n'));

        prompt.append("\nDERIVED DETECTOR SIGNALS\n");
        evidence.detectorEvidence().forEach(signal -> prompt
                .append("- artifactId=").append(signal.artifactIdentity())
                .append(" detector=").append(signal.detectorIdentity())
                .append(" signal=").append(signal.signalIdentity())
                .append(" score=").append(String.format(Locale.ROOT, "%.6f", signal.score()))
                .append(" provenance=").append(signal.provenance())
                .append('\n'));

        prompt.append("\nRETRIEVED SYNTHETIC POLICY\n");
        evidence.policyEvidence().forEach(policy -> prompt
                .append("- artifactId=").append(policy.artifactIdentity())
                .append(" content=").append(policy.content())
                .append(" retrievalMetadata=").append(policy.retrievalMetadata())
                .append('\n'));
        return prompt.toString();
    }

    /** Projects the closed activity variants without adding attributes absent from source data. */
    private static String safeActivityDetails(Activity activity) {
        return switch (activity.details()) {
            case Activity.CardDetails card -> "cardType=" + card.cardType()
                    + ",merchantName=" + card.merchantName()
                    + ",mccCode=" + card.mccCode()
                    + ",cardPresent=" + card.cardPresent()
                    + ",declineReason=" + valueOrDash(card.declineReason());
            case Activity.PaymentDetails payment -> "paymentMethod=" + payment.paymentMethod()
                    + ",receiverBankCountry=" + payment.receiverBankCountry();
            case Activity.CryptoDetails crypto -> "blockchain=" + crypto.blockchain()
                    + ",exchangeName=" + valueOrDash(crypto.exchangeName());
        };
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
