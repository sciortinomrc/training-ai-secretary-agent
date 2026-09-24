package io.meterian.aicalendar.calendar;

import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildAppointment;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildFixedAlarm;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildLinkedAlarm;
import static io.meterian.aicalendar.calendar.CalendarServiceTest.buildNote;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class CalendarServiceEditTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 5);

    @TempDir
    Path tempDir;

    private Path file;
    private CalendarService service;

    @BeforeEach
    void createServiceWithWeeklyGym() {
        file = tempDir.resolve("calendar.json");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 24, 10, 0).atZone(ROME).toInstant(), ROME);
        service = new CalendarService(new CalendarStore(file), new CalendarData(), new OccurrenceExpander(), clock);
        Appointment gym = buildAppointment("Gym", MONDAY, LocalTime.of(18, 0));
        gym.repeat = new RepeatRule(Frequency.WEEKLY, List.of(DayOfWeek.MONDAY), null);
        service.addAppointment(gym); // A-1
    }

    private void assertRejected(String expectedMessage, Runnable action) {
        CalendarException error = assertThrows(CalendarException.class, action::run);
        assertEquals(expectedMessage, error.getMessage());
    }

    @Test
    void editWholeSeriesChangesFieldsKeepsIdAndSaves() {
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(19, 0);
        changes.place = "FitLife";

        Appointment updated = (Appointment) service.editItem("A-1", null, changes);

        assertEquals("A-1", updated.id);
        assertEquals(LocalTime.of(19, 0), updated.startTime);
        assertEquals("FitLife", updated.place);
        assertEquals(LocalTime.of(19, 0), service.listDay(NEXT_MONDAY).appointments.get(0).startTime);
        assertEquals("FitLife", new CalendarStore(file).load().appointments.get(0).place);
    }

    @Test
    void editRejectsFieldsThatDoNotApply() {
        ItemChanges changes = new ItemChanges();
        changes.message = "Hello";

        assertRejected("message cannot be changed on an appointment.", () -> service.editItem("A-1", null, changes));
    }

    @Test
    void editWithNoChangesIsRejected() {
        assertRejected("Give at least one field to change.", () -> service.editItem("A-1", null, new ItemChanges()));
    }

    @Test
    void failedEditLeavesItemUnchanged() {
        ItemChanges changes = new ItemChanges();
        changes.endTime = LocalTime.of(17, 0);

        assertRejected("endTime must be after startTime.", () -> service.editItem("A-1", null, changes));
        assertEquals(null, service.findAppointment("A-1").endTime);
    }

    @Test
    void editOneOccurrenceChangesOnlyThatDate() {
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(19, 0);

        service.editItem("A-1", NEXT_MONDAY, changes);

        assertEquals(LocalTime.of(19, 0), service.listDay(NEXT_MONDAY).appointments.get(0).startTime);
        assertEquals(LocalTime.of(18, 0), service.listDay(NEXT_MONDAY.plusWeeks(1)).appointments.get(0).startTime);
    }

    @Test
    void editOccurrenceNeedsARepeatingItemAndASeriesDate() {
        service.addAppointment(buildAppointment("Dentist", MONDAY, LocalTime.of(9, 0))); // A-2
        ItemChanges changes = new ItemChanges();
        changes.startTime = LocalTime.of(10, 0);

        assertRejected("A-2 does not repeat. Call this tool without occurrenceDate.",
                () -> service.editItem("A-2", MONDAY, changes));
        assertRejected("A-1 has no occurrence on 2026-10-06.",
                () -> service.editItem("A-1", LocalDate.of(2026, 10, 6), changes));
    }

    @Test
    void editNoteAndLinkedAlarm() {
        service.addAlarm(buildLinkedAlarm("Pack the bag", "A-1", 60)); // L-1
        service.addNote(buildNote("Bring a towel", null, "A-1"));       // N-1

        ItemChanges alarmChanges = new ItemChanges();
        alarmChanges.minutesBefore = 90;
        assertEquals(90, ((Alarm) service.editItem("L-1", null, alarmChanges)).minutesBefore);

        ItemChanges noteChanges = new ItemChanges();
        noteChanges.text = "Bring two towels";
        assertEquals("Bring two towels", ((Note) service.editItem("N-1", null, noteChanges)).text);

        ItemChanges moveNote = new ItemChanges();
        moveNote.date = MONDAY;
        assertRejected("This note belongs to an appointment. Only text can change.",
                () -> service.editItem("N-1", null, moveNote));
    }

    @Test
    void removeOneOccurrenceCancelsOnlyThatDate() {
        assertEquals(List.of("A-1 on 2026-10-05"), service.removeItem("A-1", NEXT_MONDAY));

        assertTrue(service.listDay(NEXT_MONDAY).appointments.isEmpty());
        assertEquals(1, service.listDay(NEXT_MONDAY.plusWeeks(1)).appointments.size());
    }

    @Test
    void removeAppointmentAlsoRemovesLinkedAlarmsAndNotes() {
        service.addAlarm(buildLinkedAlarm("Pack the bag", "A-1", 60));        // L-1
        service.addAlarm(buildFixedAlarm("Wake up", MONDAY, LocalTime.of(7, 0))); // L-2
        service.addNote(buildNote("Bring a towel", null, "A-1"));              // N-1

        assertEquals(List.of("A-1", "L-1", "N-1"), service.removeItem("A-1", null));

        CalendarData saved = new CalendarStore(file).load();
        assertTrue(saved.appointments.isEmpty());
        assertEquals("L-2", saved.alarms.get(0).id);
        assertTrue(saved.notes.isEmpty());
    }

    @Test
    void removeUnknownIdIsRejected() {
        assertRejected("No item has the id A-7. Use find-items to get the id.", () -> service.removeItem("A-7", null));
    }
}
