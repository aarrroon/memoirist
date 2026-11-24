package com.memoirist.entry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * In the {@code test} profile the job exists but nothing is scheduled ({@code sweep-cron} is {@code -}), so no sweep
 * ever starts by itself in the middle of a test. It shares the context of the Recently deleted API tests.
 */
class RecentlyDeletedScheduleOffInTestsTest extends RecentlyDeletedApiTest {

    @Autowired
    ScheduledAnnotationBeanPostProcessor scheduler;

    @Autowired
    ApplicationContext context;

    @Test
    void theJobIsABeanButNoTaskIsScheduled() {
        assertThat(context.getBeansOfType(RecentlyDeletedSweepJob.class)).hasSize(1);
        assertThat(scheduler.getScheduledTasks()).isEmpty();
    }
}
