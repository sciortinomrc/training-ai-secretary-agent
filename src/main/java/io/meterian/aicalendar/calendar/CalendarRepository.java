package io.meterian.aicalendar.calendar;

import io.meterian.aicalendar.Json;
import java.util.List;
import java.util.function.Function;

/**
 * Holds the calendar data in memory: finds items by ID, inserts new items with a new ID, and saves the data.
 * It does no locking, because CalendarService is its only caller and CalendarService locks.
 */
class CalendarRepository {

    private final CalendarStore store;
    private CalendarData data;

    CalendarRepository(CalendarStore store, CalendarData data) {
        this.store = store;
        this.data = data;
    }

    List<Appointment> getAppointments() {
        return data.appointments;
    }

    List<Alarm> getAlarms() {
        return data.alarms;
    }

    List<Note> getNotes() {
        return data.notes;
    }

    List<Draft> getDrafts() {
        return data.drafts;
    }

    Appointment findAppointment(String id) {
        return findById(data.appointments, id, appointment -> appointment.id);
    }

    Alarm findAlarm(String id) {
        return findById(data.alarms, id, alarm -> alarm.id);
    }

    Note findNote(String id) {
        return findById(data.notes, id, note -> note.id);
    }

    void insertAppointment(Appointment appointment) {
        appointment.id = assignNextId(ItemIds.APPOINTMENT_PREFIX);
        data.appointments.add(appointment);
    }

    void insertAlarm(Alarm alarm) {
        alarm.id = assignNextId(ItemIds.ALARM_PREFIX);
        data.alarms.add(alarm);
    }

    void insertNote(Note note) {
        note.id = assignNextId(ItemIds.NOTE_PREFIX);
        data.notes.add(note);
    }

    Draft findDraft(String id) {
        return data.drafts.stream()
                .filter(draft -> draft.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new CalendarException(
                        "No draft has the id " + id + ". Use list-drafts to get the id."));
    }

    void insertDraft(Draft draft) {
        draft.id = assignNextId(ItemIds.DRAFT_PREFIX);
        data.drafts.add(draft);
    }

    void replaceDraft(Draft draft) {
        data.drafts.set(data.drafts.indexOf(findDraft(draft.id)), draft);
    }

    void removeDraft(String id) {
        data.drafts.remove(findDraft(id));
    }

    void saveChanges() {
        store.save(data);
    }

    /** A deep copy of the current data, to restore when a change fails. */
    CalendarData takeSnapshot() {
        return Json.copyValue(data, CalendarData.class);
    }

    void restoreSnapshot(CalendarData snapshot) {
        data = snapshot;
    }

    private String assignNextId(String prefix) {
        int nextNumber = data.nextIds.getOrDefault(prefix, 1);
        data.nextIds.put(prefix, nextNumber + 1);
        return prefix + "-" + nextNumber;
    }

    private static <T> T findById(List<T> items, String id, Function<T, String> readId) {
        return items.stream()
                .filter(item -> readId.apply(item).equals(id))
                .findFirst()
                .orElseThrow(() -> ItemIds.buildUnknownIdError(id));
    }
}
