package com.memoirist.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What {@code /v3/api-docs} says about the tag endpoints, which is what Swagger UI shows and what the frontend's
 * generated types (task 4.2) are made from, and about the {@code tagIds} that entries now take.
 */
class TagApiDescriptionTest extends TagApiTest {

    private String apiDocs() {
        return bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());
    }

    private static Iterable<String> responseCodes(String apiDocs, String operation) {
        Map<String, Object> responses = JsonPath.read(apiDocs, operation + ".responses");
        return responses.keySet();
    }

    @Test
    void theTagOperationsAreUnderTheTagsTagWithTheirErrorResponses() {
        String body = apiDocs();

        for (String operation : List.of(
                "$.paths['/api/v1/tags'].post",
                "$.paths['/api/v1/tags'].get",
                "$.paths['/api/v1/tags/{tagId}'].put",
                "$.paths['/api/v1/tags/{tagId}'].delete")) {
            assertThat(JsonPath.<List<String>>read(body, operation + ".tags"))
                    .as(operation)
                    .containsExactly("Tags");
        }
        assertThat(responseCodes(body, "$.paths['/api/v1/tags'].post"))
                .containsExactlyInAnyOrder("200", "201", "400", "404", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/tags'].get")).containsExactlyInAnyOrder("200", "400");
        assertThat(responseCodes(body, "$.paths['/api/v1/tags/{tagId}'].put"))
                .containsExactlyInAnyOrder("200", "400", "404", "409", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/tags/{tagId}'].delete"))
                .containsExactlyInAnyOrder("204", "400", "404");
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.paths['/api/v1/tags/{tagId}']"))
                .as("there is no read of one tag")
                .doesNotContainKey("get");
    }

    @Test
    void theTagShapeHasTheContractFieldsAndTheNameLimitsReachTheSchema() {
        String body = apiDocs();

        Map<String, Object> properties = JsonPath.read(body, "$.components.schemas.TagResponse.properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrder("id", "name", "entryCount");
        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.TagRequest.required"))
                .containsExactly("name");
        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.TagRequest.properties.name.maxLength"))
                .isEqualTo(50);
        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.TagRequest.properties.name.minLength"))
                .isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theEntryRequestsTakeAListOfAtMostTwentyTagIdsAndAnUpdateRequiresIt() {
        String body = apiDocs();

        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.EntryCreateRequest.properties.tagIds.maxItems"))
                .isEqualTo(20);
        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.EntryUpdateRequest.properties.tagIds.maxItems"))
                .isEqualTo(20);
        assertThat(JsonPath.<String>read(
                        body, "$.components.schemas.EntryUpdateRequest.properties.tagIds.items.format"))
                .isEqualTo("uuid");
        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.EntryUpdateRequest.required"))
                .contains("tagIds");
        Map<String, Object> createSchema = JsonPath.read(body, "$.components.schemas.EntryCreateRequest");
        assertThat((List<Object>) createSchema.getOrDefault("required", List.of()))
                .as("optional on a create")
                .doesNotContain("tagIds");
    }
}
