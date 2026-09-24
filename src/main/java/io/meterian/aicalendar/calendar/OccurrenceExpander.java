package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.ValuePicker.pickChangedValue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Turns items and their repeat rules into dated occurrences, with the overrides applied. */
public class OccurrenceExpander {

    private static final OccurrenceChange NO_CHANGE = new OccurrenceChange();

    public boolean isSeriesDate(LocalDate start, RepeatRule rule, LocalDate day) {
        if (day.isBefore(start)) {
            return false;
        }
        if (rule == null) {
            return day.equals(start);
        }
        if (rule.until != null && day.isAfter(rule.until)) {
            return false;
        }
        switch (rule.frequency) {
            case DAILY:
                return true;
            case WEEKLY:
                return rule.daysOfWeek != null && rule.daysOfWeek.contains(day.getDayOfWeek());
            case MONTHLY:
                return day.getDayOfMonth() == start.getDayOfMonth();
            case YEARLY:
                return day.getMonth() == start.getMonth() && day.getDayOfMonth() == start.getDayOfMonth();
            default:
                throw new IllegalStateException("Unknown frequency " + rule.frequency);
        }
    }

    public List<AppointmentOccurrence> expandAppointment(Appointment appointment, LocalDate from, LocalDate to) {
        List<AppointmentOccurrence> occurrences = new ArrayList<>();
        for (LocalDate originalDate : listCandidateDates(
                appointment.date, appointment.repeat, appointment.overrides, from, to)) {
            OccurrenceChange change = appointment.overrides.getOrDefault(originalDate, NO_CHANGE);
            LocalDate date = pickChangedValue(change.date, originalDate);
            if (isCancelled(change) || !isWithin(date, from, to)) {
                continue;
            }
            occurrences.add(new AppointmentOccurrence(appointment, originalDate, date,
                    pickChangedValue(change.startTime, appointment.startTime),
                    pickChangedValue(change.endTime, appointment.endTime),
                    pickChangedValue(change.title, appointment.title),
                    pickChangedValue(change.place, appointment.place),
                    pickChangedValue(change.leadTimeMinutes, appointment.leadTimeMinutes)));
        }
        occurrences.sort(Comparator.comparing(AppointmentOccurrence::computeStart));
        return occurrences;
    }

    public List<AlarmOccurrence> expandFixedAlarm(Alarm alarm, LocalDate from, LocalDate to) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (LocalDate originalDate : listCandidateDates(alarm.date, alarm.repeat, alarm.overrides, from, to)) {
            OccurrenceChange change = alarm.overrides.getOrDefault(originalDate, NO_CHANGE);
            LocalDate date = pickChangedValue(change.date, originalDate);
            if (isCancelled(change) || !isWithin(date, from, to)) {
                continue;
            }
            occurrences.add(new AlarmOccurrence(alarm, originalDate,
                    date.atTime(pickChangedValue(change.time, alarm.time)),
                    pickChangedValue(change.message, alarm.message)));
        }
        occurrences.sort(Comparator.comparing(occurrence -> occurrence.firesAt));
        return occurrences;
    }

    public List<AlarmOccurrence> expandLinkedAlarm(Alarm alarm, List<AppointmentOccurrence> appointmentOccurrences) {
        List<AlarmOccurrence> occurrences = new ArrayList<>();
        for (AppointmentOccurrence appointmentOccurrence : appointmentOccurrences) {
            occurrences.add(new AlarmOccurrence(alarm, appointmentOccurrence.originalDate,
                    appointmentOccurrence.computeStart().minusMinutes(alarm.minutesBefore), alarm.message));
        }
        return occurrences;
    }

    /** Series dates in [from, to], plus every overridden series date, because a move can bring it into range. */
    private List<LocalDate> listCandidateDates(LocalDate start, RepeatRule rule,
            Map<LocalDate, OccurrenceChange> overrides, LocalDate from, LocalDate to) {
        TreeSet<LocalDate> candidateDates = new TreeSet<>();
        LocalDate firstDay = start.isAfter(from) ? start : from;
        LocalDate lastDay = findLastPossibleDay(start, rule, to);
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (isSeriesDate(start, rule, day)) {
                candidateDates.add(day);
            }
        }
        for (LocalDate overriddenDate : overrides.keySet()) {
            if (isSeriesDate(start, rule, overriddenDate)) {
                candidateDates.add(overriddenDate);
            }
        }
        return new ArrayList<>(candidateDates);
    }

    private static LocalDate findLastPossibleDay(LocalDate start, RepeatRule rule, LocalDate to) {
        LocalDate seriesEnd = rule == null ? start : rule.until;
        if (seriesEnd == null || seriesEnd.isAfter(to)) {
            return to;
        }
        return seriesEnd;
    }

    private static boolean isCancelled(OccurrenceChange change) {
        return Boolean.TRUE.equals(change.cancelled);
    }

    private static boolean isWithin(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
