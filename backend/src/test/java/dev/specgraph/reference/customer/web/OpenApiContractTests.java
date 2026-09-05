package dev.specgraph.reference.customer.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.net.URL;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("VFY-CUSTOMER-READ-001")
@Tag("VFY-ANALYSIS-001")
@Tag("VFY-AUTH-001")
@Tag("VFY-FAILURE-PATHS-001")
class OpenApiContractTests {
    @Test
    void r4ContractRetainsTypedCustomerAnalysisSessionSecurityAndGroundingReferences() {
        URL contract = Thread.currentThread().getContextClassLoader().getResource("static/openapi.yaml");
        assertThat(contract).as("packaged R4 OpenAPI contract").isNotNull();

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(contract.toExternalForm(), null, null);
        assertThat(result.getMessages()).as("OpenAPI parser diagnostics").isEmpty();

        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).startsWith("3.0");
        assertThat(api.getInfo().getVersion()).isEqualTo("r4-grounded-safety");
        assertThat(api.getPaths()).containsKeys(
                "/api/session",
                "/api/session/login",
                "/api/session/logout",
                "/api/customers/{customerId}",
                "/api/customers/{customerId}/analyses",
                "/api/customers/{customerId}/analyses/{analysisId}");

        var session = api.getPaths().get("/api/session").getGet();
        assertThat(session).isNotNull();
        assertThat(session.getResponses().get("200").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/SessionResponse");

        var login = api.getPaths().get("/api/session/login").getPost();
        assertThat(login).isNotNull();
        assertThat(login.getResponses()).containsKeys("204", "401", "403");
        assertThat(login.getParameters()).extracting(parameter -> parameter.get$ref())
                .contains("#/components/parameters/CsrfToken");
        assertThat(login.getRequestBody().getContent()).containsKey("application/x-www-form-urlencoded");

        var logout = api.getPaths().get("/api/session/logout").getPost();
        assertThat(logout).isNotNull();
        assertThat(logout.getSecurity()).isNotEmpty();
        assertThat(logout.getResponses()).containsKeys("204", "401", "403");

        assertThat(api.getComponents().getSecuritySchemes()).containsKey("SessionCookie");
        var sessionCookie = api.getComponents().getSecuritySchemes().get("SessionCookie");
        assertThat(sessionCookie.getName()).isEqualTo("JSESSIONID");
        assertThat(String.valueOf(sessionCookie.getIn())).containsIgnoringCase("cookie");

        Schema<?> sessionResponse = api.getComponents().getSchemas().get("SessionResponse");
        assertThat(sessionResponse.getOneOf()).extracting(Schema::get$ref).containsExactlyInAnyOrder(
                "#/components/schemas/AuthenticatedSession",
                "#/components/schemas/UnauthenticatedSession");
        assertThat(sessionResponse.getDiscriminator().getPropertyName()).isEqualTo("state");

        Schema<?> authenticatedSession = api.getComponents().getSchemas().get("AuthenticatedSession");
        assertThat(authenticatedSession.getRequired()).containsAll(Set.of("state", "operatorId", "csrf"));
        Schema<?> unauthenticatedSession = api.getComponents().getSchemas().get("UnauthenticatedSession");
        assertThat(unauthenticatedSession.getRequired()).containsAll(Set.of("state", "csrf"));

        var customerOperation = api.getPaths().get("/api/customers/{customerId}").getGet();
        assertThat(customerOperation).isNotNull();
        assertThat(customerOperation.getSecurity()).isNotEmpty();
        assertThat(customerOperation.getParameters()).extracting(parameter -> parameter.get$ref()).containsExactly(
                "#/components/parameters/CustomerId",
                "#/components/parameters/Page",
                "#/components/parameters/CustomerReviewPageSize",
                "#/components/parameters/CustomerReviewActivityType",
                "#/components/parameters/CustomerReviewStatus",
                "#/components/parameters/CustomerReviewCreatedFrom",
                "#/components/parameters/CustomerReviewCreatedTo");
        assertThat(customerOperation.getResponses()).containsKeys("200", "400", "401", "404", "500");
        assertThat(customerOperation.getResponses().get("200").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/CustomerReviewPage");

        assertIntegerParameter(api, "Page", "page", 0, 0, null);
        assertIntegerParameter(api, "CustomerReviewPageSize", "pageSize", 50, 1, 200);
        assertIntegerParameter(api, "AnalysisHistoryPageSize", "pageSize", 20, 1, 100);
        assertThat(api.getComponents()
                        .getParameters()
                        .get("CustomerReviewActivityType")
                        .getSchema()
                        .getEnum()
                        .stream()
                        .map(String::valueOf)
                        .toList())
                .containsExactly("CARD", "PAYMENT", "CRYPTO");
        assertThat(api.getComponents().getParameters().get("CustomerReviewStatus").getSchema().getType())
                .isEqualTo("string");
        assertThat(api.getComponents().getParameters().get("CustomerReviewCreatedFrom").getSchema().getFormat())
                .isEqualTo("date-time");
        assertThat(api.getComponents().getParameters().get("CustomerReviewCreatedTo").getSchema().getFormat())
                .isEqualTo("date-time");

        Schema<?> customerReviewPage = api.getComponents().getSchemas().get("CustomerReviewPage");
        assertThat(customerReviewPage.getRequired()).containsAll(Set.of(
                "customerId", "activities", "riskEvidence", "page", "pageSize", "totalActivities",
                "totalRiskEvidence", "totalPages", "hasPrevious", "hasNext"));
        assertThat(customerReviewPage.getProperties().get("activities").getItems().get$ref())
                .isEqualTo("#/components/schemas/Activity");
        assertThat(customerReviewPage.getProperties().get("riskEvidence").getItems().get$ref())
                .isEqualTo("#/components/schemas/RiskEvidence");

        var analysisCollection = api.getPaths().get("/api/customers/{customerId}/analyses");
        assertThat(analysisCollection.getPost()).isNotNull();
        assertThat(analysisCollection.getPost().getSecurity()).isNotEmpty();
        assertThat(analysisCollection.getPost().getParameters()).extracting(parameter -> parameter.get$ref())
                .contains("#/components/parameters/CsrfToken");
        assertThat(analysisCollection.getPost().getResponses())
                .containsKeys("201", "401", "403", "404", "422", "502", "503");
        assertThat(analysisCollection.getPost().getResponses().get("201").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Analysis");
        assertThat(analysisCollection.getGet()).isNotNull();
        assertThat(analysisCollection.getGet().getSecurity()).isNotEmpty();
        assertThat(analysisCollection.getGet().getParameters()).extracting(parameter -> parameter.get$ref())
                .containsExactly(
                        "#/components/parameters/CustomerId",
                        "#/components/parameters/Page",
                        "#/components/parameters/AnalysisHistoryPageSize");
        assertThat(analysisCollection.getGet().getResponses()).containsKeys("200", "400", "401");
        var historyHeaders = analysisCollection.getGet().getResponses().get("200").getHeaders();
        assertThat(historyHeaders).containsOnlyKeys(
                "X-Page", "X-Page-Size", "X-Total-Count", "X-Total-Pages", "X-Has-Previous", "X-Has-Next");
        assertThat(historyHeaders.get("X-Page").getSchema().getType()).isEqualTo("integer");
        assertThat(historyHeaders.get("X-Page-Size").getSchema().getMaximum()).isEqualByComparingTo("100");
        assertThat(historyHeaders.get("X-Total-Count").getSchema().getFormat()).isEqualTo("int64");
        assertThat(historyHeaders.get("X-Total-Pages").getSchema().getFormat()).isEqualTo("int64");
        assertThat(historyHeaders.get("X-Has-Previous").getSchema().getType()).isEqualTo("boolean");
        assertThat(historyHeaders.get("X-Has-Next").getSchema().getType()).isEqualTo("boolean");

        Schema<?> analysis = api.getComponents().getSchemas().get("Analysis");
        assertThat(analysis.getRequired()).containsAll(Set.of(
                "analysisId", "customerId", "operatorId", "generatedAt", "riskLevel",
                "findingsSummary", "recommendations", "evidenceProvenance", "detectorProvenance", "modelProvenance"));
        assertThat(analysis.getProperties().get("analysisId").getFormat()).isEqualTo("uuid");
        assertThat(analysis.getProperties().get("generatedAt").getFormat()).isEqualTo("date-time");
        assertThat(analysis.getProperties().get("riskLevel").getEnum().stream().map(String::valueOf).toList())
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(analysis.getProperties().get("detectorProvenance").getItems().get$ref())
                .isEqualTo("#/components/schemas/RiskSignalEvidence");
        assertThat(analysis.getProperties().get("modelProvenance").get$ref())
                .isEqualTo("#/components/schemas/AnalysisModelProvenance");

        Schema<?> detectorEvidence = api.getComponents().getSchemas().get("RiskSignalEvidence");
        assertThat(detectorEvidence.getRequired()).containsAll(Set.of(
                "detectorIdentity", "signalIdentity", "score", "provenance"));

        Schema<?> evidenceReference = api.getComponents().getSchemas().get("AnalysisEvidenceReference");
        assertThat(evidenceReference.getRequired()).containsAll(Set.of("kind", "evidenceIdentity"));
        assertThat(evidenceReference.getProperties().get("kind").getEnum().stream().map(String::valueOf).toList())
                .containsExactly("ACTIVITY", "SOURCE_RISK", "DETECTOR_SIGNAL", "POLICY_RETRIEVAL");

        Schema<?> modelProvenance = api.getComponents().getSchemas().get("AnalysisModelProvenance");
        assertThat(modelProvenance.getRequired()).containsAll(Set.of(
                "backendIdentity", "modelIdentity", "promptIdentity", "evidenceReferences", "metadata"));
        assertThat(modelProvenance.getProperties().get("evidenceReferences").getItems().get$ref())
                .isEqualTo("#/components/schemas/AnalysisEvidenceReference");

        Schema<?> problem = api.getComponents().getSchemas().get("AnalysisProblem");
        assertThat(problem.getProperties().get("reason").getEnum().stream().map(String::valueOf).toList())
                .contains("DETECTOR_FAILURE", "INVALID_RESULT");

        Schema<?> activity = api.getComponents().getSchemas().get("Activity");
        assertThat(activity.getOneOf()).extracting(Schema::get$ref).containsExactlyInAnyOrder(
                "#/components/schemas/CardActivity",
                "#/components/schemas/PaymentActivity",
                "#/components/schemas/CryptoActivity");
        assertThat(activity.getDiscriminator()).isNotNull();
        assertThat(activity.getDiscriminator().getPropertyName()).isEqualTo("type");
        assertThat(activity.getDiscriminator().getMapping()).containsAllEntriesOf(java.util.Map.of(
                "CARD", "#/components/schemas/CardActivity",
                "PAYMENT", "#/components/schemas/PaymentActivity",
                "CRYPTO", "#/components/schemas/CryptoActivity"));

        Schema<?> base = api.getComponents().getSchemas().get("ActivityBase");
        assertThat(base.getRequired()).containsAll(Set.of(
                "transactionId", "amount", "currency", "status", "createdAt"));
        Schema<?> amount = base.getProperties().get("amount");
        assertThat(amount.getType()).isEqualTo("string");
        assertThat(amount.getPattern()).isEqualTo("^-?\\d+(?:\\.\\d+)?$");
        assertThat(String.valueOf(amount.getExample())).isEqualTo("248.50");

        assertActivityVariant(api, "CardActivity", "CARD", "CardDetails");
        assertActivityVariant(api, "PaymentActivity", "PAYMENT", "PaymentDetails");
        assertActivityVariant(api, "CryptoActivity", "CRYPTO", "CryptoDetails");
        assertClosedDetailsSchema(api, "CardDetails");
        assertClosedDetailsSchema(api, "PaymentDetails");
        assertClosedDetailsSchema(api, "CryptoDetails");

        Schema<?> risk = api.getComponents().getSchemas().get("RiskEvidence");
        assertThat(risk.getRequired()).containsAll(Set.of(
                "assessmentId", "transactionId", "ruleId", "ruleName", "triggeredAt", "scoreContribution"));
        assertThat(risk.getProperties().get("assessmentId").getFormat()).isEqualTo("uuid");
    }

    private static void assertActivityVariant(OpenAPI api, String schemaName, String expectedType, String detailsSchema) {
        Schema<?> variant = api.getComponents().getSchemas().get(schemaName);
        assertThat(variant).as(schemaName).isNotNull();
        assertThat(variant.getAllOf()).as(schemaName + " allOf").hasSize(2);
        assertThat(variant.getAllOf()).extracting(Schema::get$ref)
                .contains("#/components/schemas/ActivityBase");

        Schema<?> inline = variant.getAllOf().stream()
                .filter(schema -> schema.get$ref() == null)
                .findFirst()
                .orElseThrow();
        assertThat(inline.getRequired()).contains("type", "details");

        Schema<?> type = inline.getProperties().get("type");
        assertThat(type.getEnum().stream().map(String::valueOf).toList()).containsExactly(expectedType);
        Schema<?> details = inline.getProperties().get("details");
        assertThat(details.get$ref()).isEqualTo("#/components/schemas/" + detailsSchema);
    }

    private static void assertIntegerParameter(
            OpenAPI api,
            String componentName,
            String parameterName,
            int defaultValue,
            int minimum,
            Integer maximum) {
        var parameter = api.getComponents().getParameters().get(componentName);
        assertThat(parameter).as(componentName).isNotNull();
        assertThat(parameter.getName()).isEqualTo(parameterName);
        assertThat(parameter.getIn()).isEqualTo("query");
        assertThat(parameter.getRequired()).isFalse();
        assertThat(parameter.getSchema().getType()).isEqualTo("integer");
        assertThat(parameter.getSchema().getFormat()).isEqualTo("int32");
        assertThat(String.valueOf(parameter.getSchema().getDefault())).isEqualTo(Integer.toString(defaultValue));
        assertThat(parameter.getSchema().getMinimum()).isEqualByComparingTo(Integer.toString(minimum));
        if (maximum == null) {
            assertThat(parameter.getSchema().getMaximum()).isNull();
        } else {
            assertThat(parameter.getSchema().getMaximum()).isEqualByComparingTo(Integer.toString(maximum));
        }
    }

    private static void assertClosedDetailsSchema(OpenAPI api, String schemaName) {
        Schema<?> details = api.getComponents().getSchemas().get(schemaName);
        assertThat(details).as(schemaName).isNotNull();
        assertThat(details.getAdditionalProperties())
                .as(schemaName + " must reject fields from foreign activity families")
                .isEqualTo(Boolean.FALSE);
    }
}
