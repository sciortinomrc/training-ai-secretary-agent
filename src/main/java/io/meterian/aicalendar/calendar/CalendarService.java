package io.meterian.aicalendar.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The entry point to the calendar. The agent thread and the alert thread both call it, so every public method is
 * synchronized. Each method hands the work to one focused class. Every change is saved at once, and a change that
 * fails, or cannot be saved, leaves the calendar as it was.
 */
public class CalendarService {

    private final CalendarRepository repository;
    private final ItemValidator validator;
    private final CalendarQueries queries;
    private final ItemEditor editor;
    private final ItemRemover remover;
    private final ConflictChecker conflictChecker;

    public CalendarService(CalendarStore store, CalendarData data, OccurrenceExpander expander, Clock clock) {
        this.repository = new CalendarRepository(store, data);
        this.validator = new ItemValidator(repository, expander);
        this.queries = new CalendarQueries(repository, expander, clock);
        this.conflictChecker = new ConflictChecker(repository, expander);
        this.editor = new ItemEditor(repository, validator, conflictChecker);
        this.remover = new ItemRemover(repository, validator);
    }

    public synchronized Appointment addAppointment(Appointment appointment) {
        return addAppointment(appointment, false);
    }

    /** With allowOverlap, the user has agreed to book it although it overlaps another appointment. */
    public synchronized Appointment addAppointment(Appointment appointment, boolean allowOverlap) {
        return applyChange(() -> {
            validator.validateAppointment(appointment);
            conflictChecker.requireNoConflicts(appointment, allowOverlap);
            repository.insertAppointment(appointment);
            return appointment;
        });
    }

    public synchronized Alarm addAlarm(Alarm alarm) {
        return applyChange(() -> {
            validator.validateAlarm(alarm);
            repository.insertAlarm(alarm);
            return alarm;
        });
    }

    public synchronized Note addNote(Note note) {
        return applyChange(() -> {
            validator.validateNote(note);
            repository.insertNote(note);
            return note;
        });
    }

    /** Without occurrenceDate, changes the whole item or series. With it, changes only that occurrence. */
    public synchronized Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes) {
        return editItem(id, occurrenceDate, changes, false);
    }

    /** With allowOverlap, the user has agreed to a change that overlaps another appointment. */
    public synchronized Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes,
            boolean allowOverlap) {
        return applyChange(() -> editor.editItem(id, occurrenceDate, changes, allowOverlap));
    }

    /** Without occurrenceDate, removes the whole item (and an appointment's linked alarms and notes). */
    public synchronized List<String> removeItem(String id, LocalDate occurrenceDate) {
        return applyChange(() -> remover.removeItem(id, occurrenceDate));
    }

    /** Without an id, saves a new draft. With the id of an existing draft, replaces that draft. */
    public synchronized Draft saveDraft(Draft draft) {
        return applyChange(() -> {
            validator.validateDraft(draft);
            if (draft.id == null) {
                repository.insertDraft(draft);
            } else {
                repository.replaceDraft(draft);
            }
            return draft;
        });
    }

    public synchronized void removeDraft(String id) {
        applyChange(() -> {
            repository.removeDraft(id);
            return id;
        });
    }

    public synchronized Draft findDraft(String id) {
        return repository.findDraft(id);
    }

    public synchronized List<Draft> listDrafts() {
        return new ArrayList<>(repository.getDrafts());
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

    /** Runs one change and saves it. If the change or the save fails, the calendar goes back to how it was. */
    private <T> T applyChange(Supplier<T> change) {
        CalendarData snapshot = repository.takeSnapshot();
        try {
            T result = change.get();
            repository.saveChanges();
            return result;
        } catch (RuntimeException e) {
            repository.restoreSnapshot(snapshot);
            throw e;
        }
    }
}
