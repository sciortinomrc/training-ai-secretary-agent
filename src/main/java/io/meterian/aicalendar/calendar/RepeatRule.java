package io.meterian.aicalendar.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RepeatRule {
    public Frequency frequency;
    /** Only for WEEKLY. */
    public List<DayOfWeek> daysOfWeek;
    /** Last possible date. Null means no end. */
    public LocalDate until;

    public RepeatRule() {
    }

    public RepeatRule(Frequency frequency, List<DayOfWeek> daysOfWeek, LocalDate until) {
        this.frequency = frequency;
        this.daysOfWeek = daysOfWeek == null ? null : new ArrayList<>(daysOfWeek);
        this.until = until;
    }
}
