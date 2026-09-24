package io.meterian.aicalendar.calendar;

import java.time.LocalDate;

/** An inclusive range of days. */
final class DateRange {
    final LocalDate from;
    final LocalDate to;

    DateRange(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }
}
