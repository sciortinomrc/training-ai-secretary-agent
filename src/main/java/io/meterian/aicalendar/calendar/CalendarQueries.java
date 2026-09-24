package io.meterian.aicalendar.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Answers read-only questions about the calendar: occurrences in a range, everything on one day, and search. */
class CalendarQueries {

    private static final List<String> ITEM_TYPES = List.of("appointment", "alarm", "note");
    private static final int DEFAULT_SEARCH_DAYS = 365;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;
    private final Clock clock;

    CalendarQueries(CalendarRepository repository, OccurrenceExpander expander, Clock clock) {
        this.repository = repository;
        this.expander = expander;
        this.clock = clock;
    }

    List<AppointmentOccurrence> listAppointmentOccurrences(LocalDate from, LocalDate to) {
        List<AppointmentOccurrence> occurrences = new ArrayList<>();
        for (Appointment appointment : repository.getAppointments()) {
            occurrences.addAll(expander.expandAppointment(appointment, from, to));
        }
        occurrences.sort(Comparator.comparing(AppointmentOccurrence::computeStart));
        return occurrences;
    }

    List<AlarmOccurrence> listAlarmOccurrences(LocalDate from, LocalDate to) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (Alarm alarm : repository.getAlarms()) {
            occurrences.addAll(expandAlarm(alarm, new DateRange(from, to)));
        }
        occurrences.sort(Comparator.comparing(occurrence -> occurrence.firesAt));
        return occurrences;
    }

    DayAgenda listDay(LocalDate day) {
        List<AppointmentOccurrence> appointments = listAppointmentOccurrences(day, day);
        Set<String> appointmentIdsOnDay = appointments.stream()
                .map(occurrence -> occurrence.appointment.id)
                .collect(Collectors.toSet());
        List<Note> notes = repository.getNotes().stream()
                .filter(note -> day.equals(note.date) || appointmentIdsOnDay.contains(note.appointmentId))
                .collect(Collectors.toList());
        return new DayAgenda(day, appointments, listAlarmOccurrences(day, day), notes);
    }

    /** Each argument may be null. With no dates, there is no date filter. */
    List<Object> findItems(String query, String type, LocalDate from, LocalDate to) {
        requireKnownType(type);
        Optional<DateRange> searchRange = resolveSearchRange(from, to);
        List<Object> items = new ArrayList<>();
        if (includesType(type, "appointment")) {
            items.addAll(findAppointments(query, searchRange));
        }
        if (includesType(type, "alarm")) {
            items.addAll(findAlarms(query, searchRange));
        }
        if (includesType(type, "note")) {
            items.addAll(findNotes(query, searchRange));
        }
        return items;
    }

    private List<Appointment> findAppointments(String query, Optional<DateRange> searchRange) {
        return repository.getAppointments().stream()
                .filter(appointment -> matchesQuery(query,
                        appointment.title, appointment.place, joinAttendees(appointment)))
                .filter(appointment -> searchRange.map(range -> hasOccurrences(appointment, range)).orElse(true))
                .collect(Collectors.toList());
    }

    private List<Alarm> findAlarms(String query, Optional<DateRange> searchRange) {
        return repository.getAlarms().stream()
                .filter(alarm -> matchesQuery(query, alarm.message))
                .filter(alarm -> searchRange.map(range -> !expandAlarm(alarm, range).isEmpty()).orElse(true))
                .collect(Collectors.toList());
    }

    private List<Note> findNotes(String query, Optional<DateRange> searchRange) {
        return repository.getNotes().stream()
                .filter(note -> matchesQuery(query, note.text))
                .filter(note -> searchRange.map(range -> isNoteInRange(note, range)).orElse(true))
                .collect(Collectors.toList());
    }

    private List<AlarmOccurrence> expandAlarm(Alarm alarm, DateRange range) {
        if (!alarm.isLinked()) {
            return expander.expandFixedAlarm(alarm, range.from, range.to);
        }
        // A linked alarm fires before its appointment, maybe on an earlier day, so look further ahead.
        Appointment appointment = repository.findAppointment(alarm.appointmentId);
        long extraDays = alarm.minutesBefore / MINUTES_PER_DAY + 1;
        List<AppointmentOccurrence> appointmentOccurrences =
                expander.expandAppointment(appointment, range.from, range.to.plusDays(extraDays));
        return expander.expandLinkedAlarm(alarm, appointmentOccurrences).stream()
                .filter(occurrence -> range.contains(occurrence.firesAt.toLocalDate()))
                .collect(Collectors.toList());
    }

    private boolean hasOccurrences(Appointment appointment, DateRange range) {
        return !expander.expandAppointment(appointment, range.from, range.to).isEmpty();
    }

    private boolean isNoteInRange(Note note, DateRange range) {
        if (note.date != null) {
            return range.contains(note.date);
        }
        return hasOccurrences(repository.findAppointment(note.appointmentId), range);
    }

    private Optional<DateRange> resolveSearchRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return Optional.empty();
        }
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_SEARCH_DAYS);
        if (end.isBefore(start)) {
            throw new CalendarException("toDate must not be before fromDate.");
        }
        return Optional.of(new DateRange(start, end));
    }

    private static void requireKnownType(String type) {
        if (type != null && !ITEM_TYPES.contains(type)) {
            throw new CalendarException("type must be appointment, alarm or note.");
        }
    }

    private static boolean includesType(String requestedType, String itemType) {
        return requestedType == null || requestedType.equals(itemType);
    }

    private static boolean matchesQuery(String query, String... texts) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String searchText = query.toLowerCase(Locale.ROOT);
        for (String text : texts) {
            if (text != null && text.toLowerCase(Locale.ROOT).contains(searchText)) {
                return true;
            }
        }
        return false;
    }

    private static String joinAttendees(Appointment appointment) {
        return appointment.attendees.stream()
                .map(attendee -> attendee.name + " " + attendee.email)
                .collect(Collectors.joining(" "));
    }
}
