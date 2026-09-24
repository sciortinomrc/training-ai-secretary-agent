package io.meterian.aicalendar.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Stops double bookings: the same appointment booked twice, and appointments whose times overlap. */
class ConflictChecker {

    /** Series are checked this far ahead of the new appointment's first date. */
    private static final int CHECK_DAYS = 365;

    private final CalendarRepository repository;
    private final OccurrenceExpander expander;

    ConflictChecker(CalendarRepository repository, OccurrenceExpander expander) {
        this.repository = repository;
        this.expander = expander;
    }

    /** A duplicate is always rejected. An overlap is rejected unless the user allowed it. */
    void requireNoConflicts(Appointment candidate, boolean allowOverlap) {
        requireNotDuplicate(candidate);
        if (!allowOverlap) {
            requireNoOverlap(candidate);
        }
    }

    private void requireNotDuplicate(Appointment candidate) {
        for (Appointment existing : listOtherAppointments(candidate)) {
            if (isSameAppointment(existing, candidate)) {
                throw new CalendarException(existing.id + " already has this appointment: " + existing.title
                        + " on " + existing.date + " at " + existing.startTime
                        + ". Use edit to change it instead of booking it again.");
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
                && existing.date.equals(candidate.date)
                && existing.startTime.equals(candidate.startTime);
    }

    /** Back-to-back appointments (one ends when the other starts) do not overlap. */
    private static boolean isOverlapping(AppointmentOccurrence first, AppointmentOccurrence second) {
        return first.computeStart().isBefore(second.computeEnd()) && second.computeStart().isBefore(first.computeEnd());
    }

    private static CalendarException buildOverlapError(AppointmentOccurrence existing) {
        return new CalendarException("This overlaps " + existing.appointment.id + " " + existing.title + " on "
                + existing.date + " " + existing.startTime + "-" + existing.computeEnd().toLocalTime()
                + ". Ask the user if both should stay. If yes, call again with allowOverlap true.");
    }
}
