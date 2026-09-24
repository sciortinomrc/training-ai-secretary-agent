package io.meterian.aicalendar.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The entry point to the calendar. The agent thread and the alert thread both call it, so every public method is
 * synchronized. Each method hands the work to one focused class, and every change is saved at once.
 */
public class CalendarService {

    private final CalendarRepository repository;
    private final ItemValidator validator;
    private final CalendarQueries queries;
    private final ItemEditor editor;
    private final ItemRemover remover;

    public CalendarService(CalendarStore store, CalendarData data, OccurrenceExpander expander, Clock clock) {
        this.repository = new CalendarRepository(store, data);
        this.validator = new ItemValidator(repository, expander);
        this.queries = new CalendarQueries(repository, expander, clock);
        this.editor = new ItemEditor(repository, validator);
        this.remover = new ItemRemover(repository, validator);
    }

    public synchronized Appointment addAppointment(Appointment appointment) {
        validator.validateAppointment(appointment);
        repository.insertAppointment(appointment);
        repository.saveChanges();
        return appointment;
    }

    public synchronized Alarm addAlarm(Alarm alarm) {
        validator.validateAlarm(alarm);
        repository.insertAlarm(alarm);
        repository.saveChanges();
        return alarm;
    }

    public synchronized Note addNote(Note note) {
        validator.validateNote(note);
        repository.insertNote(note);
        repository.saveChanges();
        return note;
    }

    /** Without occurrenceDate, changes the whole item or series. With it, changes only that occurrence. */
    public synchronized Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes) {
        Object updatedItem = editor.editItem(id, occurrenceDate, changes);
        repository.saveChanges();
        return updatedItem;
    }

    /** Without occurrenceDate, removes the whole item (and an appointment's linked alarms and notes). */
    public synchronized List<String> removeItem(String id, LocalDate occurrenceDate) {
        List<String> removed = remover.removeItem(id, occurrenceDate);
        repository.saveChanges();
        return removed;
    }

    public synchronized Appointment findAppointment(String id) {
        return repository.findAppointment(id);
    }

    public synchronized List<AppointmentOccurrence> listAppointmentOccurrences(LocalDate from, LocalDate to) {
        return queries.listAppointmentOccurrences(from, to);
    }

    public synchronized List<AlarmOccurrence> listAlarmOccurrences(LocalDate from, LocalDate to) {
        return queries.listAlarmOccurrences(from, to);
    }

    public synchronized DayAgenda listDay(LocalDate day) {
        return queries.listDay(day);
    }

    public synchronized List<Object> findItems(String query, String type, LocalDate from, LocalDate to) {
        return queries.findItems(query, type, from, to);
    }
}
