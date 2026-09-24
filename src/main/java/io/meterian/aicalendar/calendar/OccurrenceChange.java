package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

/** A change to one occurrence of a series. A null field keeps the series value. */
public class OccurrenceChange {
    public Boolean cancelled;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public String title;
    public Integer leadTimeMinutes;
    public LocalTime time;
    public String message;
}
