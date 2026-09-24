package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildAppointment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The calendar stops double bookings: the same appointment twice, and overlapping times. */
class CalendarServiceConflictTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @TempDir
    Path tempDir;

    private CalendarService service;

    @BeforeEach
    void createServiceWithBarberAt10() {
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(tempDir.resolve("calendar.json")), new CalendarData(),
                new OccurrenceExpander(), clock);
        service.addAppointment(buildAppointment("Barber appointment", MONDAY, LocalTime.of(10, 0))); // A-1, 10-11
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void sameAppointmentAgainPointsToTheExistingOne() {
        String expected = "A-1 is already this appointment: Barber appointment on 2026-09-28 at 10:00. "
                + "If the user gave new details, change A-1 with edit. If nothing is different, "
                + "tell the user they already have it.";
        assertRejected(expected,
                () -> service.addAppointment(buildAppointment("barber appointment", MONDAY, LocalTime.of(10, 0))));
        assertRejected(expected,
                () -> service.addAppointment(buildAppointment("Barber appointment", MONDAY, LocalTime.of(15, 0))));
    }

    @Test
    void overlappingAppointmentIsRejected() {
        assertRejected("This overlaps A-1 Barber appointment on 2026-09-28 10:00-11:00. "
                        + "Overlapping appointments are not allowed. Ask the user for another time.",
                () -> service.addAppointment(buildAppointment("Call with Anna", MONDAY, LocalTime.of(10, 30))));
    }

    @Test
    void backToBackAppointmentsAreFine() {
        assertEquals("A-2", service.addAppointment(buildAppointment("Lunch", MONDAY, LocalTime.of(11, 0))).id);
    }

    @Test
    void overlapWithALaterOccurrenceOfASeriesIsFound() {
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym); // A-2

        assertRejected("This overlaps A-2 Gym on 2026-10-12 18:00-19:00. "
                        + "Overlapping appointments are not allowed. Ask the user for another time.",
                () -> service.addAppointment(
                        buildAppointment("Dentist", LocalDate.of(2026, 10, 12), LocalTime.of(18, 30))));
    }

    @Test
    void editIntoAnOverlapIsRejected() {
        service.addAppointment(buildAppointment("Lunch", MONDAY, LocalTime.of(12, 0))); // A-2
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(10, 30);

        assertRejected("This overlaps A-1 Barber appointment on 2026-09-28 10:00-11:00. "
                        + "Overlapping appointments are not allowed. Ask the user for another time.",
                () -> service.editItem("A-2", null, changes));
    }

    @Test
    void revisingAnAppointmentDoesNotConflictWithItself() {
        ItemChanges changes = new ItemChanges();
        changes.place = "12 Avenue Q";

        assertEquals("12 Avenue Q", ((Appointment) service.editItem("A-1", null, changes)).place);
    }
}
