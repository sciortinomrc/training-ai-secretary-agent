package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Pattern;

/** Checks the calendar rules for new and changed items. Every error message says what is wrong and what to do. */
class ItemValidator {

    static final int MAX_LEAD_MINUTES = 7 * 24 * 60;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;

    ItemValidator(CalendarRepository repository, OccurrenceExpander expander) {
        this.repository = repository;
        this.expander = expander;
    }

    void validateAppointment(Appointment appointment) {
        requireText(appointment.title, "title");
        requireValue(appointment.date, "date");
        requireValue(appointment.startTime, "startTime");
        requireValue(appointment.leadTimeMinutes, "leadTimeMinutes");
        requireMinutesInRange(appointment.leadTimeMinutes, "leadTimeMinutes");
        requireEndAfterStart(appointment.startTime, appointment.endTime);
        validateRepeat(appointment.repeat, appointment.date);
        appointment.attendees.forEach(ItemValidator::validateAttendee);
    }

    /** The message is optional: an alarm without one shows the word "Alarm". */
    void validateAlarm(Alarm alarm) {
        boolean hasFixedFields = alarm.date != null || alarm.time != null || alarm.repeat != null;
        boolean hasLinkedFields = alarm.appointmentId != null || alarm.minutesBefore != null;
        if (hasFixedFields == hasLinkedFields) {
            throw new CalendarException("Give either date and time, or appointmentId and minutesBefore.");
        }
        if (hasFixedFields) {
            validateFixedAlarm(alarm);
        } else {
            validateLinkedAlarm(alarm);
        }
    }

    void validateNote(Note note) {
        requireText(note.text, "text");
        if ((note.date == null) == (note.appointmentId == null)) {
            throw new CalendarException("Give either date or appointmentId.");
        }
        if (note.appointmentId != null) {
            repository.findAppointment(note.appointmentId);
        }
    }

    void validateDraft(Draft draft) {
        boolean isInvitation = draft.appointmentId != null;
        boolean isPlainEmail = !draft.recipients.isEmpty();
        if (isInvitation == isPlainEmail) {
            throw new CalendarException("Give either appointmentId or recipients.");
        }
        if (isInvitation) {
            repository.findAppointment(draft.appointmentId);
        }
        draft.recipients.forEach(ItemValidator::validateAttendee);
        requireText(draft.subject, "subject");
        requireText(draft.body, "body");
    }

    /** Checks one occurrence of an appointment as it will be after the change. */
    void validateAppointmentOccurrence(Appointment appointment, OccurrenceChange change) {
        requireEndAfterStart(pickChangedValue(change.startTime, appointment.startTime),
                pickChangedValue(change.endTime, appointment.endTime));
        if (change.leadTimeMinutes != null) {
            requireMinutesInRange(change.leadTimeMinutes, "leadTimeMinutes");
        }
    }

    void requireSeriesDate(String id, LocalDate start, RepeatRule rule, LocalDate day) {
        if (rule == null) {
            throw new CalendarException(id + " does not repeat. Call this tool without occurrenceDate.");
        }
        if (!expander.isSeriesDate(start, rule, day)) {
            throw new CalendarException(id + " has no occurrence on " + day + ".");
        }
    }

    void requireFixedAlarm(Alarm alarm) {
        if (alarm.isLinked()) {
            throw new CalendarException(
                    "A linked alarm follows its appointment. Change the appointment occurrence instead.");
        }
    }

    static CalendarException buildNoteOccurrenceError() {
        return new CalendarException("Notes do not repeat. Call this tool without occurrenceDate.");
    }

    private void validateFixedAlarm(Alarm alarm) {
        requireValue(alarm.date, "date");
        requireValue(alarm.time, "time");
        validateRepeat(alarm.repeat, alarm.date);
    }

    private void validateLinkedAlarm(Alarm alarm) {
        requireText(alarm.appointmentId, "appointmentId");
        repository.findAppointment(alarm.appointmentId);
        requireValue(alarm.minutesBefore, "minutesBefore");
        requireMinutesInRange(alarm.minutesBefore, "minutesBefore");
    }

    private static void validateRepeat(RepeatRule rule, LocalDate start) {
        if (rule == null) {
            return;
        }
        requireValue(rule.frequency, "repeat.frequency");
        boolean hasDays = rule.daysOfWeek != null && !rule.daysOfWeek.isEmpty();
        if (rule.frequency == Frequency.WEEKLY && !hasDays) {
            throw new CalendarException("repeat.daysOfWeek is required for WEEKLY.");
        }
        if (rule.frequency != Frequency.WEEKLY && hasDays) {
            throw new CalendarException("repeat.daysOfWeek is only for WEEKLY.");
        }
        if (rule.until != null && rule.until.isBefore(start)) {
            throw new CalendarException("repeat.until must not be before the first date.");
        }
    }

    private static void validateAttendee(Attendee attendee) {
        requireText(attendee.name, "attendee name");
        requireText(attendee.email, "attendee email");
        if (!EMAIL_PATTERN.matcher(attendee.email).matches()) {
            throw new CalendarException("'" + attendee.email + "' is not a valid email address.");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw buildMissingFieldError(field);
        }
    }

    private static void requireValue(Object value, String field) {
        if (value == null) {
            throw buildMissingFieldError(field);
        }
    }

    private static void requireMinutesInRange(int minutes, String field) {
        if (minutes < 0 || minutes > MAX_LEAD_MINUTES) {
            throw new CalendarException(field + " must be between 0 and " + MAX_LEAD_MINUTES + " (7 days).");
        }
    }

    private static void requireEndAfterStart(LocalTime start, LocalTime end) {
        if (end != null && !end.isAfter(start)) {
            throw new CalendarException("endTime must be after startTime.");
        }
    }

    private static CalendarException buildMissingFieldError(String field) {
        return new CalendarException(field + " is missing. Ask the user for it.");
    }
}
