package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import io.meterian.aicalendar.Json;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Changes a whole item, or one occurrence of a repeating item. A whole-item edit works on a copy, so the stored
 * item stays unchanged when the result breaks a rule.
 */
class ItemEditor {

    private static final Set<String> APPOINTMENT_FIELDS = Set.of(
            "title", "date", "startTime", "endTime", "place", "leadTimeMinutes", "attendees", "repeat");
    private static final Set<String> APPOINTMENT_OCCURRENCE_FIELDS = Set.of(
            "title", "date", "startTime", "endTime", "place", "leadTimeMinutes");
    private static final Set<String> FIXED_ALARM_FIELDS = Set.of("message", "date", "time", "repeat");
    private static final Set<String> FIXED_ALARM_OCCURRENCE_FIELDS = Set.of("message", "date", "time");
    private static final Set<String> LINKED_ALARM_FIELDS = Set.of("message", "minutesBefore");
    private static final Set<String> NOTE_FIELDS = Set.of("text", "date");

    private final CalendarRepository repository;
    private final ItemValidator validator;
    private final ConflictChecker conflictChecker;

    ItemEditor(CalendarRepository repository, ItemValidator validator, ConflictChecker conflictChecker) {
        this.repository = repository;
        this.validator = validator;
        this.conflictChecker = conflictChecker;
    }

    /** Without occurrenceDate, changes the whole item or series. With it, changes only that occurrence. */
    Object editItem(String id, LocalDate occurrenceDate, ItemChanges changes) {
        List<String> changedFields = changes.listChangedFields();
        if (changedFields.isEmpty()) {
            throw new CalendarException("Give at least one field to change.");
        }
        return occurrenceDate == null
                ? editWholeItem(id, changes, changedFields)
                : editOneOccurrence(id, occurrenceDate, changes, changedFields);
    }

