package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Appointment {
    public String id;
    public String title;
    /** For a series, the date of the first occurrence. */
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String place;
    public Integer leadTimeMinutes;
    public List<Attendee> attendees = new ArrayList<>();
    public RepeatRule repeat;
    /** Key: the original occurrence date. */
    public Map<LocalDate, OccurrenceChange> overrides = new TreeMap<>();
}
