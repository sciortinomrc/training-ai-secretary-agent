package io.meterian.aicalendar;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock that tests can move. */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.zone = zone;
        setTime(start);
    }

    public void setTime(LocalDateTime time) {
        instant = time.atZone(zone).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(LocalDateTime.ofInstant(instant, newZone), newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
