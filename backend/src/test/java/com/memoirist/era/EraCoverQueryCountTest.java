package com.memoirist.era;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.memoirist.SqlLog;
import com.memoirist.entry.RecentlyDeletedApiTest;
import com.memoirist.user.User;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What the covers cost (task 3.15). {@code GET /eras} reads every cover of the list with one query, so the number of
 * SQL statements is the same for 1 Era as for 25 (counted by Hibernate's {@link Statistics}, as in
 * {@code FeedQueryCountTest}); and creating an Era with a cover really does ask for the share lock that
 * {@code EraCoverLockTest} proves (read from the SQL itself).
 */
class EraCoverQueryCountTest extends RecentlyDeletedApiTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private long statementsDuring(Runnable request) {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        request.run();
        return statistics.getPrepareStatementCount();
    }

    private void aJournalWithEras(User user, int eras, int sealedEvery) {
        for (int i = 0; i < eras; i++) {
            String entryId = entryOn(user, W4, "Entry " + i);
            String photo =
                    aPhoto(entryId, "users/test/count/" + entryId + ".jpg", 0).toString();
            createEraWithCover(user, "Era " + i, photo);
            if (sealedEvery > 0 && i % sealedEvery == 0) {
                sealUntil(user, entryId, SEALED_UNTIL);
            }
        }
    }

    private RestTestClient.ResponseSpec createEraWithCover(User user, String title, String photo) {
        RestTestClient.ResponseSpec response = client.post()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("title", title, "startDate", "2026-10-01", "coverMediaId", photo))
                .exchange();
        response.expectStatus().isCreated();
        return response;
    }

    private void listEras(User user) {
        client.get()
                .uri(ERAS)
                .header(HEADER, user.getId().toString())
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void theListCostsTheSameNumberOfStatementsWhateverTheNumberOfEras() {
        User small = aUser();
        aJournalWithEras(small, 1, 0);
        User big = aUser();
        aJournalWithEras(big, 25, 4);

        long smallCount = statementsDuring(() -> listEras(small));
        long bigCount = statementsDuring(() -> listEras(big));

        assertThat(bigCount)
                .as("1 Era: %d statements, 25 Eras: %d", smallCount, bigCount)
                .isEqualTo(smallCount);
    }

    @Test
    void theListShowsTheCoversOfTheOpenEntriesAndNoneOfTheSealedOnes() {
        User alice = aUser();
        aJournalWithEras(alice, 8, 4); // Eras 0 and 4 have a sealed cover

        RestTestClient.ResponseSpec response =
                client.get().uri(ERAS).header(HEADER, alice.getId().toString()).exchange();
        response.expectStatus().isOk();
        String body = bodyText(response);

        List<String> hidden = JsonPath.read(body, "$[?(@.cover == null)].title");
        assertThat(hidden).containsExactlyInAnyOrder("Era 0", "Era 4");
        assertThat(JsonPath.<List<String>>read(body, "$[?(@.cover != null)].title"))
                .hasSize(6);
    }

    @Test
    void creatingAnEraWithACoverTakesTheShareLockOnThePhoto() {
        User alice = aUser();
        String entryId = entryOn(alice, W4, "Beach");
        String photo = aPhoto(entryId, "users/test/count/lock.jpg", 0).toString();

        try (SqlLog sql = SqlLog.capture()) {
            createEraWithCover(alice, "Locked", photo);

            assertThat(sql.statements())
                    .anySatisfy(statement -> assertThat(statement)
                            .startsWith("select")
                            .contains("from media")
                            .contains("for share"));
        }
    }
}
