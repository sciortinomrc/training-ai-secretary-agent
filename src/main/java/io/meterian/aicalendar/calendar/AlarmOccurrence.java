package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One moment when an alarm fires. */
public final class AlarmOccurrence {
    public final Alarm alarm;
    public final LocalDate originalDate;
    public final LocalDateTime firesAt;
    public final String message;

    public AlarmOccurrence(Alarm alarm, LocalDate originalDate, LocalDateTime firesAt, String message) {
        this.alarm = alarm;
        this.originalDate = originalDate;
        this.firesAt = firesAt;
        this.message = message;
    }
}
