package com.memoirist.entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The daily trigger of the Recently deleted sweep (D-30). It does nothing but call {@link RecentlyDeletedSweeper#sweep()},
 * so the work can be started another way when the app can't be counted on to be awake at this hour (Q5, Sprint 5).
 *
 * <p>The schedule is {@value #DEFAULT_CRON} in UTC: 16:00 UTC is 02:00 or 03:00 in Melbourne, when nobody is writing.
 * {@code memoirist.recently-deleted.sweep-cron} replaces it, and the value {@code -} turns the schedule off (Spring's
 * "disabled" cron), which is what the {@code test} profile sets: a test that runs at 16:00 UTC must not have a sweep
 * start by itself in the middle of it.
 */
@Component
class RecentlyDeletedSweepJob {

    static final String DEFAULT_CRON = "0 0 16 * * *";

    private static final Logger log = LoggerFactory.getLogger(RecentlyDeletedSweepJob.class);

    private final RecentlyDeletedSweeper sweeper;

    RecentlyDeletedSweepJob(RecentlyDeletedSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Scheduled(cron = "${memoirist.recently-deleted.sweep-cron:" + DEFAULT_CRON + "}", zone = "UTC")
    void run() {
        int erased = sweeper.sweep();
        log.info("Recently deleted: the daily sweep erased {} entries", erased);
    }
}
