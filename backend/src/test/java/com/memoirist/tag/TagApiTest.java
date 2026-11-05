package com.memoirist.tag;

import com.memoirist.entry.EntryApiTest;
import com.memoirist.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the tag tests share on top of {@link EntryApiTest} (the movable clock, users that are cleaned up afterwards, and
 * the entry calls): short helpers for the four {@code /api/v1/tags} calls, and for reading the {@code entry_tags} table.
 *
 * <p>All the subclasses use the same configuration as the entry tests, so they share one Spring context and one
 * Postgres container.
 */
public abstract class TagApiTest extends EntryApiTest {

    protected static final String TAGS = "/api/v1/tags";

    // The four calls

    protected RestTestClient.ResponseSpec postTag(User user, String name) {
        return postTag(user, json("name", name));
    }

    protected RestTestClient.ResponseSpec postTag(User user, Map<String, Object> body) {
        return client.post()
                .uri(TAGS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec listTags(User user) {
        return client.get().uri(TAGS).header(HEADER, user.getId().toString()).exchange();
    }

    protected RestTestClient.ResponseSpec putTag(User user, Object tagId, String name) {
        return putTag(user, tagId, json("name", name));
    }

    protected RestTestClient.ResponseSpec putTag(User user, Object tagId, Map<String, Object> body) {
        return client.put()
                .uri(TAGS + "/" + tagId)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected RestTestClient.ResponseSpec deleteTag(User user, Object tagId) {
        return client.delete()
                .uri(TAGS + "/" + tagId)
                .header(HEADER, user.getId().toString())
                .exchange();
    }

    // Making and reading tags

    /** Creates a tag that must be accepted as new ({@code 201}) and returns its id. */
    protected UUID aTag(User user, String name) {
        Map<String, Object> body = bodyMap(postTag(user, name).expectStatus().isCreated());
        return UUID.fromString(body.get("id").toString());
    }

    /** The user's tags as the API lists them: each one a map of {@code id}, {@code name} and {@code entryCount}. */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> tagList(User user) {
        return listTags(user)
                .expectStatus()
                .isOk()
                .expectBody(List.class)
                .returnResult()
                .getResponseBody();
    }

    protected List<String> tagNames(User user) {
        return tagList(user).stream().map(tag -> tag.get("name").toString()).toList();
    }

    /** The {@code entryCount} the list shows for the tag with this name. */
    protected long countOf(User user, String name) {
        return tagList(user).stream()
                .filter(tag -> tag.get("name").equals(name))
                .map(tag -> ((Number) tag.get("entryCount")).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tag named " + name));
    }

    protected static List<String> ids(UUID... tagIds) {
        List<String> ids = new ArrayList<>();
        for (UUID id : tagIds) {
            ids.add(id.toString());
        }
        return ids;
    }

    // Reading the database

    protected long countTags(User user) {
        return jdbc.sql("SELECT count(*) FROM tags WHERE user_id = :id")
                .param("id", user.getId())
                .query(Long.class)
                .single();
    }

    /** The ids of the tags linked to the entry, read with SQL so that nothing is hidden (a sealed entry's too). */
    protected List<UUID> linkedTagIds(Object entryId) {
        return jdbc.sql("SELECT tag_id FROM entry_tags WHERE entry_id = :id")
                .param("id", UUID.fromString(entryId.toString()))
                .query(UUID.class)
                .list();
    }

    protected long countLinks(Object tagId) {
        return jdbc.sql("SELECT count(*) FROM entry_tags WHERE tag_id = :id")
                .param("id", UUID.fromString(tagId.toString()))
                .query(Long.class)
                .single();
    }

    /** The tag row, as a map. */
    protected Map<String, Object> tagRow(Object tagId) {
        return jdbc.sql("SELECT * FROM tags WHERE id = :id")
                .param("id", UUID.fromString(tagId.toString()))
                .query()
                .singleRow();
    }
}
