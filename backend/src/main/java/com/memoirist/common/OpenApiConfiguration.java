package com.memoirist.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What {@code /v3/api-docs} says about the API as a whole (API_CONTRACT, Global rules). The controllers describe each
 * endpoint; this class fixes the three things springdoc cannot work out from the Java alone, which were found by
 * comparing the document with the contract (task 3.17):
 *
 * <ol>
 *   <li><b>Errors.</b> springdoc copies the success type onto every {@code @ApiResponse}, so a {@code 404} on
 *       {@code POST /tags} claimed to answer with a {@code TagResponse}. Every {@code 4xx} and {@code 5xx} is now an
 *       {@code application/problem+json} {@link #PROBLEM} (RFC 9457, {@code GlobalExceptionHandler}).
 *   <li><b>Success types.</b> A controller that does not name {@code produces} is documented as {@code *}{@code /*};
 *       the API only ever sends JSON.
 *   <li><b>Required fields.</b> A response always has every field (a missing value is {@code null}, never absent), but
 *       Java records carry no such promise, so a generated client (task 4.2) would type every field as optional. Every
 *       property of a response schema is marked required; the ones that can be {@code null} say so with
 *       {@code @Schema(nullable = true)} on the record component.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    /** The schema name of a caption: Jackson's {@code JsonNode}, which springdoc would describe by its Java getters. */
    static final String TIPTAP_DOCUMENT = "TipTapDocument";

    /** The schema name of an error body. */
    static final String PROBLEM = "Problem";

    /** One tag per resource, in the order the contract lists them. */
    private static final List<String> TAGS =
            List.of("Users", "Eras", "Templates", "Tags", "Feed", "Weekly Logs", "Entries", "Media", "Music");

    @Bean
    OpenAPI memoiristOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("memoirist API")
                        .version("v1")
                        .description("The Phase 1 journal API. The contract is docs/API_CONTRACT.md; every error is an"
                                + " RFC 9457 problem (application/problem+json)."))
                .tags(TAGS.stream().map(name -> new Tag().name(name)).toList());
    }

    @Bean
    OpenApiCustomizer problemsAndRequiredFields() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            schemas.put(PROBLEM, problemSchema());
            schemas.remove("JsonNode");
            schemas.put(TIPTAP_DOCUMENT, tipTapSchema());
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperations().forEach(OpenApiConfiguration::describeResponses));
            schemas.forEach(OpenApiConfiguration::requireEveryField);
            schemas.values().forEach(OpenApiConfiguration::wrapNullableReferences);
        };
    }

    private static void describeResponses(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5")) {
                response.setContent(new Content()
                        .addMediaType(
                                "application/problem+json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
            } else if (code.startsWith("2")) {
                json(response);
            }
        });
    }

    /** Renames the {@code *}{@code /*} media type of a success response to {@code application/json}. */
    private static void json(ApiResponse response) {
        Content content = response.getContent();
        if (content != null && content.containsKey("*/*")) {
            Content renamed = new Content();
            content.forEach((type, body) -> renamed.addMediaType("*/*".equals(type) ? "application/json" : type, body));
            response.setContent(renamed);
        }
    }

    /** Responses ({@code TagResponse}, {@code PageResponseEntrySummaryResponse}, {@code MusicResult}, ...). */
    private static void requireEveryField(String name, Schema<?> schema) {
        boolean response = name.endsWith("Response") || name.equals("MusicResult");
        if (response && schema.getProperties() != null) {
            schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
        }
    }

    /**
     * A nullable property that points at another schema (an Era's {@code cover}) comes out of springdoc as
     * {@code {"type": "null", "$ref": ...}}, which says nothing useful. OpenAPI 3.1 spells it {@code oneOf} the
     * schema or {@code null}.
     */
    private static void wrapNullableReferences(Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }
        schema.getProperties().replaceAll((name, property) -> {
            boolean nullable =
                    property.getTypes() != null && property.getTypes().contains("null");
            if (property.get$ref() == null || !nullable) {
                return property;
            }
            return new Schema<>()
                    .oneOf(List.of(
                            new Schema<>().$ref(property.get$ref().replace("/JsonNode", "/" + TIPTAP_DOCUMENT)),
                            new Schema<>().types(Set.of("null"))));
        });
    }

    /** A caption (D-05). What is allowed inside is the validator's business, not the schema's. */
    private static Schema<?> tipTapSchema() {
        return new ObjectSchema().description("A TipTap document (D-05): an object whose type is doc.");
    }

    /** The error body of {@code GlobalExceptionHandler}: the fields API_CONTRACT lists under Errors. */
    private static Schema<?> problemSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put(
                "type",
                new StringSchema()
                        .description(
                                "urn:memoirist:problem: plus the suffix in API_CONTRACT (not-found, entry-sealed, ...)"));
        properties.put("title", new StringSchema().description("Fixed for each type"));
        properties.put("status", new IntegerSchema().format("int32").description("The HTTP status"));
        properties.put(
                "detail", new StringSchema().description("A sentence for the caller; never repeats what they sent"));
        properties.put("instance", new StringSchema().description("The request path"));
        properties.put("timestamp", new DateTimeSchema());
        properties.put(
                "fieldErrors",
                new ObjectSchema()
                        .additionalProperties(new StringSchema())
                        .description("Only on validation-failed: field name to message"));
        Schema<?> problem = new ObjectSchema().description("An RFC 9457 problem. Same fields for every error.");
        problem.setProperties(properties);
        problem.setRequired(List.of("type", "title", "status", "detail", "instance", "timestamp"));
        return problem;
    }
}
