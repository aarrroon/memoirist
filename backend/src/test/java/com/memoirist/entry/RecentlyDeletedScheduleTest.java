package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring really schedules the daily sweep (task 3.11): {@code @EnableScheduling} is on, the property in the annotation
 * is read, and exactly one job is registered. The {@code test} profile turns the schedule off, so this class turns it
 * back on with a time that does not come round during a test run (midday on 1 January, UTC), which gives it a context
 * of its own.
 */
@IntegrationTest
@TestPropertySource(properties = "memoirist.recently-deleted.sweep-cron=0 0 12 1 1 *")
class RecentlyDeletedScheduleTest {

    @Autowired
    ScheduledAnnotationBeanPostProcessor scheduler;

    @Test
    void theSweepIsRegisteredOnceWithTheConfiguredCron() {
        List<String> expressions = scheduler.getScheduledTasks().stream()
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();

        assertThat(expressions).containsExactly("0 0 12 1 1 *");
    }
}
