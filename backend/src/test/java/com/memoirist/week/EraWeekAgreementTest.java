package com.memoirist.week;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.era.Era;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rule "an Era holds a week when their dates overlap" (D-22) is written twice: in SQL, to find the weeks of one
 * Era ({@link WeeklyLogRepository#findOverlapping}), and in Java, to find the Eras of one week
 * ({@link WeekEras#holds}). Two copies of a rule drift apart, so this test runs both on every start and end date
 * within ten days either side of a week, with and without an end, and fails if they ever disagree.
 *
 * <p>That is 21 start days and, for each, an open end and 21 end days: 462 questions, all asked of the real database
 * in one rolled-back transaction.
 */
@IntegrationTest
@Transactional
class EraWeekAgreementTest {

    /** The week under test: Monday 2026-10-26 to Sunday 2026-11-01. */
    private static final LocalDate MONDAY = Fixtures.MONDAY;

    @Autowired
    UserRepository users;

    @Autowired
    WeeklyLogRepository weeks;

    User alice;
    WeeklyLog week;

    @BeforeEach
    void aWeek() {
        alice = users.save(Fixtures.user());
        week = weeks.save(Fixtures.week(alice.getId(), MONDAY));
    }

    @Test
    void sqlAndJavaAgreeOnEveryStartAndEndAroundTheWeek() {
        List<String> disagreements = new ArrayList<>();
        int asked = 0;
        for (int startOffset = -10; startOffset <= 10; startOffset++) {
            LocalDate start = MONDAY.plusDays(startOffset);
            List<LocalDate> ends = new ArrayList<>();
            ends.add(null); // an Era that is still going
            for (int endOffset = 0; endOffset <= 20; endOffset++) {
                ends.add(start.plusDays(endOffset));
            }
            for (LocalDate end : ends) {
                boolean inSql = weeks.findOverlapping(alice.getId(), start, end).contains(week);
                boolean inJava = WeekEras.holds(eraWith(start, end), week);
                asked++;
                if (inSql != inJava) {
                    disagreements.add("Era " + start + " to " + end + ": SQL " + inSql + ", Java " + inJava);
                }
            }
        }

        assertThat(asked).isEqualTo(21 * 22);
        assertThat(disagreements).isEmpty();
    }

    @Test
    void theEdgesAreWhatD22Says() {
        // A hand-written check of the same query, so a bug that fooled both copies the same way would still show.
        assertThat(overlapping(MONDAY.minusDays(30), MONDAY.minusDays(1)))
                .as("ended the Sunday before")
                .isFalse();
        assertThat(overlapping(MONDAY.minusDays(30), MONDAY))
                .as("ended on the Monday")
                .isTrue();
        assertThat(overlapping(MONDAY.plusDays(6), MONDAY.plusDays(30)))
                .as("started on the Sunday")
                .isTrue();
        assertThat(overlapping(MONDAY.plusDays(7), MONDAY.plusDays(30)))
                .as("started the next Monday")
                .isFalse();
        assertThat(overlapping(MONDAY.plusDays(3), MONDAY.plusDays(3)))
                .as("one day inside")
                .isTrue();
        assertThat(overlapping(MONDAY.minusDays(100), null))
                .as("old and still going")
                .isTrue();
        assertThat(overlapping(MONDAY.plusDays(7), null)).as("not started yet").isFalse();
    }

    private boolean overlapping(LocalDate start, LocalDate end) {
        return weeks.findOverlapping(alice.getId(), start, end).contains(week);
    }

    private Era eraWith(LocalDate start, LocalDate end) {
        return end == null ? Fixtures.era(alice.getId(), start) : Fixtures.era(alice.getId(), start, end);
    }
}
