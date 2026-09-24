package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Removes a whole item with its linked items, or cancels one occurrence of a repeating item. */
class ItemRemover {

    private final CalendarRepository repository;
    private final ItemValidator validator;

    ItemRemover(CalendarRepository repository, ItemValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** Returns one short line for each removed thing, for example "A-1" or "A-1 on 2026-10-05". */
    List<String> removeItem(String id, LocalDate occurrenceDate) {
        return occurrenceDate == null ? removeWholeItem(id) : cancelOccurrence(id, occurrenceDate);
    }

    private List<String> cancelOccurrence(String id, LocalDate occurrenceDate) {
        Map<LocalDate, OccurrenceChange> overrides = findSeriesOverrides(id, occurrenceDate);
        overrides.computeIfAbsent(occurrenceDate, day -> new OccurrenceChange()).cancelled = true;
        return List.of(id + " on " + occurrenceDate);
    }

    private Map<LocalDate, OccurrenceChange> findSeriesOverrides(String id, LocalDate occurrenceDate) {
        if (ItemIds.isAppointmentId(id)) {
            Appointment appointment = repository.findAppointment(id);
            validator.requireSeriesDate(appointment.id, appointment.date, appointment.repeat, occurrenceDate);
            return appointment.overrides;
        }
        if (ItemIds.isAlarmId(id)) {
            Alarm alarm = repository.findAlarm(id);
            validator.requireFixedAlarm(alarm);
            validator.requireSeriesDate(alarm.id, alarm.date, alarm.repeat, occurrenceDate);
            return alarm.overrides;
        }
        if (ItemIds.isNoteId(id)) {
            throw ItemValidator.buildNoteOccurrenceError();
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private List<String> removeWholeItem(String id) {
        if (ItemIds.isAppointmentId(id)) {
            return removeAppointmentWithLinkedItems(id);
        }
        if (ItemIds.isAlarmId(id)) {
            repository.getAlarms().remove(repository.findAlarm(id));
            return List.of(id);
        }
        if (ItemIds.isNoteId(id)) {
            repository.getNotes().remove(repository.findNote(id));
            return List.of(id);
        }
        throw ItemIds.buildUnknownIdError(id);
    }

    private List<String> removeAppointmentWithLinkedItems(String appointmentId) {
        repository.getAppointments().remove(repository.findAppointment(appointmentId));
        List<String> removedIds = new ArrayList<>();
        removedIds.add(appointmentId);
        removedIds.addAll(removeMatchingItems(repository.getAlarms(),
                alarm -> appointmentId.equals(alarm.appointmentId), alarm -> alarm.id));
        removedIds.addAll(removeMatchingItems(repository.getNotes(),
                note -> appointmentId.equals(note.appointmentId), note -> note.id));
        return removedIds;
    }

    private static <T> List<String> removeMatchingItems(List<T> items, Predicate<T> isMatch,
            Function<T, String> readId) {
        List<String> removedIds = items.stream().filter(isMatch).map(readId).collect(Collectors.toList());
        items.removeIf(isMatch);
        return removedIds;
    }
}
