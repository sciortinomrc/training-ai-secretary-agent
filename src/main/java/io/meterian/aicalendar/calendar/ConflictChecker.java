package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stops double bookings. The same appointment again (same title on the same date) points to the existing one, so
 * it is revised or recognised, never booked twice. Appointments whose times overlap are not allowed.
 */
class ConflictChecker {

    /** Series are checked this far ahead of the new appointment's first date. */
    private static final int CHECK_DAYS = 365;

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;

    ConflictChecker(CalendarRepository repository, OccurrenceExpander expander) {
        this.repository = repository;
        this.expander = expander;
    }

    void requireNoConflicts(Appointment candidate) {
        requireNotDuplicate(candidate);
        requireNoOverlap(candidate);
    }

    private void requireNotDuplicate(Appointment candidate) {
        for (Appointment existing : listOtherAppointments(candidate)) {
            if (isSameAppointment(existing, candidate)) {
                throw new CalendarException(existing.id + " is already this appointment: " + existing.title
                        + " on " + existing.date + " at " + existing.startTime + ". If the user gave new details, "
                        + "change " + existing.id + " with edit. If nothing is different, tell the user they "
                        + "already have it.");
            }
        }
    }

    private void requireNoOverlap(Appointment candidate) {
        LocalDate lastDay = candidate.date.plusDays(CHECK_DAYS);
        Map<LocalDate, List<AppointmentOccurrence>> candidateOccurrencesByDate =
                expander.expandAppointment(candidate, candidate.date, lastDay).stream()
                        .collect(Collectors.groupingBy(occurrence -> occurrence.date));
        for (Appointment existing : listOtherAppointments(candidate)) {
            for (AppointmentOccurrence existingOccurrence :
                    expander.expandAppointment(existing, candidate.date, lastDay)) {
                for (AppointmentOccurrence candidateOccurrence :
                        candidateOccurrencesByDate.getOrDefault(existingOccurrence.date, List.of())) {
                    if (isOverlapping(candidateOccurrence, existingOccurrence)) {
                        throw buildOverlapError(existingOccurrence);
                    }
                }
            }
        }
    }

    private List<Appointment> listOtherAppointments(Appointment candidate) {
        return repository.getAppointments().stream()
                .filter(existing -> !existing.id.equals(candidate.id))
                .collect(Collectors.toList());
    }

    private static boolean isSameAppointment(Appointment existing, Appointment candidate) {
        return existing.title.trim().equalsIgnoreCase(candidate.title.trim())
                && existing.date.equals(candidate.date);
    }

    /** Back-to-back appointments (one ends when the other starts) do not overlap. */
    private static boolean isOverlapping(AppointmentOccurrence first, AppointmentOccurrence second) {
        return first.computeStart().isBefore(second.computeEnd()) && second.computeStart().isBefore(first.computeEnd());
    }

    private static CalendarException buildOverlapError(AppointmentOccurrence existing) {
        return new CalendarException("This overlaps " + existing.appointment.id + " " + existing.title + " on "
                + existing.date + " " + existing.startTime + "-" + existing.computeEnd().toLocalTime()
                + ". Overlapping appointments are not allowed. Ask the user for another time.");
    }
}
