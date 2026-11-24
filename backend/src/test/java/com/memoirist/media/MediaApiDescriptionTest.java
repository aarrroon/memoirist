package com.memoirist.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What {@code /v3/api-docs} says about the media endpoints: what Swagger UI shows and what the frontend's generated
 * types (task 4.2) are made from.
 */
class MediaApiDescriptionTest extends MediaApiTest {

    private String apiDocs() {
        return bodyText(
                client.get().uri("/v3/api-docs").exchange().expectStatus().isOk());
    }

    @Test
    void theThreeMediaOperationsAreUnderTheMediaTagWithTheirErrorResponses() {
        String body = apiDocs();

        for (String operation : List.of(
                "$.paths['/api/v1/entries/{entryId}/media'].post",
                "$.paths['/api/v1/entries/{entryId}/media/order'].put",
                "$.paths['/api/v1/media/{mediaId}'].delete")) {
            assertThat(JsonPath.<List<String>>read(body, operation + ".tags"))
                    .as(operation)
                    .containsExactly("Media");
        }
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/media'].post"))
                .containsExactlyInAnyOrder("201", "400", "404", "409", "413", "415", "503");
        assertThat(responseCodes(body, "$.paths['/api/v1/entries/{entryId}/media/order'].put"))
                .containsExactlyInAnyOrder("200", "400", "404", "409", "415");
        assertThat(responseCodes(body, "$.paths['/api/v1/media/{mediaId}'].delete"))
                .containsExactlyInAnyOrder("204", "400", "404", "409");
    }

    @Test
    void theUploadIsAMultipartFormWithTheFileAndTwoOptionalFields() {
        String body = apiDocs();

        String schema =
                "$.paths['/api/v1/entries/{entryId}/media'].post.requestBody.content['multipart/form-data'].schema";
        assertThat(JsonPath.<String>read(body, schema + ".$ref")).endsWith("/MediaUploadForm");
        // The form record is documentation only; these names must stay the controller's @RequestParam names.
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.components.schemas.MediaUploadForm.properties")
                        .keySet())
                .containsExactlyInAnyOrder("file", "displayOrder", "purpose");
        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.MediaUploadForm.required"))
                .containsExactly("file");
    }

    @Test
    void theUploadsFormFieldsAreNotListedAsQueryParameters() {
        String body = apiDocs();

        List<String> parameters =
                JsonPath.read(body, "$.paths['/api/v1/entries/{entryId}/media'].post.parameters[*].name");
        assertThat(parameters).containsExactly("entryId");
    }

    @Test
    void theMediaShapeHasTheContractFields() {
        Map<String, Object> properties = JsonPath.read(apiDocs(), "$.components.schemas.MediaResponse.properties");

        assertThat(properties.keySet())
                .containsExactlyInAnyOrder(
                        "id",
                        "entryId",
                        "fileUrl",
                        "mediaType",
                        "purpose",
                        "displayOrder",
                        "fileSizeBytes",
                        "uploadedAt");
    }

    @Test
    void theOrderRequestNamesTheListAndItsLimit() {
        String body = apiDocs();

        assertThat(JsonPath.<List<String>>read(body, "$.components.schemas.MediaOrderRequest.required"))
                .containsExactly("mediaIds");
        assertThat(JsonPath.<Integer>read(body, "$.components.schemas.MediaOrderRequest.properties.mediaIds.maxItems"))
                .isEqualTo(20);
    }

    private static Iterable<String> responseCodes(String apiDocs, String operation) {
        Map<String, Object> responses = JsonPath.read(apiDocs, operation + ".responses");
        return responses.keySet();
    }
}
