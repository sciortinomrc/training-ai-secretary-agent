package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.List;

/** Everything on one day, sorted by time. */
public final class DayAgenda {
    public final LocalDate day;
    public final List<AppointmentOccurrence> appointments;
    public final List<AlarmOccurrence> alarms;
    public final List<Note> notes;

    public DayAgenda(LocalDate day, List<AppointmentOccurrence> appointments, List<AlarmOccurrence> alarms,
            List<Note> notes) {
        this.day = day;
        this.appointments = appointments;
        this.alarms = alarms;
        this.notes = notes;
    }
}