    private Object editWholeItem(String id, ItemChanges changes, List<String> changedFields) {
        if (ItemIds.isAppointmentId(id)) {
            return editAppointment(id, changes, changedFields);
        }
        if (ItemIds.isAlarmId(id)) {
            return editAlarm(id, changes, changedFields);
        }
        if (ItemIds.isNoteId(id)) {
            return editNote(id, changes, changedFields);
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private Object editOneOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        if (ItemIds.isAppointmentId(id)) {
            return editAppointmentOccurrence(id, occurrenceDate, changes, changedFields);
        }
        if (ItemIds.isAlarmId(id)) {
            return editAlarmOccurrence(id, occurrenceDate, changes, changedFields);
        }
        if (ItemIds.isNoteId(id)) {
            throw ItemValidator.buildNoteOccurrenceError();
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private Appointment editAppointment(String id, ItemChanges changes, List<String> changedFields) {
        requireAllowedFields(changedFields, APPOINTMENT_FIELDS, "an appointment");
        Appointment current = repository.findAppointment(id);
        Appointment updated = Json.copyValue(current, Appointment.class);
        updated.title = pickChangedValue(changes.title, updated.title);
        updated.date = pickChangedValue(changes.date, updated.date);
        updated.startTime = pickChangedValue(changes.startTime, updated.startTime);
        updated.endTime = pickChangedValue(changes.endTime, updated.endTime);
        updated.place = pickChangedValue(changes.place, updated.place);
        updated.leadTimeMinutes = pickChangedValue(changes.leadTimeMinutes, updated.leadTimeMinutes);
        updated.attendees = pickChangedValue(changes.attendees, updated.attendees);
        updated.repeat = pickChangedValue(changes.repeat, updated.repeat);
        validator.validateAppointment(updated);
        conflictChecker.requireNoConflicts(updated);
        replaceItem(repository.getAppointments(), current, updated);
        return updated;
    }

    private Appointment editAppointmentOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        requireAllowedFields(changedFields, APPOINTMENT_OCCURRENCE_FIELDS, "one occurrence of an appointment");
        Appointment appointment = repository.findAppointment(id);
        validator.requireSeriesDate(appointment.id, appointment.date, appointment.repeat, occurrenceDate);
        OccurrenceChange change = copyExistingChange(appointment.overrides, occurrenceDate);
        change.title = pickChangedValue(changes.title, change.title);
        change.date = pickChangedValue(changes.date, change.date);
        change.startTime = pickChangedValue(changes.startTime, change.startTime);
        change.endTime = pickChangedValue(changes.endTime, change.endTime);
        change.place = pickChangedValue(changes.place, change.place);
        change.leadTimeMinutes = pickChangedValue(changes.leadTimeMinutes, change.leadTimeMinutes);
        validator.validateAppointmentOccurrence(appointment, change);
        appointment.overrides.put(occurrenceDate, change);
        return appointment;
    }

    private Alarm editAlarm(String id, ItemChanges changes, List<String> changedFields) {
        Alarm current = repository.findAlarm(id);
        if (current.isLinked()) {
            requireAllowedFields(changedFields, LINKED_ALARM_FIELDS, "a linked alarm");
        } else {
            requireAllowedFields(changedFields, FIXED_ALARM_FIELDS, "a fixed alarm");
        }
        Alarm updated = Json.copyValue(current, Alarm.class);
        updated.message = pickChangedValue(changes.message, updated.message);
        updated.date = pickChangedValue(changes.date, updated.date);
        updated.time = pickChangedValue(changes.time, updated.time);
        updated.repeat = pickChangedValue(changes.repeat, updated.repeat);
        updated.minutesBefore = pickChangedValue(changes.minutesBefore, updated.minutesBefore);
        validator.validateAlarm(updated);
        replaceItem(repository.getAlarms(), current, updated);
        return updated;
    }

    private Alarm editAlarmOccurrence(String id, LocalDate occurrenceDate, ItemChanges changes,
            List<String> changedFields) {
        Alarm alarm = repository.findAlarm(id);
        validator.requireFixedAlarm(alarm);
        requireAllowedFields(changedFields, FIXED_ALARM_OCCURRENCE_FIELDS, "one occurrence of an alarm");
        validator.requireSeriesDate(alarm.id, alarm.date, alarm.repeat, occurrenceDate);
        OccurrenceChange change = copyExistingChange(alarm.overrides, occurrenceDate);
        change.message = pickChangedValue(changes.message, change.message);
        change.date = pickChangedValue(changes.date, change.date);
        change.time = pickChangedValue(changes.time, change.time);
        alarm.overrides.put(occurrenceDate, change);
        return alarm;
    }

    private Note editNote(String id, ItemChanges changes, List<String> changedFields) {
        requireAllowedFields(changedFields, NOTE_FIELDS, "a note");
        Note current = repository.findNote(id);
        if (changes.date != null && current.appointmentId != null) {
            throw new CalendarException("This note belongs to an appointment. Only text can change.");
        }
        Note updated = Json.copyValue(current, Note.class);
        updated.text = pickChangedValue(changes.text, updated.text);
        updated.date = pickChangedValue(changes.date, updated.date);
        validator.validateNote(updated);
        replaceItem(repository.getNotes(), current, updated);
        return updated;
    }

    private static OccurrenceChange copyExistingChange(Map<LocalDate, OccurrenceChange> overrides, LocalDate day) {
        OccurrenceChange existingChange = overrides.get(day);
        return existingChange == null
                ? new OccurrenceChange()
                : Json.copyValue(existingChange, OccurrenceChange.class);
    }

    private static void requireAllowedFields(List<String> changedFields, Set<String> allowedFields,
            String itemKind) {
        List<String> rejectedFields = changedFields.stream()
                .filter(field -> !allowedFields.contains(field))
                .collect(Collectors.toList());
        if (!rejectedFields.isEmpty()) {
            throw new CalendarException(
                    String.join(", ", rejectedFields) + " cannot be changed on " + itemKind + ".");
        }
    }

    private static <T> void replaceItem(List<T> items, T current, T updated) {
        items.set(items.indexOf(current), updated);
    }
}
