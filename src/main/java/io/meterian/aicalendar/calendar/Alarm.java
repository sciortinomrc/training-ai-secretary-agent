package io.meterian.aicalendar.calendar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;

/** Either fixed (date + time, optional repeat) or linked (appointmentId + minutesBefore). */
public class Alarm {
    public String id;
    public String message;
    public LocalDate date;
    public LocalTime time;
    public RepeatRule repeat;
    public Map<LocalDate, OccurrenceChange> overrides = new TreeMap<>();
    public String appointmentId;
    public Integer minutesBefore;

    @JsonIgnore
    public boolean isLinked() {
        return appointmentId != null;
    }
}
