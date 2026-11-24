package com.memoirist;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Clock} that a test can move: {@link #set} jumps to an instant and {@link #advance} adds a duration. The app
 * asks the clock for "now" on every request, so one test can create an entry at 23:30 on a Sunday in Melbourne,
 * jump to the unlock date and read the same entry again.
 *
 * <p>The service calls {@code clock.withZone(userZone)} to find a user's "today". That returns a view that shares this
 * clock's moment, so moving the clock moves every view of it.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(new AtomicReference<>(start), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    /** Moves the clock to this moment, for this clock and every view made from it. */
    public void set(Instant moment) {
        now.set(moment);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
