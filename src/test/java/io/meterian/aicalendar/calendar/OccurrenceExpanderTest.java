package io.meterian.aicalendar.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OccurrenceExpanderTest {

    private final OccurrenceExpander expander = new OccurrenceExpander();

    private static Appointment buildAppointment(LocalDate date, LocalTime start, RepeatRule repeat) {
        Appointment appointment = new Appointment();
        appointment.id = "A-1";
        appointment.title = "Gym";
        appointment.date = date;
        appointment.startTime = start;
        appointment.leadTimeMinutes = 30;
        appointment.repeat = repeat;
        return appointment;
    }

    private List<LocalDate> listDates(Appointment appointment, LocalDate from, LocalDate to) {
        return expander.expandAppointment(appointment, from, to).stream()
                .map(occurrence -> occurrence.date)
                .collect(Collectors.toList());
    }

    @Test
    void oneTimeAppointmentOccursOnlyOnItsDate() {
        Appointment dentist = buildAppointment(LocalDate.of(2026, 9, 30), LocalTime.of(15, 0), null);

        assertEquals(List.of(LocalDate.of(2026, 9, 30)),
                listDates(dentist, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)));
        assertTrue(listDates(dentist, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).isEmpty());
    }

    @Test
    void dailyRepeatStopsAtUntil() {
        Appointment daily = buildAppointment(LocalDate.of(2026, 9, 24), LocalTime.of(7, 0),
                new RepeatRule(Frequency.DAILY, null, LocalDate.of(2026, 9, 26)));

        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)),
                listDates(daily, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void weeklyRepeatUsesSelectedDays() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 24), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), null));

        assertEquals(List.of(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28),
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                listDates(weekly, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 5)));
    }

    @Test
    void monthlyRepeatSkipsMonthsWithoutTheDay() {
        Appointment monthly = buildAppointment(LocalDate.of(2026, 1, 31), LocalTime.of(9, 0),
                new RepeatRule(Frequency.MONTHLY, null, null));

        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
                listDates(monthly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31)));
    }

    @Test
    void yearlyRepeatOn29FebruaryOccursOnlyInLeapYears() {
        Appointment birthday = buildAppointment(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0),
                new RepeatRule(Frequency.YEARLY, null, null));

        assertEquals(List.of(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29)),
                listDates(birthday, LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31)));
    }

    @Test
    void cancelledOccurrenceIsSkipped() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 28), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null));
        OccurrenceChange cancelled = new OccurrenceChange();
        cancelled.cancelled = true;
        weekly.overrides.put(LocalDate.of(2026, 10, 5), cancelled);

        assertEquals(List.of(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)),
                listDates(weekly, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)));
    }

    @Test
    void movedOccurrenceShowsOnlyOnItsNewDate() {
        Appointment weekly = buildAppointment(LocalDate.of(2026, 9, 28), LocalTime.of(18, 0),
                new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null));
        OccurrenceChange moved = new OccurrenceChange();
        moved.date = LocalDate.of(2026, 10, 6);
        moved.startTime = LocalTime.of(19, 0);
        weekly.overrides.put(LocalDate.of(2026, 10, 5), moved);

        assertTrue(listDates(weekly, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)).isEmpty());

        List<AppointmentOccurrence> tuesday =
                expander.expandAppointment(weekly, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6));
        assertEquals(1, tuesday.size());
        assertEquals(LocalDate.of(2026, 10, 5), tuesday.get(0).originalDate);
        assertEquals(LocalTime.of(19, 0), tuesday.get(0).startTime);
        assertEquals("Gym", tuesday.get(0).title);
    }

    @Test
    void fixedAlarmUsesOverrideTimeAndMessage() {
        Alarm alarm = new Alarm();
        alarm.id = "L-1";
        alarm.message = "Wake up";
        alarm.date = LocalDate.of(2026, 9, 24);
        alarm.time = LocalTime.of(7, 0);
        alarm.repeat = new RepeatRule(Frequency.DAILY, null, null);
        OccurrenceChange later = new OccurrenceChange();
        later.time = LocalTime.of(9, 0);
        later.message = "Sleep in";
        alarm.overrides.put(LocalDate.of(2026, 9, 26), later);

        List<AlarmOccurrence> occurrences =
                expander.expandFixedAlarm(alarm, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26));

        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), occurrences.get(0).firesAt);
        assertEquals("Wake up", occurrences.get(0).message);
        assertEquals(LocalDateTime.of(2026, 9, 26, 9, 0), occurrences.get(1).firesAt);
        assertEquals("Sleep in", occurrences.get(1).message);
    }

    @Test
    void linkedAlarmFiresBeforeEachOccurrenceEvenOnThePreviousDay() {
        Appointment late = buildAppointment(LocalDate.of(2026, 9, 25), LocalTime.of(0, 15), null);
        Alarm alarm = new Alarm();
        alarm.id = "L-2";
        alarm.message = "Leave now";
        alarm.appointmentId = "A-1";
        alarm.minutesBefore = 30;

        List<AlarmOccurrence> occurrences = expander.expandLinkedAlarm(alarm,
                expander.expandAppointment(late, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25)));

        assertEquals(LocalDateTime.of(2026, 9, 24, 23, 45), occurrences.get(0).firesAt);
        assertEquals(LocalDate.of(2026, 9, 25), occurrences.get(0).originalDate);
    }
}
