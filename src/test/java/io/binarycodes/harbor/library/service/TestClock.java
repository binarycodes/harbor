package io.binarycodes.harbor.library.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the test moves on by hand, so that bookmarks saved one after another
 * get distinct, predictable timestamps.
 */
class TestClock extends Clock {

    private static final Instant START = Instant.parse("2026-08-17T09:00:00Z");

    private Instant instant = START;

    @Override
    public ZoneId getZone() {
        return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    void advance(Duration amount) {
        instant = instant.plus(amount);
    }

    /**
     * The clock is a bean, so it is the same instance for every test in a class.
     */
    void reset() {
        instant = START;
    }
}
