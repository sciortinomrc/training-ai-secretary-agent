package io.meterian.aicalendar.calendar;

import java.time.LocalDate;

/** Attached to a day (date) or to an appointment (appointmentId), never both. */
public class Note {
    public String id;
    public String text;
    public LocalDate date;
    public String appointmentId;
}
