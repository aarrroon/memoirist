package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What {@code /v3/api-docs} says about the entry endpoints built so far, which is what Swagger UI shows and what the
 * frontend's generated types (task 4.2) are made from. The list grows as task 3.12 adds its endpoint.
 */
class EntryApiDescriptionTest extends EntryApiTest {

    @Test
    void theEntryOperationsAreUnderTheEntriesTagWithTheirErrorResponses() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());

        for (String operation : List.of(
                "$.paths['/api/v1/entries'].post",
                "$.paths['/api/v1/entries'].get",
                "$.paths['/api/v1/entries/{entryId}'].get",
                "$.paths['/api/v1/entries/{entryId}'].put",
                "$.paths['/api/v1/entries/{entryId}'].delete",
                "$.paths['/api/v1/entries/{entryId}/seal'].post",
                "$.paths['/api/v1/entries/opened'].get",
                "$.paths['/api/v1/entries/{entryId}/mark-seen'].post",
                "$.paths['/api/v1/entries/deleted'].get",
                "$.paths['/api/v1/entries/{entryId}/restore'].post",
                "$.paths['/api/v1/entries/{entryId}/erase'].post")) {
            assertThat(JsonPath.<List<String>>read(body, operation + ".tags"))
                    .as(operation)
                    .containsExactly("Entries");
        }
        assertThat(responseCodes(body, "$.paths['/api/v1/entries'].get"))
                .containsExactlyInAnyOrder("200", "400", "404");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries'].post"))
                .containsExactlyInAnyOrder("200", "201", "400", "404", "409", "413", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}'].get"))
                .containsExactlyInAnyOrder("200", "400", "404");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}'].put"))
                .containsExactlyInAnyOrder("200", "400", "404", "409", "413", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}'].delete"))
                .containsExactlyInAnyOrder("204", "400", "404");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/seal'].post"))
                .containsExactlyInAnyOrder("200", "400", "404", "409", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/opened'].get"))
                .containsExactlyInAnyOrder("200", "400");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/mark-seen'].post"))
                .containsExactlyInAnyOrder("204", "400", "404", "409");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/deleted'].get"))
                .containsExactlyInAnyOrder("200", "400");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/restore'].post"))
                .containsExactlyInAnyOrder("200", "400", "404");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/erase'].post"))
                .containsExactlyInAnyOrder("204", "400", "404");
    }

    @Test
    void theRecentlyDeletedListTakesPageAndSizeWithTheContractLimits() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());
        String parameters = "$.paths['/api/v1/entries/deleted'].get.parameters";

        assertThat(JsonPath.<List<String>>read(body, parameters + "[*].name"))
                .containsExactlyInAnyOrder("page", "size");
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.maximum"))
                .containsExactly(50);
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.minimum"))
                .containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.default"))
                .containsExactly(10);
    }

    @Test
    void theEntryShapeHasTheContractFieldsAndCallsTheFlagIsLocked() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());

        Map<String, Object> properties = JsonPath.read(body, "$.components.schemas.EntryResponse.properties");

        assertThat(properties.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "weeklyLogId",
                        "entryDate",
                        "templateId",
                        "title",
                        "contentJson",
                        "contentText",
                        "tags",
                        "soundtrack",
                        "replyToEntryId",
                        "replyIds",
                        "createdAt",
                        "updatedAt",
                        "unlockDate",
                        "isLocked",
                        "dateUnknown",
                        "version",
                        "media");
    }

    @Test
    void theListRowHasTheContractFieldsAndCallsTheFlagIsLocked() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());

        Map<String, Object> properties = JsonPath.read(body, "$.components.schemas.EntrySummaryResponse.properties");

        assertThat(properties.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "weeklyLogId",
                        "entryDate",
                        "title",
                        "previewLine",
                        "photoCount",
                        "voiceNoteCount",
                        "firstMediaKind",
                        "tags",
                        "createdAt",
                        "unlockDate",
                        "isLocked",
                        "dateUnknown",
                        "deletedAt");
        assertThat(JsonPath.<List<String>>read(
                        body, "$.components.schemas.EntrySummaryResponse.properties.firstMediaKind.enum"))
                .containsExactly("PHOTO", "PAGE_SCAN", "VOICE_NOTE");
    }

    @Test
    void theListTakesPageSizeAndTagIdWithTheContractLimits() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());
        String parameters = "$.paths['/api/v1/entries'].get.parameters";

        assertThat(JsonPath.<List<String>>read(body, parameters + "[*].name"))
                .containsExactlyInAnyOrder("page", "size", "tagId");
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.maximum"))
                .containsExactly(100);
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.minimum"))
                .containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(body, parameters + "[?(@.name=='size')].schema.default"))
                .containsExactly(50);
        assertThat(JsonPath.<List<Boolean>>read(body, parameters + "[?(@.name=='tagId')].required"))
                .containsExactly(false);
    }

    @Test
    void theRequestLimitsReachTheSchemaForTheGeneratedTypes() {
        String body = bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());

        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.EntryCreateRequest.properties.title.maxLength"))
                .isEqualTo(150);
        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.EntryUpdateRequest.properties.title.maxLength"))
                .isEqualTo(150);
        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.EntryUpdateRequest.required"))
                .containsExactlyInAnyOrder("entryDate", "tagIds", "version");
        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.EntrySealRequest.required"))
                .containsExactly("unlockDate");
    }

    private static Iterable<String> responseCodes(String apiDocs, String operation) {
        Map<String, Object> responses = JsonPath.read(apiDocs, operation + ".responses");
        return responses.keySet();
    }
}
