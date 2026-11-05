package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/**
 * The daily trigger (task 3.11): it only calls the sweep, and its schedule is the one the docs promise. Whether Spring
 * really registers it is {@link RecentlyDeletedScheduleTest}.
 */
class RecentlyDeletedSweepJobTest {

    @Test
    void runningTheJobCallsTheSweepOnceAndNothingElse() {
        RecentlyDeletedSweeper sweeper = mock(RecentlyDeletedSweeper.class);
        when(sweeper.sweep()).thenReturn(3);

        new RecentlyDeletedSweepJob(sweeper).run();

        verify(sweeper, times(1)).sweep();
        verifyNoMoreInteractions(sweeper);
    }

    @Test
    void theJobIsScheduledByAPropertyWithADailyDefaultInUtc() throws NoSuchMethodException {
        Scheduled scheduled =
                RecentlyDeletedSweepJob.class.getDeclaredMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron())
                .as("the property replaces the default")
                .isEqualTo("${memoirist.recently-deleted.sweep-cron:" + RecentlyDeletedSweepJob.DEFAULT_CRON + "}");
        assertThat(scheduled.zone()).as("not the server's own zone").isEqualTo("UTC");
    }

    @Test
    void theDefaultRunsOnceADayAt1600UtcWhichIsTheSmallHoursInMelbourne() {
        CronExpression cron = CronExpression.parse(RecentlyDeletedSweepJob.DEFAULT_CRON);
        ZonedDateTime morning = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneOffset.UTC);

        ZonedDateTime first = cron.next(morning);
        ZonedDateTime second = cron.next(first);

        assertThat(first).isEqualTo(ZonedDateTime.of(2026, 10, 31, 16, 0, 0, 0, ZoneOffset.UTC));
        assertThat(second).as("the next day, and not twice in one").isEqualTo(first.plusDays(1));
        assertThat(first.withZoneSameInstant(java.time.ZoneId.of("Australia/Melbourne"))
                        .getHour())
                .as("03:00 in summer time, 02:00 in winter")
                .isEqualTo(3);
    }
}
