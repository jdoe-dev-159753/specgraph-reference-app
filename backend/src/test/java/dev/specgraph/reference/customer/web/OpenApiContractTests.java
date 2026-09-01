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
class OpenApiContractTests {
    @Test
    void r2CustomerReadContractIsValidAndPreservesExactDecimalAndTypedActivityFamilies() {
        URL contract = Thread.currentThread().getContextClassLoader().getResource("static/openapi.yaml");
        assertThat(contract).as("packaged R2 OpenAPI contract").isNotNull();

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(contract.toExternalForm(), null, null);
        assertThat(result.getMessages()).as("OpenAPI parser diagnostics").isEmpty();

        OpenAPI api = result.getOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getOpenapi()).startsWith("3.0");
        assertThat(api.getInfo().getVersion()).isEqualTo("r2");
        assertThat(api.getPaths()).containsKey("/api/customers/{customerId}");
        var operation = api.getPaths().get("/api/customers/{customerId}").getGet();
        assertThat(operation).isNotNull();
        assertThat(operation.getResponses()).containsKeys("200", "404");
        var okContent = operation.getResponses().get("200").getContent();
        assertThat(okContent).containsKey("application/json");
        assertThat(okContent.get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/CustomerSnapshot");

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
        assertThat(base.getProperties()).containsKeys(
                "transactionId", "amount", "currency", "status", "createdAt");
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

        Schema<?> cardDetails = api.getComponents().getSchemas().get("CardDetails");
        assertThat(cardDetails.getProperties().get("cardPresent").getType()).isEqualTo("boolean");

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

    private static void assertClosedDetailsSchema(OpenAPI api, String schemaName) {
        Schema<?> details = api.getComponents().getSchemas().get(schemaName);
        assertThat(details).as(schemaName).isNotNull();
        assertThat(details.getAdditionalProperties())
                .as(schemaName + " must reject fields from foreign activity families")
                .isEqualTo(Boolean.FALSE);
    }
}
