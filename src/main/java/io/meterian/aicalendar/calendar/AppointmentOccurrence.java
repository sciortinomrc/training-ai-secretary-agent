package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One dated occurrence of an appointment, with its override already applied. */
public final class AppointmentOccurrence {
    public static final int DEFAULT_DURATION_MINUTES = 60;

    public final Appointment appointment;
    /** The series date before any move. Overrides use this date as key. */
    public final LocalDate originalDate;
    public final LocalDate date;
    public final LocalTime startTime;
    public final LocalTime endTime;
    public final String title;
    public final String place;
    public final int leadTimeMinutes;

    public AppointmentOccurrence(Appointment appointment, LocalDate originalDate, LocalDate date,
            LocalTime startTime, LocalTime endTime, String title, String place, int leadTimeMinutes) {
        this.appointment = appointment;
        this.originalDate = originalDate;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.place = place;
        this.leadTimeMinutes = leadTimeMinutes;
    }

    public LocalDateTime computeStart() {
        return date.atTime(startTime);
    }

    /** Without an end time, an appointment counts as DEFAULT_DURATION_MINUTES long. */
    public LocalDateTime computeEnd() {
        return endTime == null ? computeStart().plusMinutes(DEFAULT_DURATION_MINUTES) : date.atTime(endTime);
    }

    public LocalDateTime computeAlertTime() {
        return computeStart().minusMinutes(leadTimeMinutes);
    }
}
